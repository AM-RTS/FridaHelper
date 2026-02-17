package com.amrts.fridahelper.core.generator;

import com.amrts.fridahelper.core.model.GeneratedScript;
import com.amrts.fridahelper.core.model.HookRequest;
import com.amrts.fridahelper.core.model.NativeSymbol;

/**
 * Generates Frida native (Interceptor.attach) hook scripts.
 *
 * Output format:
 *   Interceptor.attach(Module.getExportByName("libfoo.so", "secret_func"), {
 *       onEnter: function(args) {
 *           console.log("[*] Called secret_func");
 *           console.log("Arg 0: " + args[0]);
 *           console.log("Arg 1: " + args[1]);
 *       },
 *       onLeave: function(retval) {
 *           console.log("Return: " + retval);
 *       }
 *   });
 */
public final class NativeHookGenerator implements ScriptGenerator {

    @Override
    public GeneratedScript generate(HookRequest request) {
        if (request.getType() != HookRequest.Type.NATIVE) {
            throw new IllegalArgumentException(
                    "NativeHookGenerator requires a NATIVE HookRequest, got: " + request.getType());
        }

        NativeSymbol symbol = request.getNativeSymbol();
        String lib = symbol.getLibName();
        String export = symbol.getExportName();
        int argCount = symbol.getArgCount();

        StringBuilder sb = new StringBuilder();

        sb.append("Interceptor.attach(Module.getExportByName(\"")
          .append(lib).append("\", \"").append(export).append("\"), {\n");

        sb.append("    onEnter: function(args) {\n");
        sb.append("        console.log(\"[*] Called ").append(export).append("\");\n");

        for (int i = 0; i < argCount; i++) {
            sb.append("        console.log(\"Arg ").append(i).append(": \" + args[").append(i).append("]);\n");
        }

        sb.append("    },\n");
        sb.append("    onLeave: function(retval) {\n");
        sb.append("        console.log(\"Return: \" + retval);\n");
        sb.append("    }\n");
        sb.append("});");

        return new GeneratedScript(sb.toString(), HookRequest.Type.NATIVE);
    }
}
