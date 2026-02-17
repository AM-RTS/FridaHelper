package com.amrts.fridahelper.app;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Saves generated scripts to Documents/FridaHelper/ as .js files.
 *
 * - API 29+: Uses MediaStore with RELATIVE_PATH (no permissions required).
 * - API 24-28: Falls back to app-specific external storage
 *   (Android/data/com.amrts.fridahelper.app/files/Documents/FridaHelper/).
 *   No storage permissions needed. File is accessible via file manager.
 *
 * Filename format: frida_hook_yyyyMMdd_HHmmss.js
 */
public final class ScriptExporter {

    private static final String SUBDIR = "FridaHelper";
    private static final String PREFIX = "frida_hook_";
    private static final String EXTENSION = ".js";
    private static final String MIME_TYPE = "application/javascript";
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);

    private ScriptExporter() { }

    /**
     * Result of a script export operation.
     */
    public static final class ExportResult {
        private final String filename;
        private final String error;

        private ExportResult(String filename, String error) {
            this.filename = filename;
            this.error = error;
        }

        public static ExportResult success(String filename) {
            return new ExportResult(filename, null);
        }

        public static ExportResult failure(String error) {
            return new ExportResult(null, error);
        }

        public boolean isSuccess() { return error == null; }
        public String getFilename() { return filename; }
        public String getError() { return error; }
    }

    /**
     * Exports script content to a .js file.
     *
     * @param context Application context
     * @param scriptContent The script text to save
     * @return ExportResult with filename on success, error message on failure
     */
    public static ExportResult export(Context context, String scriptContent) {
        String filename = PREFIX + DATE_FORMAT.format(new Date()) + EXTENSION;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportViaMediaStore(context, filename, scriptContent);
            } else {
                exportToAppStorage(context, filename, scriptContent);
            }
            return ExportResult.success(filename);
        } catch (IOException e) {
            return ExportResult.failure("Failed to save: " + e.getMessage());
        }
    }

    /**
     * API 29+: Write via MediaStore to Documents/FridaHelper/.
     */
    private static void exportViaMediaStore(Context context, String filename, String content)
            throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        values.put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOCUMENTS + File.separator + SUBDIR);

        Uri uri = context.getContentResolver()
                .insert(MediaStore.Files.getContentUri("external"), values);

        if (uri == null) {
            throw new IOException("MediaStore insert returned null URI");
        }

        try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
            if (os == null) {
                throw new IOException("Could not open output stream for URI: " + uri);
            }
            os.write(content.getBytes("UTF-8"));
            os.flush();
        }
    }

    /**
     * API 24-28: Write to app-specific external storage.
     * Path: Android/data/{package}/files/Documents/FridaHelper/
     */
    private static void exportToAppStorage(Context context, String filename, String content)
            throws IOException {
        File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), SUBDIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create directory: " + dir.getAbsolutePath());
        }

        File file = new File(dir, filename);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes("UTF-8"));
            fos.flush();
        }
    }
}
