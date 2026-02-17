package com.amrts.fridahelper.core.generator;

import com.amrts.fridahelper.core.model.GeneratedScript;
import com.amrts.fridahelper.core.model.HookRequest;
import com.amrts.fridahelper.core.model.NativeSymbol;
import com.amrts.fridahelper.core.model.SmaliMethod;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Tests for {@link NativeHookGenerator}.
 * Includes the exact expected output for the canonical example:
 *   libfoo.so, secret_func, argCount=2
 */
public class NativeHookGeneratorTest {

    private final NativeHookGenerator generator = new NativeHookGenerator();

    /**
     * Canonical example: libfoo.so + secret_func + 2 args.
     * This is the exact output the generator must produce.
     */
    @Test
    public void generateCanonicalNativeHook() {
        NativeSymbol symbol = new NativeSymbol("libfoo.so", "secret_func", 2);
        HookRequest request = HookRequest.nativeHook(symbol);

        GeneratedScript result = generator.generate(request);

        assertEquals(HookRequest.Type.NATIVE, result.getHookType());

        String expected =
                "Interceptor.attach(Module.getExportByName(\"libfoo.so\", \"secret_func\"), {\n"
              + "    onEnter: function(args) {\n"
              + "        console.log(\"[*] Called secret_func\");\n"
              + "        console.log(\"Arg 0: \" + args[0]);\n"
              + "        console.log(\"Arg 1: \" + args[1]);\n"
              + "    },\n"
              + "    onLeave: function(retval) {\n"
              + "        console.log(\"Return: \" + retval);\n"
              + "    }\n"
              + "});";

        assertEquals(expected, result.getScriptText());
    }

    /**
     * Zero-arg native hook should omit arg logging.
     */
    @Test
    public void generateZeroArgNativeHook() {
        NativeSymbol symbol = new NativeSymbol("libnative.so", "init", 0);
        HookRequest request = HookRequest.nativeHook(symbol);

        GeneratedScript result = generator.generate(request);

        String script = result.getScriptText();
        assertTrue(script.contains("Module.getExportByName(\"libnative.so\", \"init\")"));
        assertTrue(script.contains("console.log(\"[*] Called init\")"));
        assertFalse(script.contains("args[0]"));
    }

    /**
     * Native hook should NOT be wrapped by ScriptWrapper.wrapIfNeeded.
     */
    @Test
    public void nativeHookNotWrappedByDefault() {
        NativeSymbol symbol = new NativeSymbol("libfoo.so", "test", 0);
        HookRequest request = HookRequest.nativeHook(symbol);

        GeneratedScript result = generator.generate(request);
        GeneratedScript afterWrap = ScriptWrapper.wrapIfNeeded(result);

        assertEquals(result.getScriptText(), afterWrap.getScriptText());
    }

    /**
     * Wrong request type should throw.
     */
    @Test(expected = IllegalArgumentException.class)
    public void generateWithJavaRequestThrows() {
        SmaliMethod method = new SmaliMethod("com.Foo", "bar", Collections.emptyList(), "void");
        HookRequest request = HookRequest.java(method);
        generator.generate(request);
    }
}
