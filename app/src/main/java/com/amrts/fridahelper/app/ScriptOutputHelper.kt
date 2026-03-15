package com.amrts.fridahelper.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.Scroller
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

/**
 * Shared helper for output display, edit toggle, copy-to-clipboard, and file export.
 *
 * Supports three display modes:
 * - Read-only no-wrap (default): highlighted script in a HorizontalScrollView.
 * - Read-only wrapped: highlighted script in a wrapping TextView.
 * - Edit mode: highlighted text in an editable EditText, re-highlighted live with debounce.
 */
class ScriptOutputHelper(private val root: View) {

    private val outputSection: View = root.findViewById(R.id.layout_output_section)
    private val scrollOutput: HorizontalScrollView = root.findViewById(R.id.scroll_output)
    private val textOutput: TextView = root.findViewById(R.id.text_output)
    private val textOutputWrap: TextView = root.findViewById(R.id.text_output_wrap)
    private val editOutput: EditText = root.findViewById(R.id.edit_output)
    private val btnEdit: MaterialButton = root.findViewById(R.id.btn_edit)
    private val btnReset: MaterialButton = root.findViewById(R.id.btn_reset)
    private val btnWrap: MaterialButton = root.findViewById(R.id.btn_wrap)

    private val highlighter: JsSyntaxHighlighter
    private val handler = Handler(Looper.getMainLooper())
    private val highlightRunnable = Runnable { rehighlightEdit() }

    private var originalScript: String = ""
    private var editing = false
    private var wrapping = false
    private var suppressWatcher = false

    init {
        val ctx = root.context
        highlighter = JsSyntaxHighlighter(
            colorKeyword = ContextCompat.getColor(ctx, R.color.code_keyword),
            colorString = ContextCompat.getColor(ctx, R.color.code_string),
            colorComment = ContextCompat.getColor(ctx, R.color.code_comment),
            colorNumber = ContextCompat.getColor(ctx, R.color.code_number),
            colorFridaApi = ContextCompat.getColor(ctx, R.color.code_frida_api)
        )

        root.findViewById<MaterialButton>(R.id.btn_copy).setOnClickListener { copyToClipboard() }
        root.findViewById<MaterialButton>(R.id.btn_export).setOnClickListener { exportToFile() }
        btnEdit.setOnClickListener { toggleEdit() }
        btnReset.setOnClickListener { reset() }
        btnWrap.setOnClickListener { toggleWrap() }

        editOutput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (suppressWatcher || !editing) return
                handler.removeCallbacks(highlightRunnable)
                handler.postDelayed(highlightRunnable, debounceMs(s?.length ?: 0))
            }
        })
    }

    fun show(script: String) {
        originalScript = script
        editing = false

        val highlighted = highlighter.highlight(script)
        textOutput.text = highlighted
        textOutputWrap.text = highlighted
        setEditTextSuppressed(script)

        applyReadOnlyVisibility()
        btnEdit.setText(R.string.btn_edit)
        btnReset.visibility = View.GONE
        outputSection.visibility = View.VISIBLE
    }

    fun hide() {
        editing = false
        handler.removeCallbacks(highlightRunnable)
        outputSection.visibility = View.GONE
    }

    private fun toggleWrap() {
        wrapping = !wrapping
        btnWrap.setText(if (wrapping) R.string.btn_nowrap else R.string.btn_wrap)
        if (editing) applyEditWrapState() else applyReadOnlyVisibility()
    }

    private fun applyEditWrapState() {
        if (wrapping) {
            editOutput.setHorizontallyScrolling(false)
        } else {
            editOutput.setHorizontallyScrolling(true)
            editOutput.setScroller(Scroller(root.context))
        }
    }

    private fun applyReadOnlyVisibility() {
        editOutput.visibility = View.GONE
        if (wrapping) {
            scrollOutput.visibility = View.GONE
            textOutputWrap.visibility = View.VISIBLE
        } else {
            textOutputWrap.visibility = View.GONE
            scrollOutput.visibility = View.VISIBLE
        }
    }

    private fun toggleEdit() {
        if (editing) exitEditMode() else enterEditMode()
    }

    private fun enterEditMode() {
        editing = true
        setEditTextHighlighted(getPlainText())
        scrollOutput.visibility = View.GONE
        textOutputWrap.visibility = View.GONE
        applyEditWrapState()
        editOutput.visibility = View.VISIBLE
        editOutput.requestFocus()
        btnEdit.setText(R.string.btn_done)
        btnReset.visibility = View.VISIBLE
    }

    private fun exitEditMode() {
        editing = false
        handler.removeCallbacks(highlightRunnable)
        val plain = editOutput.text?.toString().orEmpty()
        val highlighted = highlighter.highlight(plain)
        textOutput.text = highlighted
        textOutputWrap.text = highlighted
        applyReadOnlyVisibility()
        btnEdit.setText(R.string.btn_edit)
    }

    private fun reset() {
        editing = false
        handler.removeCallbacks(highlightRunnable)
        val highlighted = highlighter.highlight(originalScript)
        textOutput.text = highlighted
        textOutputWrap.text = highlighted
        setEditTextSuppressed(originalScript)
        applyReadOnlyVisibility()
        btnEdit.setText(R.string.btn_edit)
        btnReset.visibility = View.GONE
    }

    private fun rehighlightEdit() {
        if (!editing) return
        val editable = editOutput.text ?: return
        if (editable.isEmpty()) return
        suppressWatcher = true
        highlighter.applyInPlace(editable)
        suppressWatcher = false
    }

    private fun setEditTextHighlighted(plain: String) {
        suppressWatcher = true
        editOutput.setText(highlighter.highlight(plain))
        suppressWatcher = false
    }

    private fun setEditTextSuppressed(text: String) {
        suppressWatcher = true
        editOutput.setText(text)
        suppressWatcher = false
    }

    private fun getPlainText(): String {
        if (editing) return editOutput.text?.toString().orEmpty()
        val t = if (wrapping) textOutputWrap.text else textOutput.text
        return t?.toString().orEmpty()
    }

    private fun copyToClipboard() {
        val text = getPlainText()
        if (text.isEmpty()) return
        val ctx = root.context
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("FridaHelper Script", text))
        Snackbar.make(root, R.string.msg_copied, Snackbar.LENGTH_SHORT).show()
    }

    private fun exportToFile() {
        val text = getPlainText()
        if (text.isEmpty()) return
        val result = ScriptExporter.export(root.context, text)
        val msg = if (result.isSuccess) {
            root.context.getString(R.string.msg_exported, result.filename)
        } else {
            root.context.getString(R.string.msg_export_failed, result.error)
        }
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show()
    }

    companion object {
        private const val SMALL_TEXT = 2000
        private const val MEDIUM_TEXT = 5000
        private const val LARGE_TEXT = 10000

        private fun debounceMs(textLength: Int): Long = when {
            textLength < SMALL_TEXT -> 30
            textLength < MEDIUM_TEXT -> 80
            textLength < LARGE_TEXT -> 150
            else -> 300
        }
    }
}
