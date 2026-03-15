package com.amrts.fridahelper.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amrts.fridahelper.app.HookViewModel
import com.amrts.fridahelper.app.ui.components.BatchImportDialog
import com.amrts.fridahelper.app.ui.components.HookQueueList
import com.amrts.fridahelper.core.generator.CompositionOptions
import com.amrts.fridahelper.app.ui.components.ScriptOutput
import kotlinx.coroutines.launch
import android.content.pm.PackageManager
import androidx.compose.material3.OutlinedTextField

@Composable
fun JavaHookScreen(
    viewModel: HookViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val scriptOutput by viewModel.javaScriptOutput.collectAsStateWithLifecycle()
    val composedOutput by viewModel.composedScriptOutput.collectAsStateWithLifecycle()
    val hookQueue by viewModel.hookQueue.collectAsStateWithLifecycle()
    val importResult by viewModel.importResult.collectAsStateWithLifecycle()

    var signature by remember { mutableStateOf("") }
    var signatureError by remember { mutableStateOf<String?>(null) }
    var timeoutText by remember { mutableStateOf("0") }
    var timeoutError by remember { mutableStateOf<String?>(null) }
    var wrapInPerform by remember { mutableStateOf(true) }
    var enableStackTrace by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val displayedScript = composedOutput ?: scriptOutput

    // Handle import result
    LaunchedEffect(importResult) {
        importResult?.let { result ->
            val msg = when (result.status) {
                HookViewModel.ImportResult.Status.SUCCESS -> "Imported ${result.count} hooks to queue"
                HookViewModel.ImportResult.Status.EMPTY -> "No hookable methods found"
                HookViewModel.ImportResult.Status.ERROR -> result.errorMessage ?: "Unknown error"
            }
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeImportResult()
        }
    }

    // Handle errors (one-shot SharedFlow events)
    LaunchedEffect(Unit) {
        viewModel.javaErrorMessage.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.composedErrorMessage.collect { snackbarHostState.showSnackbar(it) }
    }

    // Permission launcher for legacy storage
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showImportDialog = true
        else scope.launch { snackbarHostState.showSnackbar("Storage permission required") }
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
            text = "Enter a smali signature to generate a Frida Java hook script.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        FilledTonalButton(
            shape = RoundedCornerShape(24.dp),
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        showImportDialog = true
                    } else {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }
                        scope.launch { snackbarHostState.showSnackbar("Grant storage access, then try again") }
                    }
                } else {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        showImportDialog = true
                    } else {
                        permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Import .smali file(s)") }

        OutlinedTextField(
            value = signature,
            onValueChange = { signature = it; signatureError = null },
            label = { Text("Smali signature") },
            placeholder = { Text("Lcom/example/Foo;->bar(I)V", fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
            isError = signatureError != null,
            supportingText = signatureError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )

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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Switch(checked = wrapInPerform, onCheckedChange = { wrapInPerform = it })
            Text("Wrap in Java.perform", modifier = Modifier.padding(start = 8.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Switch(checked = enableStackTrace, onCheckedChange = { enableStackTrace = it })
            Text("Enable Stack Trace", modifier = Modifier.padding(start = 8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    signatureError = null; timeoutError = null
                    val sig = signature.trim()
                    if (sig.isEmpty()) { signatureError = "Please enter a smali signature"; return@Button }
                    val tr = HookViewModel.safeParseInt(timeoutText, 0, HookViewModel.MAX_TIMEOUT_MS)
                    if (!tr.isValid) { timeoutError = tr.error; return@Button }
                    viewModel.generateJavaHook(sig, wrapInPerform, tr.value, enableStackTrace)
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            ) { Text("Generate") }

            FilledTonalButton(
                onClick = {
                    signatureError = null
                    val sig = signature.trim()
                    if (sig.isEmpty()) { signatureError = "Please enter a smali signature"; return@FilledTonalButton }
                    try {
                        val request = viewModel.createJavaHookRequest(sig)
                        viewModel.addHook(request)
                        signature = ""
                        scope.launch { snackbarHostState.showSnackbar("Hook added. Queue: ${viewModel.queueSize}") }
                    } catch (e: IllegalArgumentException) {
                        signatureError = e.message
                    }
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
                                    .wrapInPerform(wrapInPerform)
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

    // Dialogs
    if (showImportDialog) {
        BatchImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { path, filter -> viewModel.importSmaliMethods(path, filter) }
        )
    }
    
}
