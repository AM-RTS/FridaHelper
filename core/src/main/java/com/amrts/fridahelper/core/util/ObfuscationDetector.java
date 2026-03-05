package com.amrts.fridahelper.core.util;

/**
 * Detects whether a method or class name contains obfuscated/non-ASCII characters
 * that require special quoting in Frida scripts.
 *
 * Heuristic: any code point >= 320 is treated as obfuscated. This threshold
 * catches common obfuscators (Chinese/Cyrillic/emoji renaming) while allowing
 * standard ASCII and common Latin-Extended characters.
 */
public final class ObfuscationDetector {

    private static final int OBFUSCATION_THRESHOLD = 320;
    private static final int MIN_MEANINGFUL_LENGTH = 3;

    private ObfuscationDetector() { }

    /**
     * Returns true if the string contains any code point at or above the obfuscation threshold.
     */
    public static boolean isObfuscated(String name) {
        if (name == null || name.isEmpty()) return false;

        for (int i = 0; i < name.length(); ) {
            int codePoint = name.codePointAt(i);
            if (codePoint >= OBFUSCATION_THRESHOLD) {
                return true;
            }
            i += Character.charCount(codePoint);
        }
        return false;
    }

    /**
     * Returns true if the class name is unsuitable for use as a variable name.
     * A name is unsuitable if it is obfuscated (non-ASCII) or too short (< 3 chars),
     * which typically indicates ProGuard/R8 minification (e.g. "a", "b0").
     */
    public static boolean isUnsuitableForVariable(String simpleName) {
        if (simpleName == null || simpleName.isEmpty()) return true;
        if (simpleName.length() < MIN_MEANINGFUL_LENGTH) return true;
        return isObfuscated(simpleName);
    }
}
