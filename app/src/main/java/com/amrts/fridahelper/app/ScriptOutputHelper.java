package com.amrts.fridahelper.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.Scroller;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

/**
 * Shared helper for output display, edit toggle, copy-to-clipboard, and file export.
 *
 * Supports three display modes:
 * - Read-only no-wrap (default): highlighted script in a HorizontalScrollView.
 * - Read-only wrapped: highlighted script in a wrapping TextView.
 * - Edit mode: highlighted text in an editable EditText, re-highlighted live with debounce.
 */
public final class ScriptOutputHelper {

    private static final int SMALL_TEXT = 2000;
    private static final int MEDIUM_TEXT = 5000;
    private static final int LARGE_TEXT = 10000;

    private final View root;
    private final View outputSection;
    private final HorizontalScrollView scrollOutput;
    private final TextView textOutput;
    private final TextView textOutputWrap;
    private final EditText editOutput;
    private final MaterialButton btnEdit;
    private final MaterialButton btnReset;
    private final MaterialButton btnWrap;

    private final JsSyntaxHighlighter highlighter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable highlightRunnable = this::rehighlightEdit;

    private String originalScript;
    private boolean editing;
    private boolean wrapping;
    private boolean suppressWatcher;

    public ScriptOutputHelper(View root) {
        this.root = root;
        this.outputSection = root.findViewById(R.id.layout_output_section);
        this.scrollOutput = root.findViewById(R.id.scroll_output);
        this.textOutput = root.findViewById(R.id.text_output);
        this.textOutputWrap = root.findViewById(R.id.text_output_wrap);
        this.editOutput = root.findViewById(R.id.edit_output);
        this.btnEdit = root.findViewById(R.id.btn_edit);
        this.btnReset = root.findViewById(R.id.btn_reset);
        this.btnWrap = root.findViewById(R.id.btn_wrap);

        MaterialButton btnCopy = root.findViewById(R.id.btn_copy);
        MaterialButton btnExport = root.findViewById(R.id.btn_export);

        Context ctx = root.getContext();
        highlighter = new JsSyntaxHighlighter(
                ContextCompat.getColor(ctx, R.color.code_keyword),
                ContextCompat.getColor(ctx, R.color.code_string),
                ContextCompat.getColor(ctx, R.color.code_comment),
                ContextCompat.getColor(ctx, R.color.code_number),
                ContextCompat.getColor(ctx, R.color.code_frida_api));

        btnCopy.setOnClickListener(v -> copyToClipboard());
        btnExport.setOnClickListener(v -> exportToFile());
        btnEdit.setOnClickListener(v -> toggleEdit());
        btnReset.setOnClickListener(v -> reset());
        btnWrap.setOnClickListener(v -> toggleWrap());

        editOutput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppressWatcher || !editing) return;
                handler.removeCallbacks(highlightRunnable);
                handler.postDelayed(highlightRunnable, debounceMs(s != null ? s.length() : 0));
            }
        });
    }

    public void show(String script) {
        this.originalScript = script;
        this.editing = false;

        CharSequence highlighted = highlighter.highlight(script);
        textOutput.setText(highlighted);
        textOutputWrap.setText(highlighted);
        setEditTextSuppressed(script);

        applyReadOnlyVisibility();
        btnEdit.setText(R.string.btn_edit);
        btnReset.setVisibility(View.GONE);
        outputSection.setVisibility(View.VISIBLE);
    }

    public void hide() {
        this.editing = false;
        handler.removeCallbacks(highlightRunnable);
        outputSection.setVisibility(View.GONE);
    }

    private void toggleWrap() {
        wrapping = !wrapping;
        btnWrap.setText(wrapping ? R.string.btn_nowrap : R.string.btn_wrap);
        if (editing) {
            applyEditWrapState();
        } else {
            applyReadOnlyVisibility();
        }
    }

    private void applyEditWrapState() {
        if (wrapping) {
            editOutput.setHorizontallyScrolling(false);
        } else {
            editOutput.setHorizontallyScrolling(true);
            editOutput.setScroller(new Scroller(root.getContext()));
        }
    }

    /** Dynamic debounce: near-instant for small scripts, safer delay for large ones. */
    private static long debounceMs(int textLength) {
        if (textLength < SMALL_TEXT) return 30;
        if (textLength < MEDIUM_TEXT) return 80;
        if (textLength < LARGE_TEXT) return 150;
        return 300;
    }

    private void applyReadOnlyVisibility() {
        editOutput.setVisibility(View.GONE);
        if (wrapping) {
            scrollOutput.setVisibility(View.GONE);
            textOutputWrap.setVisibility(View.VISIBLE);
        } else {
            textOutputWrap.setVisibility(View.GONE);
            scrollOutput.setVisibility(View.VISIBLE);
        }
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
        setEditTextHighlighted(getPlainText());
        scrollOutput.setVisibility(View.GONE);
        textOutputWrap.setVisibility(View.GONE);
        applyEditWrapState();
        editOutput.setVisibility(View.VISIBLE);
        editOutput.requestFocus();
        btnEdit.setText(R.string.btn_done);
        btnReset.setVisibility(View.VISIBLE);
    }

    private void exitEditMode() {
        editing = false;
        handler.removeCallbacks(highlightRunnable);
        CharSequence edited = editOutput.getText();
        String plain = edited != null ? edited.toString() : "";
        CharSequence highlighted = highlighter.highlight(plain);
        textOutput.setText(highlighted);
        textOutputWrap.setText(highlighted);
        applyReadOnlyVisibility();
        btnEdit.setText(R.string.btn_edit);
    }

    private void reset() {
        editing = false;
        handler.removeCallbacks(highlightRunnable);
        CharSequence highlighted = highlighter.highlight(originalScript);
        textOutput.setText(highlighted);
        textOutputWrap.setText(highlighted);
        setEditTextSuppressed(originalScript);
        applyReadOnlyVisibility();
        btnEdit.setText(R.string.btn_edit);
        btnReset.setVisibility(View.GONE);
    }

    /**
     * Re-highlights the EditText content in-place without calling setText().
     * This avoids the full layout pass that setText() triggers, eliminating lag.
     */
    private void rehighlightEdit() {
        if (!editing) return;
        Editable editable = editOutput.getText();
        if (editable == null || editable.length() == 0) return;

        suppressWatcher = true;
        highlighter.applyInPlace(editable);
        suppressWatcher = false;
    }

    /** Sets highlighted text on editOutput without triggering the TextWatcher. */
    private void setEditTextHighlighted(String plain) {
        suppressWatcher = true;
        editOutput.setText(highlighter.highlight(plain));
        suppressWatcher = false;
    }

    /** Sets plain text on editOutput without triggering the TextWatcher. */
    private void setEditTextSuppressed(String text) {
        suppressWatcher = true;
        editOutput.setText(text);
        suppressWatcher = false;
    }

    private String getPlainText() {
        if (editing) {
            CharSequence t = editOutput.getText();
            return t != null ? t.toString() : "";
        }
        CharSequence t = wrapping ? textOutputWrap.getText() : textOutput.getText();
        return t != null ? t.toString() : "";
    }

    private void copyToClipboard() {
        String text = getPlainText();
        if (text.isEmpty()) return;

        Context ctx = root.getContext();
        ClipboardManager clipboard = (ClipboardManager)
                ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("FridaHelper Script", text));
            Snackbar.make(root, R.string.msg_copied, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void exportToFile() {
        String text = getPlainText();
        if (text.isEmpty()) return;

        ScriptExporter.ExportResult result =
                ScriptExporter.export(root.getContext(), text);

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
