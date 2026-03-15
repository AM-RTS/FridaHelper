package com.amrts.fridahelper.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.amrts.fridahelper.core.batch.BatchFilter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Shows a dialog to collect a .smali file/directory path and filter options,
 * then triggers batch import into the hook queue.
 */
public final class BatchImportDialogHelper {

    public interface OnImportListener {
        void onImport(String path, BatchFilter filter);
    }

    private BatchImportDialogHelper() { }

    public static void show(Context context, OnImportListener listener) {
        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_batch_import, null);

        TextInputLayout layoutPath = dialogView.findViewById(R.id.layout_import_path);
        TextInputEditText editPath = dialogView.findViewById(R.id.edit_import_path);
        MaterialSwitch switchSkipCtors = dialogView.findViewById(R.id.switch_skip_constructors);
        TextInputLayout layoutInclude = dialogView.findViewById(R.id.layout_include_classes);
        TextInputEditText editInclude = dialogView.findViewById(R.id.edit_include_classes);
        TextInputLayout layoutExclude = dialogView.findViewById(R.id.layout_exclude_methods);
        TextInputEditText editExclude = dialogView.findViewById(R.id.edit_exclude_methods);

        switchSkipCtors.setChecked(true);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_import_title)
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_import_positive, null)
                .setNegativeButton(R.string.dialog_import_negative, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    layoutPath.setError(null);
                    layoutInclude.setError(null);
                    layoutExclude.setError(null);

                    String path = getText(editPath);
                    String includeClasses = getText(editInclude);
                    String excludeMethods = getText(editExclude);

                    if (path.isEmpty()) {
                        layoutPath.setError(context.getString(R.string.msg_error_empty_path));
                        return;
                    }

                    if (!includeClasses.isEmpty() && !isValidRegex(includeClasses)) {
                        layoutInclude.setError(context.getString(R.string.msg_error_invalid_regex));
                        return;
                    }
                    if (!excludeMethods.isEmpty() && !isValidRegex(excludeMethods)) {
                        layoutExclude.setError(context.getString(R.string.msg_error_invalid_regex));
                        return;
                    }

                    BatchFilter.Builder builder = BatchFilter.builder()
                            .skipConstructors(switchSkipCtors.isChecked());
                    if (!includeClasses.isEmpty()) {
                        builder.includeClasses(includeClasses);
                    }
                    if (!excludeMethods.isEmpty()) {
                        builder.excludeMethods(excludeMethods);
                    }

                    dialog.dismiss();
                    listener.onImport(path, builder.build());
                }));

        dialog.show();
    }

    private static String getText(TextInputEditText edit) {
        return edit.getText() != null ? edit.getText().toString().trim() : "";
    }

    private static boolean isValidRegex(String regex) {
        try {
            Pattern.compile(regex);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }
}
