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
 *   var cls = Java.use("com.example.Foo");
 *   cls.bar.overload("int", "java.lang.String").implementation = function(a, b){
 *       console.log("Param 1: " + a);
 *       console.log("Param 2: " + b);
 *       var retval = this.bar(a, b);
 *       console.log("Return Value: " + retval);
 *       //console.log(Java.use("android.util.Log").getStackTraceString(Java.use("java.lang.Exception").$new()));
 *       return retval;
 *   }
 */
public final class JavaHookGenerator implements ScriptGenerator {

    private static final String INDENT = "    ";

    @Override
    public GeneratedScript generate(HookRequest request) {
        if (request.getType() != HookRequest.Type.JAVA) {
            throw new IllegalArgumentException("JavaHookGenerator requires a JAVA HookRequest, got: " + request.getType());
        }

        SmaliMethod method = request.getSmaliMethod();
        StringBuilder sb = new StringBuilder();

        String className = method.getClassName();
        String methodName = method.getMethodName();
        List<String> paramTypes = method.getParamTypes();
        int paramCount = paramTypes.size();

        String paramNames = ParamNameGenerator.generate(paramCount);
        String methodAccess = buildMethodAccess(methodName);
        String overloadArgs = buildOverloadArgs(paramTypes);

        sb.append("var cls = Java.use(\"").append(className).append("\");\n");
        sb.append("cls").append(methodAccess).append(".overload(").append(overloadArgs)
          .append(").implementation = function(").append(paramNames).append("){\n");

        if (paramCount > 0) {
            sb.append(buildLoggers(paramCount));
        }

        sb.append(INDENT).append("var retval = this").append(methodAccess).append("(").append(paramNames).append(");\n");
        sb.append(INDENT).append("console.log(\"Return Value: \" + retval);\n");
        sb.append(INDENT).append("//console.log(Java.use(\"android.util.Log\").getStackTraceString(Java.use(\"java.lang.Exception\").$new()));\n");
        sb.append(INDENT).append("return retval;\n");
        sb.append("}");

        return new GeneratedScript(sb.toString(), HookRequest.Type.JAVA);
    }

    /**
     * Builds the method access expression, handling obfuscated names and constructors.
     */
    private String buildMethodAccess(String methodName) {
        if (methodName.equals("<init>")) {
            return ".$init";
        }
        if (ObfuscationDetector.isObfuscated(methodName)) {
            return "[\"" + methodName + "\"]";
        }
        return "." + methodName;
    }

    /**
     * Builds the overload() arguments from resolved param types.
     */
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
     * Builds indented console.log statements for each parameter.
     */
    private String buildLoggers(int paramCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paramCount; i++) {
            String varName = ParamNameGenerator.nameAt(i);
            sb.append(INDENT).append("console.log(\"Param ").append(i + 1).append(": \" + ").append(varName).append(");\n");
        }
        return sb.toString();
    }
}
