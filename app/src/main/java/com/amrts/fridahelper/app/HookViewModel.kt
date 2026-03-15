package com.amrts.fridahelper.app

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.Collections

/**
 * Shared ViewModel for both Java and Native hook generation and multi-hook composition.
 *
 * Uses coroutines via viewModelScope for background work.
 * Separate LiveData streams for Java, Native, and composed output.
 * Hook queue is maintained internally via ScriptComposer.
 */
class HookViewModel : ViewModel() {

    private val parser = SmaliSignatureParser()
    private val javaGenerator = JavaHookGenerator()
    private val nativeGenerator = NativeHookGenerator()
    private val composer = ScriptComposer()

    private val _javaScriptOutput = MutableLiveData<String?>()
    val javaScriptOutput: LiveData<String?> get() = _javaScriptOutput

    private val _javaErrorMessage = MutableLiveData<String?>()
    val javaErrorMessage: LiveData<String?> get() = _javaErrorMessage

    private val _nativeScriptOutput = MutableLiveData<String?>()
    val nativeScriptOutput: LiveData<String?> get() = _nativeScriptOutput

    private val _nativeErrorMessage = MutableLiveData<String?>()
    val nativeErrorMessage: LiveData<String?> get() = _nativeErrorMessage

    private val _composedScriptOutput = MutableLiveData<String?>()
    val composedScriptOutput: LiveData<String?> get() = _composedScriptOutput

    private val _composedErrorMessage = MutableLiveData<String?>()
    val composedErrorMessage: LiveData<String?> get() = _composedErrorMessage

    private val _hookQueue = MutableLiveData<List<HookRequest>>(emptyList())
    val hookQueue: LiveData<List<HookRequest>> get() = _hookQueue

    private val _importResult = MutableLiveData<ImportResult>()
    val importResult: LiveData<ImportResult> get() = _importResult

    private val _isGenerating = MutableLiveData(false)
    val isGenerating: LiveData<Boolean> get() = _isGenerating

    // ========== Batch import ==========

    fun importSmaliMethods(pathString: String, filter: BatchFilter) {
        if (_isGenerating.value == true) return
        _isGenerating.value = true

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    doImport(pathString, filter)
                }
                _hookQueue.value = Collections.unmodifiableList(ArrayList(composer.requests))
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

    private fun doImport(pathString: String, filter: BatchFilter): ImportResult {
        val file = File(pathString)
        if (!file.exists()) return ImportResult.error("Path does not exist: $pathString")
        if (!file.canRead()) return ImportResult.error("Cannot read path (check storage permissions): $pathString")

        val reader = SmaliFileReader()
        val entries = mutableListOf<SmaliMethodEntry>()

        when {
            file.isDirectory -> collectSmaliEntries(file, reader, entries)
            file.name.endsWith(".smali") -> entries.addAll(reader.parseLines(readLines(file)))
            else -> return ImportResult.error("Not a .smali file or directory")
        }

        val filtered = filter.apply(entries)
        val localParser = SmaliSignatureParser()
        var added = 0
        for (entry in filtered) {
            try {
                val method = localParser.parse(entry.fullSignature)
                composer.addRequest(HookRequest.java(method))
                added++
            } catch (_: IllegalArgumentException) { }
        }

        return if (added > 0) ImportResult.success(added) else ImportResult.empty()
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

    val queueSize: Int get() = composer.size()

    fun composeHooks(options: CompositionOptions) {
        if (_isGenerating.value == true) return
        if (composer.size() == 0) {
            _composedErrorMessage.value = "Queue is empty. Add hooks first."
            return
        }
        _isGenerating.value = true
        val snapshot = ArrayList(composer.requests)

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ScriptComposer(snapshot).compose(options)
                }
                _composedScriptOutput.value = result.scriptText
                _composedErrorMessage.value = null
            } catch (e: Exception) {
                _composedErrorMessage.value = e.message
                _composedScriptOutput.value = null
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private fun publishQueue() {
        _hookQueue.value = Collections.unmodifiableList(ArrayList(composer.requests))
    }

    // ========== Single-hook generation ==========

    fun generateJavaHook(smaliSignature: String, wrapInPerform: Boolean, timeoutMs: Int) {
        if (_isGenerating.value == true) return
        _isGenerating.value = true

        viewModelScope.launch {
            try {
                val scriptText = withContext(Dispatchers.IO) {
                    val method = parser.parse(smaliSignature)
                    val request = HookRequest.java(method)
                    var result = javaGenerator.generate(request)
                    if (wrapInPerform) result = ScriptWrapper.wrapIfNeeded(result)
                    if (timeoutMs > 0) result = ScriptWrapper.wrapInSetTimeout(result, timeoutMs)
                    result.scriptText
                }
                _javaScriptOutput.value = scriptText
                _javaErrorMessage.value = null
            } catch (e: Exception) {
                _javaErrorMessage.value = e.message
                _javaScriptOutput.value = null
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun generateNativeHook(symbol: NativeSymbol, wrapInPerform: Boolean) {
        if (_isGenerating.value == true) return
        _isGenerating.value = true

        viewModelScope.launch {
            try {
                val scriptText = withContext(Dispatchers.IO) {
                    val timeoutMs = symbol.setTimeoutMs
                    val genSymbol = if (wrapInPerform && timeoutMs > 0) {
                        rebuildWithoutTimeout(symbol)
                    } else symbol

                    val request = HookRequest.nativeHook(genSymbol)
                    var result = nativeGenerator.generate(request)
                    if (wrapInPerform) result = ScriptWrapper.wrapInJavaPerform(result)
                    if (wrapInPerform && timeoutMs > 0) result = ScriptWrapper.wrapInSetTimeout(result, timeoutMs)
                    result.scriptText
                }
                _nativeScriptOutput.value = scriptText
                _nativeErrorMessage.value = null
            } catch (e: Exception) {
                _nativeErrorMessage.value = e.message
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

        @JvmStatic
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
