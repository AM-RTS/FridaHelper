package com.amrts.fridahelper.core.util;

/**
 * Shared indentation utility for generated Frida scripts.
 */
public final class ScriptIndent {

    public static final String INDENT = "    ";

    private ScriptIndent() { }

    /**
     * Indents every non-empty line of a multi-line string by the given prefix.
     */
    public static String indentBlock(String block, String indent) {
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
