package com.amrts.fridahelper.core.util;

/**
 * Generates sequential lowercase parameter variable names for Frida hook scripts.
 * Pure function — no mutable state between calls.
 *
 * For count=3 -> "a, b, c"
 * For count=0 -> ""
 * For count > 26 -> wraps: "a, b, ..., z, a0, b0, ..."
 */
public final class ParamNameGenerator {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";

    private ParamNameGenerator() { }

    /**
     * Generates a comma-separated string of parameter variable names.
     * @param count number of parameters (>= 0)
     * @return e.g. "a, b, c" for count=3; "" for count=0
     */
    public static String generate(int count) {
        if (count <= 0) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(", ");
            sb.append(nameAt(i));
        }
        return sb.toString();
    }

    /**
     * Returns the individual variable name at a given index.
     * 0->'a', 25->'z', 26->'a0', 27->'b0', etc.
     */
    public static String nameAt(int index) {
        if (index < 26) {
            return String.valueOf(ALPHABET.charAt(index));
        }
        int suffix = (index / 26) - 1;
        int charIdx = index % 26;
        return String.valueOf(ALPHABET.charAt(charIdx)) + suffix;
    }
}
