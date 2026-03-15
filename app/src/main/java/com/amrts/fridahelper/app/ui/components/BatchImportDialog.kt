package com.amrts.fridahelper.app.ui.components

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amrts.fridahelper.core.batch.BatchFilter

private fun treeUriToFilePath(uri: Uri): String? {
    return try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":", limit = 2)
        if (parts[0] == "primary") {
            val subPath = parts.getOrElse(1) { "" }
            val base = Environment.getExternalStorageDirectory().absolutePath
            if (subPath.isEmpty()) base else "$base/$subPath"
        } else null
    } catch (_: Exception) { null }
}

@Composable
fun BatchImportDialog(
    onDismiss: () -> Unit,
    onImport: (path: String, filter: BatchFilter) -> Unit
) {
    var path by remember { mutableStateOf("") }
    var skipConstructors by remember { mutableStateOf(true) }
    var includeClasses by remember { mutableStateOf("") }
    var excludeMethods by remember { mutableStateOf("") }
    var pathError by remember { mutableStateOf<String?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val filePath = treeUriToFilePath(it)
            if (filePath != null) {
                path = filePath
                pathError = null
            } else {
                pathError = "Could not resolve path from selected folder"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Batch Import .smali") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it; pathError = null },
                    label = { Text("Path to .smali file or directory") },
                    isError = pathError != null,
                    supportingText = pathError?.let { { Text(it) } },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { folderPicker.launch(null) }) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = "Browse")
                        }
                    },
                    modifier = Modifier
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = skipConstructors, onCheckedChange = { skipConstructors = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Skip constructors")
                }
                OutlinedTextField(
                    value = includeClasses,
                    onValueChange = { includeClasses = it },
                    label = { Text("Include classes (regex, optional)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = excludeMethods,
                    onValueChange = { excludeMethods = it },
                    label = { Text("Exclude methods (regex, optional)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = path.trim()
                if (trimmed.isEmpty()) {
                    pathError = "Path is required"
                    return@TextButton
                }
                val builder = BatchFilter.Builder().skipConstructors(skipConstructors)
                if (includeClasses.isNotBlank()) builder.includeClasses(includeClasses.trim())
                if (excludeMethods.isNotBlank()) builder.excludeMethods(excludeMethods.trim())
                onImport(trimmed, builder.build())
                onDismiss()
            }) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
