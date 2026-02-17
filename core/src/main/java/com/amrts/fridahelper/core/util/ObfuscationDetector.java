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
}
