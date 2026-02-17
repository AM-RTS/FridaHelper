package com.amrts.fridahelper.core.generator;

import com.amrts.fridahelper.core.model.GeneratedScript;
import com.amrts.fridahelper.core.model.HookRequest;

/**
 * Wraps generated hook scripts in runtime envelopes (Java.perform, setImmediate, etc.).
 *
 * This is intentionally separate from ScriptGenerator because:
 * - Wrapping is a caller/presentation concern, not a generation concern.
 * - The same hook snippet might be used standalone (snippet mode) or wrapped (script mode).
 * - Native hooks don't need Java.perform but might need setImmediate in some contexts.
 */
public final class ScriptWrapper {

    private ScriptWrapper() { }

    /**
     * Wraps a generated script in Java.perform() if it's a Java hook.
     * Returns the script unchanged for native hooks (they run at top level).
     */
    public static GeneratedScript wrapIfNeeded(GeneratedScript script) {
        if (script.getHookType() == HookRequest.Type.JAVA) {
            String wrapped = "Java.perform(function(){\n  "
                    + script.getScriptText()
                    + "\n});";
            return new GeneratedScript(wrapped, script.getHookType());
        }
        return script;
    }

    /**
     * Always wraps in Java.perform(), regardless of hook type.
     * Useful when the caller explicitly wants a full script.
     */
    public static GeneratedScript wrapInJavaPerform(GeneratedScript script) {
        String wrapped = "Java.perform(function(){\n  "
                + script.getScriptText()
                + "\n});";
        return new GeneratedScript(wrapped, script.getHookType());
    }
}
