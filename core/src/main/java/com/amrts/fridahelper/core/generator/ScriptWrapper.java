package com.amrts.fridahelper.core.generator;

import com.amrts.fridahelper.core.model.GeneratedScript;
import com.amrts.fridahelper.core.model.HookRequest;

/**
 * Wraps generated hook scripts in runtime envelopes (Java.perform, setTimeout, etc.).
 *
 * This is intentionally separate from ScriptGenerator because:
 * - Wrapping is a caller/presentation concern, not a generation concern.
 * - The same hook snippet might be used standalone (snippet mode) or wrapped (script mode).
 * - Native hooks don't need Java.perform but might need setTimeout in some contexts.
 */
public final class ScriptWrapper {

    private static final String INDENT = "    ";

    private ScriptWrapper() { }

    /**
     * Wraps a generated script in Java.perform() if it's a Java hook.
     * Returns the script unchanged for native hooks (they run at top level).
     */
    public static GeneratedScript wrapIfNeeded(GeneratedScript script) {
        if (script.getHookType() == HookRequest.Type.JAVA) {
            return wrapInJavaPerform(script);
        }
        return script;
    }

    /**
     * Always wraps in Java.perform(), regardless of hook type.
     * Useful when the caller explicitly wants a full script.
     * Indents every line of the inner script by 4 spaces.
     */
    public static GeneratedScript wrapInJavaPerform(GeneratedScript script) {
        String indented = indentBlock(script.getScriptText(), INDENT);
        String wrapped = "Java.perform(function(){\n"
                + indented + "\n"
                + "});";
        return new GeneratedScript(wrapped, script.getHookType());
    }

    /**
     * Wraps a generated script in setTimeout(function(){ ... }, delayMs).
     * Useful for timing-sensitive hooks that need a delay after injection.
     *
     * @param script the script to wrap
     * @param delayMs delay in milliseconds (must be > 0)
     * @return wrapped script
     */
    public static GeneratedScript wrapInSetTimeout(GeneratedScript script, int delayMs) {
        if (delayMs <= 0) return script;

        String indented = indentBlock(script.getScriptText(), INDENT);
        String wrapped = "setTimeout(function() {\n"
                + indented + "\n"
                + "}, " + delayMs + ");";
        return new GeneratedScript(wrapped, script.getHookType());
    }

    /**
     * Indents every non-empty line of a multi-line string by the given prefix.
     */
    private static String indentBlock(String block, String indent) {
        String[] lines = block.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append("\n");
            if (!lines[i].isEmpty()) {
                sb.append(indent).append(lines[i]);
            }
        }
        return sb.toString();
    }
}
