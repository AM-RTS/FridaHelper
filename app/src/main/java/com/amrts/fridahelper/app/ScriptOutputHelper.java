package com.amrts.fridahelper.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

/**
 * Shared helper for output display, copy-to-clipboard, and file export.
 * Eliminates code duplication between JavaHookFragment and NativeHookFragment.
 */
public final class ScriptOutputHelper {

    private final View root;
    private final View outputSection;
    private final TextView textOutput;

    public ScriptOutputHelper(View root) {
        this.root = root;
        this.outputSection = root.findViewById(R.id.layout_output_section);
        this.textOutput = root.findViewById(R.id.text_output);

        MaterialButton btnCopy = root.findViewById(R.id.btn_copy);
        MaterialButton btnExport = root.findViewById(R.id.btn_export);

        btnCopy.setOnClickListener(v -> copyToClipboard());
        btnExport.setOnClickListener(v -> exportToFile());
    }

    public void show(String script) {
        textOutput.setText(script);
        outputSection.setVisibility(View.VISIBLE);
    }

    public void hide() {
        outputSection.setVisibility(View.GONE);
    }

    private void copyToClipboard() {
        CharSequence text = textOutput.getText();
        if (text == null || text.length() == 0) return;

        Context ctx = root.getContext();
        ClipboardManager clipboard = (ClipboardManager)
                ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("FridaHelper Script", text));
            Snackbar.make(root, R.string.msg_copied, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void exportToFile() {
        CharSequence text = textOutput.getText();
        if (text == null || text.length() == 0) return;

        ScriptExporter.ExportResult result =
                ScriptExporter.export(root.getContext(), text.toString());

        if (result.isSuccess()) {
            Snackbar.make(root,
                    root.getContext().getString(R.string.msg_exported, result.getFilename()),
                    Snackbar.LENGTH_LONG).show();
        } else {
            Snackbar.make(root,
                    root.getContext().getString(R.string.msg_export_failed, result.getError()),
                    Snackbar.LENGTH_LONG).show();
        }
    }
}
