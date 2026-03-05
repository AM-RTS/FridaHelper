package com.amrts.fridahelper.core.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates parameter variable names for Frida hook scripts.
 *
 * Two modes:
 * 1. Simple (no types): sequential a, b, c ... z, a0, b0 ...
 * 2. Type-aware: derives short prefix from Java type name, numbering only when
 *    the same type appears multiple times. Falls back to simple mode for
 *    unrecognised types.
 */
public final class ParamNameGenerator {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";

    private static final Map<String, String> TYPE_PREFIX;

    static {
        Map<String, String> m = new HashMap<>();
        m.put("int", "i");
        m.put("long", "l");
        m.put("float", "f");
        m.put("double", "d");
        m.put("boolean", "b");
        m.put("byte", "by");
        m.put("char", "ch");
        m.put("short", "s");
        m.put("java.lang.String", "str");
        m.put("java.lang.Object", "obj");
        TYPE_PREFIX = m;
    }

    private ParamNameGenerator() { }

    /**
     * Simple sequential names: "a, b, c" for count=3.
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
     * Type-aware names derived from the resolved Java types.
     * Numbers are appended only when the same type appears more than once.
     * Falls back to simple a,b,c for types not in the prefix map.
     *
     * @param paramTypes resolved param types (e.g. "int", "java.lang.String")
     * @return comma-separated names (e.g. "i1, i2, str")
     */
    public static String generate(List<String> paramTypes) {
        if (paramTypes == null || paramTypes.isEmpty()) return "";

        int n = paramTypes.size();
        String[] names = new String[n];
        Map<String, Integer> typeCounts = new HashMap<>();

        for (String type : paramTypes) {
            String key = normalizeType(type);
            typeCounts.merge(key, 1, Integer::sum);
        }

        Map<String, Integer> typeCounters = new HashMap<>();
        int fallbackIdx = 0;

        for (int i = 0; i < n; i++) {
            String type = paramTypes.get(i);
            String key = normalizeType(type);
            String prefix = resolvePrefix(key);

            if (prefix == null) {
                names[i] = nameAt(fallbackIdx++);
            } else {
                int total = typeCounts.getOrDefault(key, 1);
                if (total == 1) {
                    names[i] = prefix;
                } else {
                    int seq = typeCounters.merge(key, 1, Integer::sum);
                    names[i] = prefix + seq;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(", ");
            sb.append(names[i]);
        }
        return sb.toString();
    }

    /**
     * Individual name at index (simple mode). 0→'a', 25→'z', 26→'a0'.
     */
    public static String nameAt(int index) {
        if (index < 26) {
            return String.valueOf(ALPHABET.charAt(index));
        }
        int suffix = (index / 26) - 1;
        int charIdx = index % 26;
        return String.valueOf(ALPHABET.charAt(charIdx)) + suffix;
    }

    private static String resolvePrefix(String normalizedType) {
        String prefix = TYPE_PREFIX.get(normalizedType);
        if (prefix != null) return prefix;

        if (normalizedType.endsWith("[]")) {
            String base = normalizedType.substring(0, normalizedType.indexOf('['));
            String basePrefix = TYPE_PREFIX.get(base);
            if (basePrefix != null) return basePrefix + "Arr";
            String simple = simpleClassName(base);
            return simple != null ? simple + "Arr" : null;
        }

        return simpleClassName(normalizedType);
    }

    /**
     * Extracts simple class name and lowercases first char for use as variable.
     * Returns null if the result would be too short (< 3 chars) to be meaningful.
     */
    private static String simpleClassName(String fullyQualified) {
        if (fullyQualified == null || fullyQualified.isEmpty()) return null;
        int dot = fullyQualified.lastIndexOf('.');
        String simple = dot >= 0 ? fullyQualified.substring(dot + 1) : fullyQualified;
        if (simple.length() < 3) return null;
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

    /**
     * Strips array brackets for type counting so int[] and int[][] group together.
     */
    private static String normalizeType(String type) {
        return type;
    }
}
