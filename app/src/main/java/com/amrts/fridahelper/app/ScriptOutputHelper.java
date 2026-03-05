package com.amrts.fridahelper.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

/**
 * Shared helper for output display, edit toggle, copy-to-clipboard, and file export.
 *
 * Supports two modes:
 * - Read-only (default): script shown in a selectable TextView inside a HorizontalScrollView.
 * - Edit mode: script shown in an editable EditText, user can modify freely.
 *
 * Transitions:
 *   show(script) → read-only mode, stores original script for reset.
 *   Edit button  → switches to edit mode (EditText pre-filled with current text).
 *   Done button  → switches back to read-only with the (possibly edited) text.
 *   Reset button → reverts to the original generated script, returns to read-only.
 *   Copy/Export  → always uses the currently visible text (edited or original).
 */
public final class ScriptOutputHelper {

    private final View root;
    private final View outputSection;
    private final HorizontalScrollView scrollOutput;
    private final TextView textOutput;
    private final EditText editOutput;
    private final MaterialButton btnEdit;
    private final MaterialButton btnReset;

    private String originalScript;
    private boolean editing;

    public ScriptOutputHelper(View root) {
        this.root = root;
        this.outputSection = root.findViewById(R.id.layout_output_section);
        this.scrollOutput = root.findViewById(R.id.scroll_output);
        this.textOutput = root.findViewById(R.id.text_output);
        this.editOutput = root.findViewById(R.id.edit_output);
        this.btnEdit = root.findViewById(R.id.btn_edit);
        this.btnReset = root.findViewById(R.id.btn_reset);

        MaterialButton btnCopy = root.findViewById(R.id.btn_copy);
        MaterialButton btnExport = root.findViewById(R.id.btn_export);

        btnCopy.setOnClickListener(v -> copyToClipboard());
        btnExport.setOnClickListener(v -> exportToFile());
        btnEdit.setOnClickListener(v -> toggleEdit());
        btnReset.setOnClickListener(v -> reset());
    }

    public void show(String script) {
        this.originalScript = script;
        this.editing = false;

        textOutput.setText(script);
        editOutput.setText(script);

        scrollOutput.setVisibility(View.VISIBLE);
        editOutput.setVisibility(View.GONE);
        btnEdit.setText(R.string.btn_edit);
        btnReset.setVisibility(View.GONE);
        outputSection.setVisibility(View.VISIBLE);
    }

    public void hide() {
        this.editing = false;
        outputSection.setVisibility(View.GONE);
    }

    private void toggleEdit() {
        if (editing) {
            exitEditMode();
        } else {
            enterEditMode();
        }
    }

    private void enterEditMode() {
        editing = true;
        editOutput.setText(textOutput.getText());
        scrollOutput.setVisibility(View.GONE);
        editOutput.setVisibility(View.VISIBLE);
        editOutput.requestFocus();
        btnEdit.setText(R.string.btn_done);
        btnReset.setVisibility(View.VISIBLE);
    }

    private void exitEditMode() {
        editing = false;
        CharSequence edited = editOutput.getText();
        textOutput.setText(edited != null ? edited.toString() : "");
        editOutput.setVisibility(View.GONE);
        scrollOutput.setVisibility(View.VISIBLE);
        btnEdit.setText(R.string.btn_edit);
    }

    private void reset() {
        editing = false;
        textOutput.setText(originalScript);
        editOutput.setText(originalScript);
        editOutput.setVisibility(View.GONE);
        scrollOutput.setVisibility(View.VISIBLE);
        btnEdit.setText(R.string.btn_edit);
        btnReset.setVisibility(View.GONE);
    }

    private CharSequence getCurrentText() {
        if (editing) {
            return editOutput.getText();
        }
        return textOutput.getText();
    }

    private void copyToClipboard() {
        CharSequence text = getCurrentText();
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
        CharSequence text = getCurrentText();
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
