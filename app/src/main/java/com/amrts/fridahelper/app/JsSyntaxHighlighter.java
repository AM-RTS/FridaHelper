package com.amrts.fridahelper.app;

import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight regex-based JavaScript syntax highlighter for Frida scripts.
 * Produces a {@link SpannableStringBuilder} with {@link ForegroundColorSpan}s.
 *
 * Token priority (highest first): comments → strings → Frida API → keywords → numbers.
 * Earlier matches claim their range — later rules skip overlapping regions.
 *
 * For live editing, use {@link #applyInPlace(Editable)} to update spans without
 * replacing text (avoids the full layout pass that {@code setText()} triggers).
 */
public final class JsSyntaxHighlighter {

    private static final Pattern COMMENTS = Pattern.compile("//[^\n]*");
    private static final Pattern STRINGS = Pattern.compile(
            "\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'|`(?:[^`\\\\]|\\\\.)*`");
    private static final Pattern FRIDA_API = Pattern.compile(
            "\\b(?:Java\\.(?:use|perform|choose|cast|available|enumerateLoadedClasses)"
            + "|Interceptor\\.(?:attach|detach|replace)"
            + "|Module\\.(?:findExportByName|findBaseAddress|enumerateExports)"
            + "|console\\.(?:log|warn|error)"
            + "|send|recv|ptr|NULL|Memory\\.(?:read|write|alloc)\\w*)\\b");
    private static final Pattern KEYWORDS = Pattern.compile(
            "\\b(?:var|let|const|function|return|this|new|if|else|for|while|do"
            + "|try|catch|finally|throw|typeof|instanceof|in|of|class|extends"
            + "|true|false|null|undefined|void|break|continue|switch|case|default)\\b");
    private static final Pattern NUMBERS = Pattern.compile(
            "\\b(?:0x[0-9a-fA-F]+|\\d+(?:\\.\\d+)?)\\b");

    private final int colorComment;
    private final int colorString;
    private final int colorFridaApi;
    private final int colorKeyword;
    private final int colorNumber;

    public JsSyntaxHighlighter(int colorKeyword, int colorString, int colorComment,
                               int colorNumber, int colorFridaApi) {
        this.colorKeyword = colorKeyword;
        this.colorString = colorString;
        this.colorComment = colorComment;
        this.colorNumber = colorNumber;
        this.colorFridaApi = colorFridaApi;
    }

    /** Creates a new highlighted SpannableStringBuilder (for read-only TextViews). */
    public SpannableStringBuilder highlight(String code) {
        SpannableStringBuilder sb = new SpannableStringBuilder(code);
        applySpans(sb, code.length());
        return sb;
    }

    /**
     * Updates spans in-place on an existing Editable (for live EditText highlighting).
     * Avoids setText() which triggers a costly full layout pass.
     */
    public void applyInPlace(Editable editable) {
        ForegroundColorSpan[] existing = editable.getSpans(
                0, editable.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : existing) {
            editable.removeSpan(span);
        }
        applySpans(editable, editable.length());
    }

    private void applySpans(Spannable target, int length) {
        boolean[] claimed = new boolean[length];
        applyPattern(target, claimed, COMMENTS, colorComment);
        applyPattern(target, claimed, STRINGS, colorString);
        applyPattern(target, claimed, FRIDA_API, colorFridaApi);
        applyPattern(target, claimed, KEYWORDS, colorKeyword);
        applyPattern(target, claimed, NUMBERS, colorNumber);
    }

    private static void applyPattern(Spannable target, boolean[] claimed,
                                     Pattern pattern, int color) {
        Matcher m = pattern.matcher(target);
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            if (isAnyClaimed(claimed, start, end)) continue;
            target.setSpan(new ForegroundColorSpan(color), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            claimRange(claimed, start, end);
        }
    }

    private static boolean isAnyClaimed(boolean[] claimed, int start, int end) {
        for (int i = start; i < end; i++) {
            if (claimed[i]) return true;
        }
        return false;
    }

    private static void claimRange(boolean[] claimed, int start, int end) {
        for (int i = start; i < end; i++) {
            claimed[i] = true;
        }
    }
}
