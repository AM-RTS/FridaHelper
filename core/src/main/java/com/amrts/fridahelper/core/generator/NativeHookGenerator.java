package com.amrts.fridahelper.core.generator;

import com.amrts.fridahelper.core.model.GeneratedScript;
import com.amrts.fridahelper.core.model.HookRequest;
import com.amrts.fridahelper.core.model.NativeSymbol;

/**
 * Generates Frida native hook scripts (Interceptor.attach).
 *
 * Supports:
 * - Export-based hooks:  Module.findExportByName("lib.so", "func")
 * - Export with null lib: Module.findExportByName(null, "func")
 * - Address-based hooks: ptr("0x1234") or Module.findBaseAddress("lib").add(ptr("0x..."))
 * - Wait-for-load wrapper: android_dlopen_ext interception pattern
 * - setTimeout wrapper: delayed execution for timing-sensitive hooks
 */
public final class NativeHookGenerator implements ScriptGenerator {

    /** nativeLog() function definition for native backtrace logging. */
    public static final String NATIVE_LOG_FUNCTION =
            "function nativeLog(ctx){\n"
          + "    console.log(Thread.backtrace(ctx, Backtracer.ACCURATE).map(DebugSymbol.fromAddress).join('\\n'));\n"
          + "}";

    @Override
    public GeneratedScript generate(HookRequest request) {
        return generate(request, false);
    }

    public GeneratedScript generate(HookRequest request, boolean enableStackTrace) {
        if (request.getType() != HookRequest.Type.NATIVE) {
            throw new IllegalArgumentException(
                    "NativeHookGenerator requires a NATIVE HookRequest, got: " + request.getType());
        }

        NativeSymbol symbol = request.getNativeSymbol();
        String script;

        if (symbol.isWaitForLoad()) {
            script = buildWaitForLoadScript(symbol, enableStackTrace);
        } else {
            script = buildInterceptorAttach(symbol, enableStackTrace);
        }

        if (symbol.getSetTimeoutMs() > 0) {
            script = wrapInSetTimeout(script, symbol.getSetTimeoutMs());
        }

        if (enableStackTrace) {
            script = NATIVE_LOG_FUNCTION + "\n\n" + script;
        }

        return new GeneratedScript(script, HookRequest.Type.NATIVE);
    }

    /**
     * Generates the full script (including waitForLoad/setTimeout wrappers) but
     * WITHOUT the helper function prefix. Used by ScriptComposer which emits
     * helper functions once at the top of the composed output.
     */
    GeneratedScript generateWithoutHelpers(HookRequest request, boolean enableStackTrace) {
        if (request.getType() != HookRequest.Type.NATIVE) {
            throw new IllegalArgumentException(
                    "NativeHookGenerator requires a NATIVE HookRequest, got: " + request.getType());
        }

        NativeSymbol symbol = request.getNativeSymbol();
        String script;

        if (symbol.isWaitForLoad()) {
            script = buildWaitForLoadScript(symbol, enableStackTrace);
        } else {
            script = buildInterceptorAttach(symbol, enableStackTrace);
        }

        if (symbol.getSetTimeoutMs() > 0) {
            script = wrapInSetTimeout(script, symbol.getSetTimeoutMs());
        }

        return new GeneratedScript(script, HookRequest.Type.NATIVE);
    }

    @Override
    public GeneratedScript generateBody(HookRequest request) {
        return generateBody(request, false);
    }

    public GeneratedScript generateBody(HookRequest request, boolean enableStackTrace) {
        if (request.getType() != HookRequest.Type.NATIVE) {
            throw new IllegalArgumentException(
                    "NativeHookGenerator requires a NATIVE HookRequest, got: " + request.getType());
        }

        NativeSymbol symbol = request.getNativeSymbol();
        String hookBody = buildInterceptorAttach(symbol, enableStackTrace);
        return new GeneratedScript(hookBody, HookRequest.Type.NATIVE);
    }

    private String buildInterceptorAttach(NativeSymbol symbol, boolean enableStackTrace) {
        return buildInterceptorBlock(buildTargetExpression(symbol), buildLabel(symbol), symbol.getArgCount(), enableStackTrace);
    }

    private String buildInterceptorBlock(String targetExpr, String label, int argCount, boolean enableStackTrace) {
        StringBuilder sb = new StringBuilder();
        sb.append("Interceptor.attach(").append(targetExpr).append(", {\n");
        sb.append("    onEnter: function(args) {\n");
        sb.append("        console.log(\"[*] Called ").append(label).append("\");\n");

        for (int i = 0; i < argCount; i++) {
            sb.append("        console.log(\"Arg ").append(i).append(": \" + args[").append(i).append("]);\n");
        }

        if (enableStackTrace) {
            sb.append("        nativeLog(this.context);\n");
        }

        sb.append("    },\n");
        sb.append("    onLeave: function(retval) {\n");
        sb.append("        console.log(\"Return: \" + retval);\n");
        sb.append("    }\n");
        sb.append("});");
        return sb.toString();
    }

    private String buildTargetExpression(NativeSymbol symbol) {
        if (symbol.getTargetMode() == NativeSymbol.TargetMode.ADDRESS) {
            if (symbol.getLibName() != null) {
                return "Module.findBaseAddress(\"" + symbol.getLibName() + "\").add(ptr(\"" + symbol.getAddress() + "\"))";
            }
            return "ptr(\"" + symbol.getAddress() + "\")";
        }

        String libArg = symbol.getLibName() == null
                ? "null"
                : "\"" + symbol.getLibName() + "\"";

        return "Module.findExportByName(" + libArg + ", \"" + symbol.getExportName() + "\")";
    }

    private String buildLabel(NativeSymbol symbol) {
        if (symbol.getTargetMode() == NativeSymbol.TargetMode.ADDRESS) {
            return symbol.getAddress();
        }
        return symbol.getExportName();
    }

    /**
     * Builds the inner body for a waitForLoad hook: resolves via the libName function
     * parameter and attaches the interceptor. Used by ScriptComposer for grouping
     * multiple waitForLoad hooks on the same library.
     *
     * @param symbol the native symbol
     * @param varName JS variable name for the resolved target (e.g. "nativeMethod" or "nativeMethod0")
     * @param enableStackTrace whether to include nativeLog() calls
     * @return the "var ... = ...; Interceptor.attach(...)" block
     */
    String buildWaitForLoadInner(NativeSymbol symbol, String varName, boolean enableStackTrace) {
        String resolve;
        String label;
        if (symbol.getTargetMode() == NativeSymbol.TargetMode.ADDRESS) {
            resolve = "var " + varName + " = Module.findBaseAddress(libName).add(ptr(\"" + symbol.getAddress() + "\"));";
            label = symbol.getAddress();
        } else {
            resolve = "var " + varName + " = Module.findExportByName(libName, \"" + symbol.getExportName() + "\");";
            label = symbol.getExportName();
        }
        String interceptor = buildInterceptorBlock(varName, label, symbol.getArgCount(), enableStackTrace);
        return resolve + "\n" + interceptor;
    }

    /**
     * Builds the full waitForLoad script. Resolves the target from the dynamic
     * libName parameter passed at runtime by the dlopen interceptor.
     * Supports both EXPORT mode (findExportByName) and ADDRESS mode (findBaseAddress + offset).
     */
    private String buildWaitForLoadScript(NativeSymbol symbol, boolean enableStackTrace) {
        String label;
        String resolveLine;
        if (symbol.getTargetMode() == NativeSymbol.TargetMode.ADDRESS) {
            label = symbol.getAddress();
            resolveLine = "    var nativeMethod = Module.findBaseAddress(libName).add(ptr(\"" + symbol.getAddress() + "\"));\n";
        } else {
            label = symbol.getExportName();
            resolveLine = "    var nativeMethod = Module.findExportByName(libName, \"" + symbol.getExportName() + "\");\n";
        }
        int argCount = symbol.getArgCount();

        String innerBody = buildInterceptorBlock("nativeMethod", label, argCount, enableStackTrace);
        String indentedInner = indentBlock(innerBody, "    ");

        StringBuilder sb = new StringBuilder();
        sb.append("function onLibLoaded(libName) {\n");
        sb.append(resolveLine);
        sb.append(indentedInner).append("\n");
        sb.append("}\n\n");
        sb.append("function waitForLibLoading(libraryName) {\n");
        sb.append("    var isLibLoaded = false;\n\n");
        sb.append("    Interceptor.attach(Module.findExportByName(null, \"android_dlopen_ext\"), {\n");
        sb.append("        onEnter: function(args) {\n");
        sb.append("            var libraryPath = Memory.readCString(args[0]);\n");
        sb.append("            if (libraryPath.includes(libraryName)) {\n");
        sb.append("                console.log(\"[+] Loading library \" + libraryPath + \"...\");\n");
        sb.append("                isLibLoaded = true;\n");
        sb.append("            }\n");
        sb.append("        },\n");
        sb.append("        onLeave: function(retval) {\n");
        sb.append("            if (isLibLoaded) {\n");
        sb.append("                onLibLoaded(libraryName);\n");
        sb.append("                isLibLoaded = false;\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("    });\n");
        sb.append("}\n\n");
        sb.append("waitForLibLoading(\"").append(symbol.getLibName()).append("\");");

        return sb.toString();
    }

    private String wrapInSetTimeout(String script, int ms) {
        String indented = indentBlock(script, "    ");
        return "setTimeout(function() {\n"
                + indented + "\n"
                + "}, " + ms + ");";
    }

    private String indentBlock(String block, String indent) {
        return com.amrts.fridahelper.core.util.ScriptIndent.indentBlock(block, indent);
    }
}
