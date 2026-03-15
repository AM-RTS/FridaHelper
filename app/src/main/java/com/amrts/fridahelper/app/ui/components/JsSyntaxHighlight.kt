package com.amrts.fridahelper.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.amrts.fridahelper.app.theme.CodeComment
import com.amrts.fridahelper.app.theme.CodeFridaApi
import com.amrts.fridahelper.app.theme.CodeKeyword
import com.amrts.fridahelper.app.theme.CodeNumber
import com.amrts.fridahelper.app.theme.CodeString
import java.util.regex.Pattern

/**
 * Builds an [AnnotatedString] with JavaScript syntax highlighting for Frida scripts.
 * Token priority (highest first): comments → strings → Frida API → keywords → numbers.
 */
fun highlightJs(code: String): AnnotatedString {
    return buildAnnotatedString {
        append(code)
        val claimed = BooleanArray(code.length)
        applyPattern(this, claimed, COMMENTS, CodeComment)
        applyPattern(this, claimed, STRINGS, CodeString)
        applyPattern(this, claimed, FRIDA_API, CodeFridaApi)
        applyPattern(this, claimed, KEYWORDS, CodeKeyword)
        applyPattern(this, claimed, NUMBERS, CodeNumber)
    }
}

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

private fun applyPattern(
    builder: AnnotatedString.Builder,
    claimed: BooleanArray,
    pattern: Pattern,
    color: Color
) {
    val m = pattern.matcher(builder.toAnnotatedString().text)
    while (m.find()) {
        val start = m.start()
        val end = m.end()
        if ((start until end).any { claimed[it] }) continue
        builder.addStyle(SpanStyle(color = color), start, end)
        for (i in start until end) claimed[i] = true
    }
}
