package com.amrts.fridahelper.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amrts.fridahelper.app.HookViewModel
import com.amrts.fridahelper.app.ui.components.HookQueueList
import com.amrts.fridahelper.core.generator.CompositionOptions
import com.amrts.fridahelper.app.ui.components.ScriptOutput
import com.amrts.fridahelper.core.model.HookRequest
import com.amrts.fridahelper.core.model.NativeSymbol
import kotlinx.coroutines.launch

private const val MODE_EXPORT = 0
private const val MODE_ADDRESS = 1

private val HEX_PATTERN = Regex("^(0[xX])?[0-9a-fA-F]+$")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeHookScreen(
    viewModel: HookViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val scriptOutput by viewModel.nativeScriptOutput.collectAsStateWithLifecycle()
    val composedOutput by viewModel.composedScriptOutput.collectAsStateWithLifecycle()
    val hookQueue by viewModel.hookQueue.collectAsStateWithLifecycle()
    

    var targetMode by remember { mutableIntStateOf(MODE_EXPORT) }
    var libName by remember { mutableStateOf("") }
    var exportName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var argCountText by remember { mutableStateOf("0") }
    var timeoutText by remember { mutableStateOf("0") }
    var waitForLoad by remember { mutableStateOf(false) }
    var wrapPerform by remember { mutableStateOf(false) }
    var enableStackTrace by remember { mutableStateOf(false) }
    

    // Field errors
    var libNameError by remember { mutableStateOf<String?>(null) }
    var exportNameError by remember { mutableStateOf<String?>(null) }
    var addressError by remember { mutableStateOf<String?>(null) }
    var argCountError by remember { mutableStateOf<String?>(null) }
    var timeoutError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val displayedScript = composedOutput ?: scriptOutput

    LaunchedEffect(Unit) { viewModel.nativeErrorMessage.collect { snackbarHostState.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.composedErrorMessage.collect { snackbarHostState.showSnackbar(it) } }

    fun clearErrors() {
        libNameError = null; exportNameError = null; addressError = null
        argCountError = null; timeoutError = null
    }

    fun buildSymbol(): NativeSymbol? {
        clearErrors()
        var hasError = false
        val builder = NativeSymbol.Builder()
        val lib = libName.trim()
        builder.libName(lib.ifEmpty { null })

        if (targetMode == MODE_EXPORT) {
            val exp = exportName.trim()
            if (exp.isEmpty()) { exportNameError = "Please enter an export name"; hasError = true }
            if (waitForLoad && lib.isEmpty()) { libNameError = "Wait for load requires a library name"; hasError = true }
            if (!hasError) {
                builder.exportName(exp)
                if (waitForLoad) builder.waitForLoad(true)
            }
        } else {
            val addr = address.trim()
            if (addr.isEmpty()) {
                addressError = "Please enter an address"; hasError = true
            } else if (!isValidHexAddress(addr)) {
                addressError = "Invalid hex address (e.g. 0xDEAD or 1A2B)"; hasError = true
            }
            if (waitForLoad && lib.isEmpty()) { libNameError = "Wait for load requires a library name"; hasError = true }
            if (!hasError) {
                builder.address(addr)
                if (waitForLoad) builder.waitForLoad(true)
            }
        }

        val argResult = HookViewModel.safeParseInt(argCountText, 0, HookViewModel.MAX_ARG_COUNT)
        if (!argResult.isValid) { argCountError = argResult.error; hasError = true }
        val timeResult = HookViewModel.safeParseInt(timeoutText, 0, HookViewModel.MAX_TIMEOUT_MS)
        if (!timeResult.isValid) { timeoutError = timeResult.error; hasError = true }

        if (hasError) return null
        builder.argCount(argResult.value)
        if (timeResult.value > 0) builder.setTimeoutMs(timeResult.value)

        return try { builder.build() } catch (e: IllegalArgumentException) {
            exportNameError = e.message; null
        }
    }

    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.scrollToOutput.collect {
            if (viewModel.autoScrollEnabled.value) {
                kotlinx.coroutines.delay(350)
                scrollState.animateScrollTo(
                    scrollState.maxValue,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 500,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .imePadding()
    ) {
        Text(
            text = "Select target mode and fill in the fields to generate a native hook.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text("Target Mode", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = targetMode == MODE_EXPORT,
                onClick = { targetMode = MODE_EXPORT },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("Export name") }
            SegmentedButton(
                selected = targetMode == MODE_ADDRESS,
                onClick = { targetMode = MODE_ADDRESS },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("Address (ptr)") }
        }

        val monoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)

        // Library name (shared)
        OutlinedTextField(
            value = libName,
            onValueChange = { libName = it; libNameError = null },
            label = { Text("Library name (optional)") },
            placeholder = { Text("libnative.so", fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
            isError = libNameError != null,
            supportingText = libNameError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            singleLine = true,
            textStyle = monoStyle,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )

        // Export name (export mode)
        AnimatedVisibility(visible = targetMode == MODE_EXPORT) {
            OutlinedTextField(
                value = exportName,
                onValueChange = { exportName = it; exportNameError = null },
                label = { Text("Export/symbol name") },
                isError = exportNameError != null,
                supportingText = exportNameError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true,
                textStyle = monoStyle,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        // Address (address mode)
        AnimatedVisibility(visible = targetMode == MODE_ADDRESS) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it; addressError = null },
                label = { Text("Address (e.g. 0xDEAD)") },
                isError = addressError != null,
                supportingText = addressError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true,
                textStyle = monoStyle,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text("0x1234ABCD", fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
            )
        }

        // Arg count
        OutlinedTextField(
            value = argCountText,
            onValueChange = { argCountText = it; argCountError = null },
            label = { Text("Number of arguments") },
            isError = argCountError != null,
            supportingText = argCountError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )

        // Timeout
        OutlinedTextField(
            value = timeoutText,
            onValueChange = { timeoutText = it; timeoutError = null },
            label = { Text("setTimeout delay (ms, 0 = none)") },
            isError = timeoutError != null,
            supportingText = timeoutError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        // Switches
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            Switch(checked = waitForLoad, onCheckedChange = { waitForLoad = it })
            Text("Wait for library loading", modifier = Modifier.padding(start = 8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Switch(checked = wrapPerform, onCheckedChange = { wrapPerform = it })
            Text("Wrap in Java.perform", modifier = Modifier.padding(start = 8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Switch(checked = enableStackTrace, onCheckedChange = { enableStackTrace = it })
            Text("Enable Stack Trace", modifier = Modifier.padding(start = 8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    val symbol = buildSymbol() ?: return@Button
                    viewModel.generateNativeHook(symbol, wrapPerform, enableStackTrace)
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            ) { Text("Generate") }

            FilledTonalButton(
                onClick = {
                    val symbol = buildSymbol() ?: return@FilledTonalButton
                    val request = HookRequest.nativeHook(symbol)
                    viewModel.addHook(request)
                    exportName = ""; address = ""; argCountText = "0"; timeoutText = "0"
                    scope.launch { snackbarHostState.showSnackbar("Hook added. Queue: ${viewModel.queueSize}") }
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            ) { Text("Add to Queue") }
        }

        AnimatedVisibility(
            visible = hookQueue.isNotEmpty(),
            enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 4 },
            exit = fadeOut(tween(200, easing = FastOutLinearInEasing)) +
                    slideOutVertically(tween(200, easing = FastOutLinearInEasing)) { it / 4 }
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val tr = HookViewModel.safeParseInt(timeoutText, 0, HookViewModel.MAX_TIMEOUT_MS)
                            val timeout = if (tr.isValid) tr.value else 0
                            viewModel.composeHooks(
                                CompositionOptions.builder()
                                    .wrapInPerform(wrapPerform)
                                    .enableStackTrace(enableStackTrace)
                                    .setTimeoutMs(timeout)
                                    .build()
                            )
                        },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Compose Script") }
                    OutlinedButton(
                        onClick = {
                            viewModel.clearHooks()
                            scope.launch { snackbarHostState.showSnackbar("Queue cleared") }
                        },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Clear Queue") }
                }
                HookQueueList(
                    queue = hookQueue,
                    onRemove = { viewModel.removeHook(it) },
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = displayedScript != null,
            enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 4 },
            exit = fadeOut(tween(200, easing = FastOutLinearInEasing))
        ) {
            displayedScript?.let { script ->
                ScriptOutput(
                    script = script,
                    snackbarHostState = snackbarHostState,
                    onClear = { viewModel.clearScriptOutput() }
                )
            }
        }
    }
}

private fun isValidHexAddress(input: String): Boolean = HEX_PATTERN.matches(input)
