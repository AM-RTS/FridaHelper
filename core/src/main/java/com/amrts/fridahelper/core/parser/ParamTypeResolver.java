package com.amrts.fridahelper.core.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves JVM type descriptors (as used in smali) into human-readable Java type names.
 * Pure function — no I/O, no mutable state.
 *
 * Examples:
 *   "I"                    -> "int"
 *   "Ljava/lang/String;"   -> "java.lang.String"
 *   "[I"                   -> "[I"  (array notation preserved for Frida overload matching)
 *   "ILjava/lang/String;Z" -> ["int", "java.lang.String", "boolean"]
 */
public final class ParamTypeResolver {

    private static final Map<String, String> PRIMITIVE_MAP;

    static {
        Map<String, String> m = new HashMap<>();
        m.put("Z", "boolean");
        m.put("B", "byte");
        m.put("C", "char");
        m.put("S", "short");
        m.put("I", "int");
        m.put("F", "float");
        m.put("J", "long");
        m.put("D", "double");
        m.put("V", "void");
        PRIMITIVE_MAP = Collections.unmodifiableMap(m);
    }

    /**
     * Regex that matches one JVM type descriptor at a time.
     * Handles: array prefixes, object types (L...;), and single-char primitives.
     */
    private static final Pattern TYPE_PATTERN =
            Pattern.compile("(\\[*L[^;]+;|\\[*[ZBCSIJFDV])");

    /**
     * Resolves a concatenated param descriptor string into a list of Java type names.
     * E.g. "ILjava/lang/String;Z" -> ["int", "java.lang.String", "boolean"]
     */
    public List<String> resolveAll(String paramDescriptor) {
        if (paramDescriptor == null || paramDescriptor.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        Matcher matcher = TYPE_PATTERN.matcher(paramDescriptor);
        while (matcher.find()) {
            result.add(resolveSingle(matcher.group(0)));
        }
        return result;
    }

    /**
     * Resolves a single JVM type descriptor into a Java type name.
     * Handles arrays by preserving the descriptor (Frida uses JVM notation for arrays).
     */
    public String resolveSingle(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            return "";
        }

        if (descriptor.startsWith("[")) {
            return resolveArrayType(descriptor);
        }

        if (descriptor.length() == 1) {
            String resolved = PRIMITIVE_MAP.get(descriptor);
            return resolved != null ? resolved : descriptor;
        }

        if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
            return descriptor.substring(1, descriptor.length() - 1).replace("/", ".");
        }

        return descriptor;
    }

    private String resolveArrayType(String descriptor) {
        int arrayDepth = 0;
        int i = 0;
        while (i < descriptor.length() && descriptor.charAt(i) == '[') {
            arrayDepth++;
            i++;
        }
        String baseDescriptor = descriptor.substring(i);
        String baseType = resolveSingle(baseDescriptor);

        StringBuilder sb = new StringBuilder(baseType);
        for (int d = 0; d < arrayDepth; d++) {
            sb.append("[]");
        }
        return sb.toString();
    }
}
