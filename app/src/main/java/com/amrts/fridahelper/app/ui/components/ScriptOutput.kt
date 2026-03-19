package com.amrts.fridahelper.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amrts.fridahelper.app.ScriptExporter
import com.amrts.fridahelper.app.theme.CodeBg
import com.amrts.fridahelper.app.theme.CodeText
import kotlinx.coroutines.launch

private val JsSyntaxTransformation = object : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        return TransformedText(highlightJs(text.text), OffsetMapping.Identity)
    }
}

@Composable
fun ScriptOutput(
    script: String,
    snackbarHostState: SnackbarHostState,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    var wrapping by remember { mutableStateOf(false) }
    var editBuffer by remember(script) { mutableStateOf(script) }
    var showExportDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(modifier.padding(top = 20.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Output",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { wrapping = !wrapping }) {
                Text(if (wrapping) "No Wrap" else "Wrap")
            }
            TextButton(onClick = { editing = !editing }) {
                Text(if (editing) "Done" else "Edit")
            }
            if (editing) {
                TextButton(onClick = { editBuffer = script }) {
                    Text("Reset")
                }
            }
            if (onClear != null) {
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
            }
        }

        val scrollModifier = if (!wrapping) Modifier.horizontalScroll(rememberScrollState()) else Modifier
        val codeStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            color = CodeText,
            fontSize = 12.sp
        )

        val codeBlockShape = RoundedCornerShape(12.dp)
        if (editing) {
            BasicTextField(
                value = editBuffer,
                onValueChange = { editBuffer = it },
                textStyle = codeStyle,
                visualTransformation = JsSyntaxTransformation,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CodeBg, codeBlockShape)
                    .padding(16.dp)
                    .then(scrollModifier)
            )
        } else {
            val highlighted = remember(editBuffer) { highlightJs(editBuffer) }
            SelectionContainer {
                Text(
                    text = highlighted,
                    style = codeStyle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CodeBg, codeBlockShape)
                        .padding(16.dp)
                        .then(scrollModifier)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Frida Script", editBuffer))
                    scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Copy") }

            OutlinedButton(
                onClick = { showExportDialog = true },
                modifier = Modifier.weight(1f)
            ) { Text("Export") }
        }
    }

    if (showExportDialog) {
        ExportFilenameDialog(
            onDismiss = { showExportDialog = false },
            onExport = { customName ->
                showExportDialog = false
                val result = ScriptExporter.export(context, editBuffer, customName.ifBlank { null })
                val msg = if (result.isSuccess) "Saved: ${result.filename}" else "Export failed: ${result.error}"
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }
}

@Composable
private fun ExportFilenameDialog(
    onDismiss: () -> Unit,
    onExport: (String) -> Unit
) {
    var filename by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Script") },
        text = {
            OutlinedTextField(
                value = filename,
                onValueChange = { filename = it },
                label = { Text("Filename (optional)") },
                placeholder = { Text("Leave blank for auto-name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onExport(filename.trim()) }) { Text("Export") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
