package com.amrts.fridahelper.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.amrts.fridahelper.core.generator.CompositionOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Shows a dialog to collect CompositionOptions (wrapInPerform, setTimeoutMs)
 * before composing a multi-hook script.
 */
public final class ComposeDialogHelper {

    public interface OnComposeListener {
        void onCompose(CompositionOptions options);
    }

    private ComposeDialogHelper() { }

    public static void show(Context context, OnComposeListener listener) {
        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_compose_options, null);

        CheckBox checkPerform = dialogView.findViewById(R.id.check_wrap_perform);
        EditText editTimeout = dialogView.findViewById(R.id.edit_compose_timeout);

        checkPerform.setChecked(true);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_compose_title)
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_compose_positive, (dialog, which) -> {
                    boolean wrapInPerform = checkPerform.isChecked();
                    int timeoutMs = 0;
                    try {
                        String text = editTimeout.getText().toString().trim();
                        if (!text.isEmpty()) {
                            timeoutMs = Integer.parseInt(text);
                            if (timeoutMs < 0) timeoutMs = 0;
                        }
                    } catch (NumberFormatException ignored) { }

                    CompositionOptions options = CompositionOptions.builder()
                            .wrapInPerform(wrapInPerform)
                            .setTimeoutMs(timeoutMs)
                            .build();
                    listener.onCompose(options);
                })
                .setNegativeButton(R.string.dialog_compose_negative, null)
                .show();
    }
}
