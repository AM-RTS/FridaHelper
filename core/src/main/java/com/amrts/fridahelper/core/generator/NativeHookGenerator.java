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
 * - Address-based hooks: ptr("0x1234")
 * - Wait-for-load wrapper: android_dlopen_ext interception pattern
 * - setTimeout wrapper: delayed execution for timing-sensitive hooks
 *
 * The generator produces the core hook body. Wait-for-load and setTimeout
 * are composed as wrappers around the body (not baked in), keeping each
 * concern isolated and testable.
 */
public final class NativeHookGenerator implements ScriptGenerator {

    @Override
    public GeneratedScript generate(HookRequest request) {
        if (request.getType() != HookRequest.Type.NATIVE) {
            throw new IllegalArgumentException(
                    "NativeHookGenerator requires a NATIVE HookRequest, got: " + request.getType());
        }

        NativeSymbol symbol = request.getNativeSymbol();

        // Build the core Interceptor.attach body
        String hookBody = buildInterceptorAttach(symbol);

        // Apply optional wrappers
        String script = hookBody;

        if (symbol.isWaitForLoad()) {
            script = wrapInWaitForLoad(script, symbol.getLibName(), symbol.getExportName());
        }

        if (symbol.getSetTimeoutMs() > 0) {
            script = wrapInSetTimeout(script, symbol.getSetTimeoutMs());
        }

        return new GeneratedScript(script, HookRequest.Type.NATIVE);
    }

    @Override
    public GeneratedScript generateBody(HookRequest request) {
        if (request.getType() != HookRequest.Type.NATIVE) {
            throw new IllegalArgumentException(
                    "NativeHookGenerator requires a NATIVE HookRequest, got: " + request.getType());
        }

        NativeSymbol symbol = request.getNativeSymbol();
        String hookBody = buildInterceptorAttach(symbol);
        return new GeneratedScript(hookBody, HookRequest.Type.NATIVE);
    }

    /**
     * Builds the core Interceptor.attach block.
     */
    private String buildInterceptorAttach(NativeSymbol symbol) {
        StringBuilder sb = new StringBuilder();
        String targetExpr = buildTargetExpression(symbol);
        String label = buildLabel(symbol);

        sb.append("Interceptor.attach(").append(targetExpr).append(", {\n");
        sb.append("    onEnter: function(args) {\n");
        sb.append("        console.log(\"[*] Called ").append(label).append("\");\n");

        for (int i = 0; i < symbol.getArgCount(); i++) {
            sb.append("        console.log(\"Arg ").append(i).append(": \" + args[").append(i).append("]);\n");
        }

        sb.append("    },\n");
        sb.append("    onLeave: function(retval) {\n");
        sb.append("        console.log(\"Return: \" + retval);\n");
        sb.append("    }\n");
        sb.append("});");

        return sb.toString();
    }

    /**
     * Builds the JS expression that resolves the target address.
     * - EXPORT + lib:     Module.findExportByName("lib.so", "func")
     * - EXPORT + null lib: Module.findExportByName(null, "func")
     * - ADDRESS:          ptr("0x1234")
     */
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

    /**
     * Human-readable label for the console.log header line.
     */
    private String buildLabel(NativeSymbol symbol) {
        if (symbol.getTargetMode() == NativeSymbol.TargetMode.ADDRESS) {
            return symbol.getAddress();
        }
        return symbol.getExportName();
    }

    /**
     * Wraps the hook body in the android_dlopen_ext wait-for-load pattern.
     * This intercepts library loading and attaches the hook only after the target lib is loaded.
     */
    private String wrapInWaitForLoad(String hookBody, String libName, String exportName) {
        String indent = "    ";
        String indentedBody = indentBlock(hookBody, indent);

        StringBuilder sb = new StringBuilder();
        sb.append("function onLibLoaded(libName) {\n");
        sb.append("    var nativeMethod = Module.findExportByName(libName, \"").append(exportName).append("\");\n");
        sb.append(indentedBody).append("\n");
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
        sb.append("        onLeave: function(args) {\n");
        sb.append("            if (isLibLoaded) {\n");
        sb.append("                onLibLoaded(libraryName);\n");
        sb.append("                isLibLoaded = false;\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("    });\n");
        sb.append("}\n\n");
        sb.append("waitForLibLoading(\"").append(libName).append("\");");

        return sb.toString();
    }

    /**
     * Wraps the script in setTimeout(function(){ ... }, ms).
     */
    private String wrapInSetTimeout(String script, int ms) {
        String indented = indentBlock(script, "    ");
        return "setTimeout(function() {\n"
                + indented + "\n"
                + "}, " + ms + ");";
    }

    /**
     * Indents every line of a multi-line string by the given prefix.
     */
    private String indentBlock(String block, String indent) {
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
