package com.amrts.fridahelper.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amrts.fridahelper.core.batch.BatchFilter
import com.amrts.fridahelper.core.batch.SmaliFileReader
import com.amrts.fridahelper.core.batch.SmaliMethodEntry
import com.amrts.fridahelper.core.generator.CompositionOptions
import com.amrts.fridahelper.core.generator.JavaHookGenerator
import com.amrts.fridahelper.core.generator.NativeHookGenerator
import com.amrts.fridahelper.core.generator.ScriptComposer
import com.amrts.fridahelper.core.generator.ScriptWrapper
import com.amrts.fridahelper.core.model.HookRequest
import com.amrts.fridahelper.core.model.NativeSymbol
import com.amrts.fridahelper.core.parser.SmaliSignatureParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

/**
 * Shared ViewModel for both Java and Native hook generation and multi-hook composition.
 *
 * Uses coroutines via viewModelScope for background work.
 * Exposes StateFlow for Compose integration (collectAsStateWithLifecycle).
 */
class HookViewModel : ViewModel() {

    private val parser = SmaliSignatureParser()
    private val javaGenerator = JavaHookGenerator()
    private val nativeGenerator = NativeHookGenerator()
    private val composer = ScriptComposer()

    private val _javaScriptOutput = MutableStateFlow<String?>(null)
    val javaScriptOutput: StateFlow<String?> = _javaScriptOutput.asStateFlow()

    private val _javaErrorMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val javaErrorMessage: SharedFlow<String> = _javaErrorMessage.asSharedFlow()

    private val _nativeScriptOutput = MutableStateFlow<String?>(null)
    val nativeScriptOutput: StateFlow<String?> = _nativeScriptOutput.asStateFlow()

    private val _nativeErrorMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val nativeErrorMessage: SharedFlow<String> = _nativeErrorMessage.asSharedFlow()

    private val _composedScriptOutput = MutableStateFlow<String?>(null)
    val composedScriptOutput: StateFlow<String?> = _composedScriptOutput.asStateFlow()

    private val _composedErrorMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val composedErrorMessage: SharedFlow<String> = _composedErrorMessage.asSharedFlow()

    private val _hookQueue = MutableStateFlow<List<HookRequest>>(emptyList())
    val hookQueue: StateFlow<List<HookRequest>> = _hookQueue.asStateFlow()

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)

    private val _scrollToOutput = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToOutput: SharedFlow<Unit> = _scrollToOutput.asSharedFlow()

    val autoScrollEnabled = MutableStateFlow(true)

    // ========== Batch import ==========

    fun importSmaliMethods(pathString: String, filter: BatchFilter) {
        if (_isGenerating.value) return
        _isGenerating.value = true

        viewModelScope.launch {
            try {
                val (result, requests) = withContext(Dispatchers.IO) {
                    doImport(pathString, filter)
                }
                for (request in requests) {
                    composer.addRequest(request)
                }
                _hookQueue.value = ArrayList(composer.requests)
                _importResult.value = result
            } catch (e: Exception) {
                _importResult.value = ImportResult.error(
                    "Import failed: ${e.message ?: e.javaClass.simpleName}"
                )
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun consumeImportResult() {
        _importResult.value = null
    }

    private fun doImport(pathString: String, filter: BatchFilter): Pair<ImportResult, List<HookRequest>> {
        val file = File(pathString)
        if (!file.exists()) return ImportResult.error("Path does not exist: $pathString") to emptyList()
        if (!file.canRead()) return ImportResult.error("Cannot read path (check storage permissions): $pathString") to emptyList()

        val reader = SmaliFileReader()
        val entries = mutableListOf<SmaliMethodEntry>()

        when {
            file.isDirectory -> collectSmaliEntries(file, reader, entries)
            file.name.endsWith(".smali") -> entries.addAll(reader.parseLines(readLines(file)))
            else -> return ImportResult.error("Not a .smali file or directory") to emptyList()
        }

        val filtered = filter.apply(entries)
        val localParser = SmaliSignatureParser()
        val requests = mutableListOf<HookRequest>()
        for (entry in filtered) {
            try {
                val method = localParser.parse(entry.fullSignature)
                requests.add(HookRequest.java(method))
            } catch (_: IllegalArgumentException) { }
        }

        val result = if (requests.isNotEmpty()) ImportResult.success(requests.size) else ImportResult.empty()
        return result to requests
    }

    private fun collectSmaliEntries(dir: File, reader: SmaliFileReader, out: MutableList<SmaliMethodEntry>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                collectSmaliEntries(child, reader, out)
            } else if (child.name.endsWith(".smali") && child.canRead()) {
                try {
                    out.addAll(reader.parseLines(readLines(child)))
                } catch (_: IOException) { }
            }
        }
    }

    private fun readLines(file: File): List<String> {
        val lines = mutableListOf<String>()
        BufferedReader(FileReader(file)).use { br ->
            var line = br.readLine()
            while (line != null) {
                lines.add(line)
                line = br.readLine()
            }
        }
        return lines
    }

    data class ImportResult(
        val status: Status,
        val count: Int = 0,
        val errorMessage: String? = null
    ) {
        enum class Status { SUCCESS, EMPTY, ERROR }

        companion object {
            fun success(count: Int) = ImportResult(Status.SUCCESS, count)
            fun empty() = ImportResult(Status.EMPTY)
            fun error(message: String) = ImportResult(Status.ERROR, errorMessage = message)
        }
    }

    // ========== Hook queue management ==========

    fun addHook(request: HookRequest) {
        composer.addRequest(request)
        publishQueue()
    }

    fun removeHook(index: Int) {
        if (index in 0 until composer.size()) {
            composer.removeRequest(index)
            publishQueue()
        }
    }

    fun clearHooks() {
        composer.clear()
        publishQueue()
    }

    fun clearScriptOutput() {
        _javaScriptOutput.value = null
        _nativeScriptOutput.value = null
        _composedScriptOutput.value = null
    }

    val queueSize: Int get() = composer.size()

    fun composeHooks(options: CompositionOptions) {
        if (_isGenerating.value) return
        if (composer.size() == 0) {
            _composedErrorMessage.tryEmit("Queue is empty. Add hooks first.")
            return
        }
        _isGenerating.value = true
        val snapshot = ArrayList(composer.requests)

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ScriptComposer(snapshot).compose(options)
                }
                _javaScriptOutput.value = null
                _nativeScriptOutput.value = null
                _composedScriptOutput.value = result.scriptText
                _scrollToOutput.tryEmit(Unit)
            } catch (e: Exception) {
                _composedErrorMessage.tryEmit(e.message ?: "Composition failed")
                _composedScriptOutput.value = null
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private fun publishQueue() {
        _hookQueue.value = ArrayList(composer.requests)
    }

    // ========== Single-hook generation ==========

    fun generateJavaHook(smaliSignature: String, wrapInPerform: Boolean, timeoutMs: Int, enableStackTrace: Boolean = false) {
        if (_isGenerating.value) return
        _isGenerating.value = true

        viewModelScope.launch {
            try {
                val scriptText = withContext(Dispatchers.IO) {
                    val method = parser.parse(smaliSignature)
                    val request = HookRequest.java(method)
                    var result = javaGenerator.generate(request, enableStackTrace)
                    if (wrapInPerform) result = ScriptWrapper.wrapIfNeeded(result)
                    if (timeoutMs > 0) result = ScriptWrapper.wrapInSetTimeout(result, timeoutMs)
                    result.scriptText
                }
                _composedScriptOutput.value = null
                _javaScriptOutput.value = scriptText
                _scrollToOutput.tryEmit(Unit)
            } catch (e: Exception) {
                _javaErrorMessage.tryEmit(e.message ?: "Generation failed")
                _javaScriptOutput.value = null
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun generateNativeHook(symbol: NativeSymbol, wrapInPerform: Boolean, enableStackTrace: Boolean = false) {
        if (_isGenerating.value) return
        _isGenerating.value = true

        viewModelScope.launch {
            try {
                val scriptText = withContext(Dispatchers.IO) {
                    val timeoutMs = symbol.setTimeoutMs
                    val genSymbol = if (wrapInPerform && timeoutMs > 0) {
                        rebuildWithoutTimeout(symbol)
                    } else symbol

                    val request = HookRequest.nativeHook(genSymbol)
                    var result = nativeGenerator.generate(request, enableStackTrace)
                    if (wrapInPerform) result = ScriptWrapper.wrapInJavaPerform(result)
                    if (wrapInPerform && timeoutMs > 0) result = ScriptWrapper.wrapInSetTimeout(result, timeoutMs)
                    result.scriptText
                }
                _composedScriptOutput.value = null
                _nativeScriptOutput.value = scriptText
                _scrollToOutput.tryEmit(Unit)
            } catch (e: Exception) {
                _nativeErrorMessage.tryEmit(e.message ?: "Generation failed")
                _nativeScriptOutput.value = null
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private fun rebuildWithoutTimeout(original: NativeSymbol): NativeSymbol {
        val builder = NativeSymbol.Builder().argCount(original.argCount)
        if (original.targetMode == NativeSymbol.TargetMode.EXPORT) {
            builder.libName(original.libName).exportName(original.exportName)
            if (original.isWaitForLoad) builder.waitForLoad(true)
        } else {
            builder.address(original.address)
        }
        return builder.build()
    }

    // ========== HookRequest creation helpers ==========

    fun createJavaHookRequest(smaliSignature: String): HookRequest {
        val method = parser.parse(smaliSignature)
        return HookRequest.java(method)
    }

    // ========== Utility ==========

    data class ParseResult(val value: Int, val error: String?) {
        val isValid: Boolean get() = error == null

        companion object {
            fun success(value: Int) = ParseResult(value, null)
            fun error(message: String) = ParseResult(0, message)
        }
    }

    companion object {
        const val MAX_ARG_COUNT = NativeSymbol.MAX_ARG_COUNT
        const val MAX_TIMEOUT_MS = 999999

        fun safeParseInt(text: String?, defaultValue: Int, maxValue: Int): ParseResult {
            val trimmed = text?.trim().orEmpty()
            if (trimmed.isEmpty()) return ParseResult.success(defaultValue)
            return try {
                val value = trimmed.toLong()
                when {
                    value < 0 -> ParseResult.error("Value cannot be negative")
                    value > maxValue -> ParseResult.error("Value too large (max $maxValue)")
                    else -> ParseResult.success(value.toInt())
                }
            } catch (_: NumberFormatException) {
                ParseResult.error("Not a valid number")
            }
        }
    }
}
