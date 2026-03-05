package com.amrts.fridahelper.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;

import com.amrts.fridahelper.core.generator.CompositionOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

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
        TextInputEditText editTimeout = dialogView.findViewById(R.id.edit_compose_timeout);

        checkPerform.setChecked(true);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_compose_title)
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_compose_positive, (dialog, which) -> {
                    boolean wrapInPerform = checkPerform.isChecked();

                    String text = editTimeout.getText() != null
                            ? editTimeout.getText().toString().trim() : "";
                    HookViewModel.ParseResult result =
                            HookViewModel.safeParseInt(text, 0, HookViewModel.MAX_TIMEOUT_MS);

                    int timeoutMs = result.isValid() ? result.getValue() : 0;

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
