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
 *       //console.log(Java.use("android.util.Log").getStackTraceString(Java.use("java.lang.Exception").$new()));
 *       return retval;
 *   }
 */
public final class JavaHookGenerator implements ScriptGenerator {

    private static final String INDENT = "    ";

    @Override
    public GeneratedScript generate(HookRequest request) {
        return generateBody(request);
    }

    @Override
    public GeneratedScript generateBody(HookRequest request) {
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
        sb.append(INDENT).append("//console.log(Java.use(\"android.util.Log\").getStackTraceString(Java.use(\"java.lang.Exception\").$new()));\n");
        if (!isVoid) {
            sb.append(INDENT).append("return retval;\n");
        }
        sb.append("}");

        return new GeneratedScript(sb.toString(), HookRequest.Type.JAVA);
    }

    /**
     * Generates just the method hook portion without the Java.use declaration.
     * Used by ScriptComposer when grouping multiple hooks for the same class.
     *
     * @param request the hook request
     * @param varName the variable name to use (from the shared Java.use call)
     * @return script text containing only the method hook (no var declaration)
     */
    public String generateMethodHook(HookRequest request, String varName) {
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
        sb.append(INDENT).append("//console.log(Java.use(\"android.util.Log\").getStackTraceString(Java.use(\"java.lang.Exception\").$new()));\n");
        if (!isVoid) {
            sb.append(INDENT).append("return retval;\n");
        }
        sb.append("}");

        return sb.toString();
    }

    /**
     * Derives a JavaScript variable name from the fully-qualified class name.
     * Extracts the simple name, lowercases the first character.
     * Falls back to "cls" if the name is obfuscated or too short.
     */
    static String deriveClassVariable(String className) {
        if (className == null || className.isEmpty()) return "cls";
        int dot = className.lastIndexOf('.');
        String simple = dot >= 0 ? className.substring(dot + 1) : className;
        if (ObfuscationDetector.isUnsuitableForVariable(simple)) return "cls";
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
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
     * Uses simple class name for readable classes, full name for obfuscated ones (varName == "cls").
     */
    private String buildTraceLog(String className, String varName, String methodName,
                                 List<String> paramTypes, boolean isVoid) {
        String displayClass;
        if ("cls".equals(varName)) {
            displayClass = className;
        } else {
            int dot = className.lastIndexOf('.');
            displayClass = dot >= 0 ? className.substring(dot + 1) : className;
        }

        String displayMethod = "<init>".equals(methodName) ? "$init" : methodName;

        StringBuilder sb = new StringBuilder();
        sb.append(INDENT).append("console.log(`");
        sb.append(displayClass).append(".").append(displayMethod).append("(");

        if (!paramTypes.isEmpty()) {
            String[] names = ParamNameGenerator.generate(paramTypes).split(", ");
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
