package com.amrts.fridahelper.core.generator;

import com.amrts.fridahelper.core.model.GeneratedScript;
import com.amrts.fridahelper.core.model.HookRequest;
import com.amrts.fridahelper.core.model.SmaliMethod;
import com.amrts.fridahelper.core.util.ObfuscationDetector;
import com.amrts.fridahelper.core.util.ParamNameGenerator;

import java.util.List;

/**
 * Generates Frida Java hook scripts from smali method signatures.
 *
 * Output format (properly indented):
 *   var networkManager = Java.use("com.example.NetworkManager");
 *   networkManager.sendRequest.overload("java.lang.String", "int").implementation = function(str, i){
 *       var retval = this.sendRequest(str, i);
 *       console.log(`NetworkManager.sendRequest(${str}, ${i}) => ${retval}`);
 *       return retval;
 *   }
 */
public final class JavaHookGenerator implements ScriptGenerator {

    private static final String INDENT = "    ";

    /** log() function definition for Java stack trace logging. */
    public static final String LOG_FUNCTION =
            "function log(){\n"
          + "    console.log(Java.use(\"android.util.Log\").getStackTraceString(Java.use(\"java.lang.Exception\").$new()));\n"
          + "}";

    @Override
    public GeneratedScript generate(HookRequest request) {
        return generateBody(request, false);
    }

    public GeneratedScript generate(HookRequest request, boolean enableStackTrace) {
        GeneratedScript body = generateBody(request, enableStackTrace);
        if (enableStackTrace) {
            return new GeneratedScript(LOG_FUNCTION + "\n\n" + body.getScriptText(), body.getHookType());
        }
        return body;
    }

    @Override
    public GeneratedScript generateBody(HookRequest request) {
        return generateBody(request, false);
    }

    public GeneratedScript generateBody(HookRequest request, boolean enableStackTrace) {
        if (request.getType() != HookRequest.Type.JAVA) {
            throw new IllegalArgumentException("JavaHookGenerator requires a JAVA HookRequest, got: " + request.getType());
        }

        SmaliMethod method = request.getSmaliMethod();
        StringBuilder sb = new StringBuilder();

        String className = method.getClassName();
        String methodName = method.getMethodName();
        List<String> paramTypes = method.getParamTypes();

        String varName = deriveClassVariable(className);
        String paramNames = ParamNameGenerator.generate(paramTypes);
        String methodAccess = buildMethodAccess(methodName);
        String overloadArgs = buildOverloadArgs(paramTypes);

        sb.append("var ").append(varName).append(" = Java.use(\"").append(className).append("\");\n");
        sb.append(varName).append(methodAccess).append(".overload(").append(overloadArgs)
          .append(").implementation = function(").append(paramNames).append("){\n");

        boolean isVoid = "void".equals(method.getReturnType());
        if (isVoid) {
            sb.append(INDENT).append("this").append(methodAccess).append("(").append(paramNames).append(");\n");
        } else {
            sb.append(INDENT).append("var retval = this").append(methodAccess).append("(").append(paramNames).append(");\n");
        }
        sb.append(buildTraceLog(className, varName, methodName, paramTypes, isVoid));
        appendStackTraceLine(sb, enableStackTrace);
        if (!isVoid) {
            sb.append(INDENT).append("return retval;\n");
        }
        sb.append("}");

        return new GeneratedScript(sb.toString(), HookRequest.Type.JAVA);
    }

    public String generateMethodHook(HookRequest request, String varName) {
        return generateMethodHook(request, varName, false);
    }

    public String generateMethodHook(HookRequest request, String varName, boolean enableStackTrace) {
        if (request.getType() != HookRequest.Type.JAVA) {
            throw new IllegalArgumentException("JavaHookGenerator requires a JAVA HookRequest");
        }

        SmaliMethod method = request.getSmaliMethod();
        StringBuilder sb = new StringBuilder();

        String className = method.getClassName();
        String methodName = method.getMethodName();
        List<String> paramTypes = method.getParamTypes();

        String paramNames = ParamNameGenerator.generate(paramTypes);
        String methodAccess = buildMethodAccess(methodName);
        String overloadArgs = buildOverloadArgs(paramTypes);

        sb.append(varName).append(methodAccess).append(".overload(").append(overloadArgs)
          .append(").implementation = function(").append(paramNames).append("){\n");

        boolean isVoid = "void".equals(method.getReturnType());
        if (isVoid) {
            sb.append(INDENT).append("this").append(methodAccess).append("(").append(paramNames).append(");\n");
        } else {
            sb.append(INDENT).append("var retval = this").append(methodAccess).append("(").append(paramNames).append(");\n");
        }
        sb.append(buildTraceLog(className, varName, methodName, paramTypes, isVoid));
        appendStackTraceLine(sb, enableStackTrace);
        if (!isVoid) {
            sb.append(INDENT).append("return retval;\n");
        }
        sb.append("}");

        return sb.toString();
    }

    private void appendStackTraceLine(StringBuilder sb, boolean enableStackTrace) {
        if (enableStackTrace) {
            sb.append(INDENT).append("log();\n");
        }
    }

    /**
     * Derives a JavaScript variable name from the fully-qualified class name.
     * Extracts the simple name, lowercases the first character.
     * For obfuscated/short names, sanitizes the full class name into a valid
     * JS identifier (e.g., "am.h" → "am_h", "A2.A" → "A2_A").
     * Falls back to "cls" only if sanitization produces an empty string.
     */
    static String deriveClassVariable(String className) {
        if (className == null || className.isEmpty()) return "cls";
        int dot = className.lastIndexOf('.');
        String simple = dot >= 0 ? className.substring(dot + 1) : className;
        if (!ObfuscationDetector.isUnsuitableForVariable(simple)) {
            return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
        }
        return sanitizeAsVariable(className);
    }

    /**
     * Sanitizes a class name into a concise JS identifier by taking the last 2
     * dot-separated segments (parent + class), joining with underscore, and
     * stripping non-ASCII chars. Falls back to "cls" if the result is empty.
     */
    static String sanitizeAsVariable(String className) {
        String[] parts = className.split("\\.");
        int start = Math.max(0, parts.length - 2);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < parts.length; i++) {
            if (sb.length() > 0) sb.append('_');
            for (int j = 0; j < parts[i].length(); j++) {
                char c = parts[i].charAt(j);
                if (c < 128 && (Character.isLetterOrDigit(c) || c == '_' || c == '$')) {
                    sb.append(c);
                }
            }
        }
        if (sb.length() == 0) return "cls";
        return sb.toString();
    }

    private String buildMethodAccess(String methodName) {
        if (methodName.equals("<init>")) {
            return ".$init";
        }
        if (ObfuscationDetector.isObfuscated(methodName)) {
            return "[\"" + methodName + "\"]";
        }
        return "." + methodName;
    }

    private String buildOverloadArgs(List<String> paramTypes) {
        if (paramTypes.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paramTypes.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(paramTypes.get(i)).append("\"");
        }
        return sb.toString();
    }

    /**
     * Builds a single console.log trace line using JS template literals.
     * Uses simple class name for readable classes, full name for obfuscated ones.
     */
    private String buildTraceLog(String className, String varName, String methodName,
                                 List<String> paramTypes, boolean isVoid) {
        int dot = className.lastIndexOf('.');
        String simple = dot >= 0 ? className.substring(dot + 1) : className;
        String displayClass = ObfuscationDetector.isUnsuitableForVariable(simple) ? className : simple;

        String displayMethod = "<init>".equals(methodName) ? "$init" : methodName;

        StringBuilder sb = new StringBuilder();
        sb.append(INDENT).append("console.log(`");
        sb.append(displayClass).append(".").append(displayMethod).append("(");

        if (!paramTypes.isEmpty()) {
            String[] names = ParamNameGenerator.generateArray(paramTypes);
            for (int i = 0; i < names.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append("${").append(names[i]).append("}");
            }
        }

        sb.append(")");
        if (!isVoid) {
            sb.append(" => ${retval}");
        }
        sb.append("`);\n");

        return sb.toString();
    }
}
