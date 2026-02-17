package com.amrts.fridahelper.core.model;

/**
 * Immutable result of script generation.
 * Contains the generated JavaScript source and metadata about the hook type.
 */
public final class GeneratedScript {

    private final String scriptText;
    private final HookRequest.Type hookType;

    public GeneratedScript(String scriptText, HookRequest.Type hookType) {
        if (scriptText == null) throw new IllegalArgumentException("scriptText must not be null");
        if (hookType == null) throw new IllegalArgumentException("hookType must not be null");
        this.scriptText = scriptText;
        this.hookType = hookType;
    }

    public String getScriptText() { return scriptText; }
    public HookRequest.Type getHookType() { return hookType; }

    @Override
    public String toString() {
        return "GeneratedScript{type=" + hookType + ", length=" + scriptText.length() + "}";
    }
}
