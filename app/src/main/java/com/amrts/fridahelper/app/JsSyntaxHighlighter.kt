package com.amrts.fridahelper.app

import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import java.util.regex.Pattern

/**
 * Lightweight regex-based JavaScript syntax highlighter for Frida scripts.
 *
 * Token priority (highest first): comments -> strings -> Frida API -> keywords -> numbers.
 * Earlier matches claim their range; later rules skip overlapping regions.
 *
 * For live editing, use [applyInPlace] to update spans without replacing text
 * (avoids the full layout pass that setText() triggers).
 */
class JsSyntaxHighlighter(
    private val colorKeyword: Int,
    private val colorString: Int,
    private val colorComment: Int,
    private val colorNumber: Int,
    private val colorFridaApi: Int
) {

    /** Creates a new highlighted SpannableStringBuilder (for read-only TextViews). */
    fun highlight(code: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder(code)
        applySpans(sb, code.length)
        return sb
    }

    /** Updates spans in-place on an existing Editable (for live EditText highlighting). */
    fun applyInPlace(editable: Editable) {
        for (span in editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)) {
            editable.removeSpan(span)
        }
        applySpans(editable, editable.length)
    }

    private fun applySpans(target: Spannable, length: Int) {
        val claimed = BooleanArray(length)
        applyPattern(target, claimed, COMMENTS, colorComment)
        applyPattern(target, claimed, STRINGS, colorString)
        applyPattern(target, claimed, FRIDA_API, colorFridaApi)
        applyPattern(target, claimed, KEYWORDS, colorKeyword)
        applyPattern(target, claimed, NUMBERS, colorNumber)
    }

    companion object {
        private val COMMENTS = Pattern.compile("//[^\n]*")
        private val STRINGS = Pattern.compile(
            "\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'|`(?:[^`\\\\]|\\\\.)*`"
        )
        private val FRIDA_API = Pattern.compile(
            "\\b(?:Java\\.(?:use|perform|choose|cast|available|enumerateLoadedClasses)"
                + "|Interceptor\\.(?:attach|detach|replace)"
                + "|Module\\.(?:findExportByName|findBaseAddress|enumerateExports)"
                + "|console\\.(?:log|warn|error)"
                + "|send|recv|ptr|NULL|Memory\\.(?:read|write|alloc)\\w*)\\b"
        )
        private val KEYWORDS = Pattern.compile(
            "\\b(?:var|let|const|function|return|this|new|if|else|for|while|do"
                + "|try|catch|finally|throw|typeof|instanceof|in|of|class|extends"
                + "|true|false|null|undefined|void|break|continue|switch|case|default)\\b"
        )
        private val NUMBERS = Pattern.compile(
            "\\b(?:0x[0-9a-fA-F]+|\\d+(?:\\.\\d+)?)\\b"
        )

        private fun applyPattern(target: Spannable, claimed: BooleanArray, pattern: Pattern, color: Int) {
            val m = pattern.matcher(target)
            while (m.find()) {
                val start = m.start()
                val end = m.end()
                if ((start until end).any { claimed[it] }) continue
                target.setSpan(
                    ForegroundColorSpan(color), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                for (i in start until end) claimed[i] = true
            }
        }
    }
}
