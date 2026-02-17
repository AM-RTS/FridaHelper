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
 * Covers: export-based, address-based, null lib, waitForLoad, setTimeout, and combinations.
 */
public class NativeHookGeneratorTest {

    private final NativeHookGenerator generator = new NativeHookGenerator();

    // ===== Export-based hooks =====

    /**
     * Canonical example: libfoo.so + secret_func + 2 args.
     */
    @Test
    public void generateCanonicalExportHook() {
        NativeSymbol symbol = new NativeSymbol.Builder()
                .libName("libfoo.so")
                .exportName("secret_func")
                .argCount(2)
                .build();
        HookRequest request = HookRequest.nativeHook(symbol);
        GeneratedScript result = generator.generate(request);

        assertEquals(HookRequest.Type.NATIVE, result.getHookType());

        String expected =
                "Interceptor.attach(Module.findExportByName(\"libfoo.so\", \"secret_func\"), {\n"
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
     * Zero-arg export hook should omit arg logging.
     */
    @Test
    public void generateZeroArgExportHook() {
        NativeSymbol symbol = new NativeSymbol.Builder()
                .libName("libnative.so")
                .exportName("init")
                .argCount(0)
                .build();
        HookRequest request = HookRequest.nativeHook(symbol);
        GeneratedScript result = generator.generate(request);

        String script = result.getScriptText();
        assertTrue(script.contains("Module.findExportByName(\"libnative.so\", \"init\")"));
        assertTrue(script.contains("console.log(\"[*] Called init\")"));
        assertFalse(script.contains("args[0]"));
    }

    // ===== Null library (wildcard) =====

    /**
     * Empty/null library name should produce null in JS (not "null" string).
     */
    @Test
    public void generateNullLibraryExportHook() {
        NativeSymbol symbol = new NativeSymbol.Builder()
                .libName(null)
                .exportName("open")
                .argCount(1)
                .build();
        HookRequest request = HookRequest.nativeHook(symbol);
        GeneratedScript result = generator.generate(request);

        String script = result.getScriptText();
        assertTrue(script.contains("Module.findExportByName(null, \"open\")"));
        assertFalse(script.contains("\"null\""));
    }

    /**
     * Empty string lib name should also produce null.
     */
    @Test
    public void generateEmptyLibraryProducesNull() {
        NativeSymbol symbol = new NativeSymbol.Builder()
                .libName("")
                .exportName("open")
                .argCount(0)
                .build();
        assertNull(symbol.getLibName());

        GeneratedScript result = generator.generate(HookRequest.nativeHook(symbol));
        assertTrue(result.getScriptText().contains("Module.findExportByName(null, \"open\")"));
    }

    // ===== Address-based hooks =====

    /**
     * Address mode should produce ptr("0xABCD").
     */
    @Test
    public void generateAddressBasedHook() {
        NativeSymbol symbol = new NativeSymbol.Builder()
                .address("0xDEAD")
                .argCount(1)
                .build();
        HookRequest request = HookRequest.nativeHook(symbol);
        GeneratedScript result = generator.generate(request);

        String expected =
                "Interceptor.attach(ptr(\"0xDEAD\"), {\n"
              + "    onEnter: function(args) {\n"
              + "        console.log(\"[*] Called 0xDEAD\");\n"
              + "        console.log(\"Arg 0: \" + args[0]);\n"
              + "    },\n"
              + "    onLeave: function(retval) {\n"
              + "        console.log(\"Return: \" + retval);\n"
              + "    }\n"
              + "});";

        assertEquals(expected, result.getScriptText());
    }

    // ===== Wait for load =====

    /**
     * waitForLoad should wrap in android_dlopen_ext pattern.
     */
    @Test
    public void generateWaitForLoadHook() {
        NativeSymbol symbol = new NativeSymbol.Builder()
                .libName("native-lib.so")
                .exportName("Jniint")
                .argCount(0)
                .waitForLoad(true)
                .build();
        HookRequest request = HookRequest.nativeHook(symbol);
        GeneratedScript result = generator.generate(request);

        String script = result.getScriptText();
        assertTrue(script.contains("function onLibLoaded(libName)"));
        assertTrue(script.contains("function waitForLibLoading(libraryName)"));
        assertTrue(script.contains("android_dlopen_ext"));
        assertTrue(script.contains("libraryPath.includes(libraryName)"));
        assertTrue(script.contains("onLibLoaded(libraryName)"));
        assertTrue(script.contains("waitForLibLoading(\"native-lib.so\")"));
        assertTrue(script.contains("Module.findExportByName(libName, \"Jniint\")"));
    }

    /**
     * waitForLoad with null lib should fail at build time.
     */
    @Test(expected = IllegalArgumentException.class)
    public void waitForLoadWithNullLibThrows() {
        new NativeSymbol.Builder()
                .libName(null)
                .exportName("func")
                .waitForLoad(true)
                .build();
    }

    /**
     * waitForLoad with ADDRESS mode should fail at build time.
     */
    @Test(expected = IllegalArgumentException.class)
    public void waitForLoadWithAddressModeThrows() {
        new NativeSymbol.Builder()
                .address("0x1234")
                .waitForLoad(true)
                .build();
    }

    // ===== setTimeout =====

    /**
     * setTimeout should wrap the entire script.
     */
    @Test
    public void generateWithSetTimeout() {
        NativeSymbol symbol = new NativeSymbol.Builder()
                .libName("libfoo.so")
                .exportName("func")
                .argCount(0)
                .setTimeoutMs(500)
                .build();
        HookRequest request = HookRequest.nativeHook(symbol);
        GeneratedScript result = generator.generate(request);

        String script = result.getScriptText();
        assertTrue(script.startsWith("setTimeout(function() {"));
        assertTrue(script.contains("Module.findExportByName(\"libfoo.so\", \"func\")"));
        assertTrue(script.endsWith("}, 500);"));
    }

    // ===== Combinations =====

    /**
     * waitForLoad + setTimeout should apply both wrappers.
     */
    @Test
    public void generateWaitForLoadAndSetTimeout() {
        NativeSymbol symbol = new NativeSymbol.Builder()
                .libName("libcrypto.so")
                .exportName("encrypt")
                .argCount(2)
                .waitForLoad(true)
                .setTimeoutMs(1000)
                .build();
        HookRequest request = HookRequest.nativeHook(symbol);
        GeneratedScript result = generator.generate(request);

        String script = result.getScriptText();
        // setTimeout is the outermost wrapper
        assertTrue(script.startsWith("setTimeout(function() {"));
        assertTrue(script.endsWith("}, 1000);"));
        // waitForLoad is inside
        assertTrue(script.contains("function waitForLibLoading(libraryName)"));
        assertTrue(script.contains("waitForLibLoading(\"libcrypto.so\")"));
    }

    // ===== Backward compatibility =====

    /**
     * The convenience factory NativeSymbol.export() should still work.
     */
    @Test
    public void backwardCompatExportFactory() {
        NativeSymbol symbol = NativeSymbol.export("libfoo.so", "secret_func", 2);
        HookRequest request = HookRequest.nativeHook(symbol);
        GeneratedScript result = generator.generate(request);

        assertTrue(result.getScriptText().contains("Module.findExportByName(\"libfoo.so\", \"secret_func\")"));
        assertFalse(result.getScriptText().contains("setTimeout"));
        assertFalse(result.getScriptText().contains("waitForLibLoading"));
    }

    // ===== Validation =====

    @Test(expected = IllegalArgumentException.class)
    public void exportModeRequiresExportName() {
        new NativeSymbol.Builder().libName("lib.so").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void addressModeRequiresAddress() {
        new NativeSymbol.Builder().address("").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void argCountExceedingMaxThrows() {
        new NativeSymbol.Builder()
                .libName("lib.so")
                .exportName("func")
                .argCount(NativeSymbol.MAX_ARG_COUNT + 1)
                .build();
    }

    @Test
    public void argCountAtMaxBoundarySucceeds() {
        NativeSymbol symbol = new NativeSymbol.Builder()
                .libName("lib.so")
                .exportName("func")
                .argCount(NativeSymbol.MAX_ARG_COUNT)
                .build();
        assertEquals(NativeSymbol.MAX_ARG_COUNT, symbol.getArgCount());
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
