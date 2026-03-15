package com.amrts.fridahelper.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves generated scripts to Documents/FridaHelper/ as .js files.
 *
 * - API 29+: Uses MediaStore with RELATIVE_PATH (no permissions required).
 * - API 24-28: Falls back to app-specific external storage.
 */
object ScriptExporter {

    private const val SUBDIR = "FridaHelper"
    private const val PREFIX = "frida_hook_"
    private const val EXTENSION = ".js"
    private const val MIME_TYPE = "application/javascript"

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    data class ExportResult(
        val filename: String? = null,
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = error == null

        companion object {
            fun success(filename: String) = ExportResult(filename = filename)
            fun failure(error: String) = ExportResult(error = error)
        }
    }

    fun export(context: Context, scriptContent: String, customName: String? = null): ExportResult {
        val baseName = if (!customName.isNullOrBlank()) customName.trim() else "$PREFIX${timestamp()}"
        val filename = if (baseName.endsWith(EXTENSION)) baseName else "$baseName$EXTENSION"
        val customDir = ThemeManager.getExportDir(context)
        return try {
            if (customDir.isNotBlank()) {
                exportToCustomDir(customDir, filename, scriptContent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportViaMediaStore(context, filename, scriptContent)
            } else {
                exportToAppStorage(context, filename, scriptContent)
            }
            ExportResult.success(filename)
        } catch (e: IOException) {
            ExportResult.failure("Failed to save: ${e.message}")
        }
    }

    private fun exportToCustomDir(dirPath: String, filename: String, content: String) {
        val dir = File(dirPath)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Failed to create directory: ${dir.absolutePath}")
        }
        if (!dir.canWrite()) {
            throw IOException("Cannot write to directory: ${dir.absolutePath}")
        }
        FileOutputStream(File(dir, filename)).use { fos ->
            fos.write(content.toByteArray(Charsets.UTF_8))
            fos.flush()
        }
    }

    private fun exportViaMediaStore(context: Context, filename: String, content: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOCUMENTS + File.separator + SUBDIR)
        }

        val uri = context.contentResolver
            .insert(MediaStore.Files.getContentUri("external"), values)
            ?: throw IOException("MediaStore insert returned null URI")

        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(content.toByteArray(Charsets.UTF_8))
            os.flush()
        } ?: throw IOException("Could not open output stream for URI: $uri")
    }

    private fun exportToAppStorage(context: Context, filename: String, content: String) {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), SUBDIR)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Failed to create directory: ${dir.absolutePath}")
        }

        FileOutputStream(File(dir, filename)).use { fos ->
            fos.write(content.toByteArray(Charsets.UTF_8))
            fos.flush()
        }
    }
}
