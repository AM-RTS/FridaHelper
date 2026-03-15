package com.amrts.fridahelper.core.generator;

import com.amrts.fridahelper.core.model.GeneratedScript;
import com.amrts.fridahelper.core.model.HookRequest;
import com.amrts.fridahelper.core.model.NativeSymbol;
import com.amrts.fridahelper.core.model.SmaliMethod;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Tests for {@link ScriptComposer}.
 * Covers: single hooks, mixed hooks, wrappers, waitForLoad placement,
 * insertion order, edge cases, and snapshot assertions.
 */
public class ScriptComposerTest {

    private ScriptComposer composer;

    @Before
    public void setUp() {
        composer = new ScriptComposer();
    }

    // ========== Lifecycle & validation ==========

    @Test(expected = IllegalStateException.class)
    public void composeEmptyThrows() {
        composer.compose(CompositionOptions.none());
    }

    @Test(expected = IllegalArgumentException.class)
    public void composeNullOptionsThrows() {
        composer.addRequest(javaRequest("com.Foo", "bar"));
        composer.compose(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addNullRequestThrows() {
        composer.addRequest(null);
    }

    @Test
    public void clearRemovesAllRequests() {
        composer.addRequest(javaRequest("com.Foo", "bar"));
        composer.addRequest(javaRequest("com.Foo", "baz"));
        assertEquals(2, composer.size());

        composer.clear();
        assertEquals(0, composer.size());
    }

    @Test
    public void getRequestsReturnsUnmodifiableList() {
        composer.addRequest(javaRequest("com.Foo", "bar"));
        try {
            composer.getRequests().add(javaRequest("com.Foo", "baz"));
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // ========== Single hook composition ==========

    @Test
    public void singleJavaHookNoWrappers() {
        composer.addRequest(javaRequest("com.example.Foo", "bar"));
        GeneratedScript result = composer.compose(CompositionOptions.none());

        assertEquals(HookRequest.Type.JAVA, result.getHookType());
        assertTrue(result.getScriptText().contains("Java.use(\"com.example.Foo\")"));
        assertTrue(result.getScriptText().contains("foo.bar.overload()"));
        assertFalse(result.getScriptText().contains("Java.perform"));
        assertFalse(result.getScriptText().contains("setTimeout"));
    }

    @Test
    public void singleJavaHookWithPerform() {
        composer.addRequest(javaRequest("com.example.Foo", "bar"));
        GeneratedScript result = composer.compose(CompositionOptions.withPerform());

        assertTrue(result.getScriptText().startsWith("Java.perform(function(){"));
        assertTrue(result.getScriptText().endsWith("});"));
        assertTrue(result.getScriptText().contains("Java.use(\"com.example.Foo\")"));
    }

    @Test
    public void singleNativeHookNoWrappers() {
        composer.addRequest(nativeExportRequest("libfoo.so", "func", 1));
        GeneratedScript result = composer.compose(CompositionOptions.none());

        assertEquals(HookRequest.Type.NATIVE, result.getHookType());
        assertTrue(result.getScriptText().contains("Interceptor.attach"));
        assertTrue(result.getScriptText().contains("Module.findExportByName(\"libfoo.so\", \"func\")"));
        assertFalse(result.getScriptText().contains("Java.perform"));
    }

    @Test
    public void singleNativeHookWithPerformAndTimeout() {
        composer.addRequest(nativeExportRequest("libfoo.so", "func", 0));
        CompositionOptions options = CompositionOptions.builder()
                .wrapInPerform(true)
                .setTimeoutMs(500)
                .build();
        GeneratedScript result = composer.compose(options);

        // setTimeout outermost
        assertTrue(result.getScriptText().startsWith("setTimeout(function() {"));
        assertTrue(result.getScriptText().endsWith("}, 500);"));
        // Java.perform inner
        assertTrue(result.getScriptText().contains("Java.perform(function(){"));
        // Body innermost
        assertTrue(result.getScriptText().contains("Interceptor.attach"));
    }

    // ========== Multiple hooks of same type ==========

    @Test
    public void twoJavaHooksMerged() {
        composer.addRequest(javaRequest("com.Foo", "bar"));
        composer.addRequest(javaRequest("com.Baz", "qux"));
        GeneratedScript result = composer.compose(CompositionOptions.withPerform());

        String script = result.getScriptText();
        assertEquals(HookRequest.Type.JAVA, result.getHookType());
        assertTrue(script.startsWith("Java.perform(function(){"));

        // Both hooks present
        assertTrue(script.contains("Java.use(\"com.Foo\")"));
        assertTrue(script.contains("foo.bar.overload()"));
        assertTrue(script.contains("Java.use(\"com.Baz\")"));
        assertTrue(script.contains("baz.qux.overload()"));

        // Only one Java.perform wrapper (not two)
        assertEquals(1, countOccurrences(script, "Java.perform(function(){"));
    }

    @Test
    public void sameClassJavaHooksGrouped_singleJavaUse() {
        composer.addRequest(javaRequest("com.example.Foo", "bar"));
        composer.addRequest(javaRequest("com.example.Foo", "baz"));
        GeneratedScript result = composer.compose(CompositionOptions.none());

        String script = result.getScriptText();
        assertEquals(1, countOccurrences(script, "Java.use(\"com.example.Foo\")"));
        assertTrue(script.contains("foo.bar.overload()"));
        assertTrue(script.contains("foo.baz.overload()"));
    }

    @Test
    public void sameClassJavaHooksGrouped_withPerform() {
        composer.addRequest(javaRequest("com.example.Foo", "first"));
        composer.addRequest(javaRequest("com.example.Foo", "second"));
        composer.addRequest(javaRequest("com.example.Bar", "other"));
        GeneratedScript result = composer.compose(CompositionOptions.withPerform());

        String script = result.getScriptText();
        assertEquals(1, countOccurrences(script, "Java.use(\"com.example.Foo\")"));
        assertEquals(1, countOccurrences(script, "Java.use(\"com.example.Bar\")"));
        assertEquals(1, countOccurrences(script, "Java.perform(function(){"));
        assertTrue(script.contains("foo.first.overload()"));
        assertTrue(script.contains("foo.second.overload()"));
        assertTrue(script.contains("bar.other.overload()"));
    }

    @Test
    public void twoNativeHooksMerged() {
        composer.addRequest(nativeExportRequest("libfoo.so", "func_a", 1));
        composer.addRequest(nativeExportRequest("libbar.so", "func_b", 2));
        GeneratedScript result = composer.compose(CompositionOptions.none());

        String script = result.getScriptText();
        assertEquals(HookRequest.Type.NATIVE, result.getHookType());

        // Both hooks present
        assertTrue(script.contains("Module.findExportByName(\"libfoo.so\", \"func_a\")"));
        assertTrue(script.contains("Module.findExportByName(\"libbar.so\", \"func_b\")"));

        // Separated by blank line
        assertTrue(script.contains("});\n\nInterceptor.attach"));
    }

    // ========== Mixed Java + Native hooks ==========

    @Test
    public void mixedJavaAndNativeHooksWithPerform() {
        composer.addRequest(javaRequest("com.Foo", "bar"));
        composer.addRequest(nativeExportRequest("libfoo.so", "native_func", 1));
        GeneratedScript result = composer.compose(CompositionOptions.withPerform());

        String script = result.getScriptText();
        // Result type is JAVA because at least one Java hook is present
        assertEquals(HookRequest.Type.JAVA, result.getHookType());

        // Both hooks inside one Java.perform
        assertTrue(script.startsWith("Java.perform(function(){"));
        assertTrue(script.contains("Java.use(\"com.Foo\")"));
        assertTrue(script.contains("Interceptor.attach"));
        assertEquals(1, countOccurrences(script, "Java.perform(function(){"));
    }

    @Test
    public void mixedHooksPreserveInsertionOrder() {
        composer.addRequest(nativeExportRequest("libfoo.so", "first", 0));
        composer.addRequest(javaRequest("com.Middle", "second"));
        composer.addRequest(nativeExportRequest("libbar.so", "third", 0));

        GeneratedScript result = composer.compose(CompositionOptions.none());
        String script = result.getScriptText();

        int firstIdx = script.indexOf("\"first\"");
        int secondIdx = script.indexOf("com.Middle");
        int thirdIdx = script.indexOf("\"third\"");

        assertTrue("first before second", firstIdx < secondIdx);
        assertTrue("second before third", secondIdx < thirdIdx);
    }

    // ========== waitForLoad placement ==========

    @Test
    public void waitForLoadHookEmittedAtTopLevel() {
        NativeSymbol waitSymbol = new NativeSymbol.Builder()
                .libName("libnative.so")
                .exportName("Jniint")
                .argCount(0)
                .waitForLoad(true)
                .build();
        composer.addRequest(HookRequest.nativeHook(waitSymbol));
        composer.addRequest(javaRequest("com.Foo", "bar"));

        GeneratedScript result = composer.compose(CompositionOptions.withPerform());
        String script = result.getScriptText();

        // waitForLoad comes first (top-level)
        assertTrue(script.startsWith("function onLibLoaded(libName)"));
        assertTrue(script.contains("waitForLibLoading(\"libnative.so\")"));

        // Java hook is in wrapped section after
        assertTrue(script.contains("Java.perform(function(){"));
        assertTrue(script.contains("Java.use(\"com.Foo\")"));

        // waitForLoad functions are outside Java.perform
        int waitForLoadEnd = script.indexOf("waitForLibLoading(\"libnative.so\");");
        int javaPerformStart = script.indexOf("Java.perform(function(){");
        assertTrue("waitForLoad before Java.perform", waitForLoadEnd < javaPerformStart);
    }

    @Test
    public void multipleWaitForLoadHooks() {
        NativeSymbol wait1 = new NativeSymbol.Builder()
                .libName("libfoo.so")
                .exportName("func_a")
                .argCount(0)
                .waitForLoad(true)
                .build();
        NativeSymbol wait2 = new NativeSymbol.Builder()
                .libName("libbar.so")
                .exportName("func_b")
                .argCount(1)
                .waitForLoad(true)
                .build();
        composer.addRequest(HookRequest.nativeHook(wait1));
        composer.addRequest(HookRequest.nativeHook(wait2));

        GeneratedScript result = composer.compose(CompositionOptions.none());
        String script = result.getScriptText();

        // Both waitForLoad hooks present
        assertTrue(script.contains("waitForLibLoading(\"libfoo.so\")"));
        assertTrue(script.contains("waitForLibLoading(\"libbar.so\")"));

        // No wrapper applied (all hooks are top-level)
        assertFalse(script.contains("Java.perform"));
    }

    @Test
    public void waitForLoadWithTimeoutOnSymbolPreserved() {
        NativeSymbol symbol = new NativeSymbol.Builder()
                .libName("libnative.so")
                .exportName("func")
                .argCount(0)
                .waitForLoad(true)
                .setTimeoutMs(1000)
                .build();
        composer.addRequest(HookRequest.nativeHook(symbol));

        GeneratedScript result = composer.compose(CompositionOptions.none());
        String script = result.getScriptText();

        // The waitForLoad hook's own setTimeout is preserved
        assertTrue(script.contains("setTimeout(function() {"));
        assertTrue(script.contains("}, 1000);"));
        assertTrue(script.contains("waitForLibLoading(\"libnative.so\")"));
    }

    // ========== Wrapper deduplication ==========

    @Test
    public void compositionPerformWrapsDeduplicated() {
        composer.addRequest(javaRequest("com.A", "a"));
        composer.addRequest(javaRequest("com.B", "b"));
        composer.addRequest(javaRequest("com.C", "c"));

        GeneratedScript result = composer.compose(CompositionOptions.withPerform());
        String script = result.getScriptText();

        // Exactly one Java.perform
        assertEquals(1, countOccurrences(script, "Java.perform(function(){"));
        // All three hooks present
        assertTrue(script.contains("Java.use(\"com.A\")"));
        assertTrue(script.contains("Java.use(\"com.B\")"));
        assertTrue(script.contains("Java.use(\"com.C\")"));
    }

    @Test
    public void setTimeoutWrapsEntireComposition() {
        composer.addRequest(javaRequest("com.Foo", "bar"));
        composer.addRequest(nativeExportRequest("lib.so", "func", 0));

        CompositionOptions options = CompositionOptions.builder()
                .wrapInPerform(true)
                .setTimeoutMs(300)
                .build();
        GeneratedScript result = composer.compose(options);
        String script = result.getScriptText();

        // setTimeout is outermost
        assertTrue(script.startsWith("setTimeout(function() {"));
        assertTrue(script.endsWith("}, 300);"));

        // Java.perform is inside setTimeout
        assertTrue(script.contains("Java.perform(function(){"));

        // Exactly one of each
        assertEquals(1, countOccurrences(script, "setTimeout(function() {"));
        assertEquals(1, countOccurrences(script, "Java.perform(function(){"));
    }

    // ========== Complex mixed scenarios ==========

    @Test
    public void fullMixedScenario_JavaNativeWaitForLoad() {
        // 1 Java hook
        composer.addRequest(javaRequest("com.example.App", "onCreate"));

        // 1 standard native hook
        composer.addRequest(nativeExportRequest("libcrypto.so", "encrypt", 2));

        // 1 waitForLoad native hook
        NativeSymbol waitSymbol = new NativeSymbol.Builder()
                .libName("libnative.so")
                .exportName("Jniint")
                .argCount(1)
                .waitForLoad(true)
                .build();
        composer.addRequest(HookRequest.nativeHook(waitSymbol));

        CompositionOptions options = CompositionOptions.builder()
                .wrapInPerform(true)
                .setTimeoutMs(500)
                .build();
        GeneratedScript result = composer.compose(options);
        String script = result.getScriptText();

        // 1. waitForLoad is first (top-level)
        assertTrue(script.startsWith("function onLibLoaded(libName)"));
        assertTrue(script.contains("waitForLibLoading(\"libnative.so\")"));

        // 2. Wrapped section follows with setTimeout outermost, Java.perform inner
        assertTrue(script.contains("setTimeout(function() {"));
        assertTrue(script.contains("Java.perform(function(){"));

        // 3. Java hook and standard native hook are inside the wrappers
        assertTrue(script.contains("Java.use(\"com.example.App\")"));
        assertTrue(script.contains("Module.findExportByName(\"libcrypto.so\", \"encrypt\")"));

        // 4. waitForLoad is NOT inside Java.perform or setTimeout
        int waitEnd = script.indexOf("waitForLibLoading(\"libnative.so\");")
                + "waitForLibLoading(\"libnative.so\");".length();
        int setTimeoutStart = script.indexOf("setTimeout(function() {");
        assertTrue("waitForLoad section ends before setTimeout", waitEnd < setTimeoutStart);
    }

    @Test
    public void onlyWaitForLoadHooksNoWrappedSection() {
        NativeSymbol symbol = new NativeSymbol.Builder()
                .libName("lib.so")
                .exportName("func")
                .argCount(0)
                .waitForLoad(true)
                .build();
        composer.addRequest(HookRequest.nativeHook(symbol));

        // Even with wrapInPerform=true, no Java.perform is emitted
        // because all hooks are top-level (no wrapped section exists)
        GeneratedScript result = composer.compose(CompositionOptions.withPerform());
        String script = result.getScriptText();

        assertTrue(script.contains("waitForLibLoading(\"lib.so\")"));
        assertFalse(script.contains("Java.perform"));
    }

    // ========== Snapshot test: exact output verification ==========

    @Test
    public void snapshot_TwoJavaHooksWrapped() {
        SmaliMethod m1 = new SmaliMethod("com.Foo", "bar",
                Arrays.asList("int"), "void");
        SmaliMethod m2 = new SmaliMethod("com.Baz", "qux",
                Collections.emptyList(), "java.lang.String");

        composer.addRequest(HookRequest.java(m1));
        composer.addRequest(HookRequest.java(m2));

        GeneratedScript result = composer.compose(CompositionOptions.withPerform());

        String expected =
                "Java.perform(function(){\n"
              + "    var foo = Java.use(\"com.Foo\");\n"
              + "    foo.bar.overload(\"int\").implementation = function(i){\n"
              + "        this.bar(i);\n"
              + "        console.log(`Foo.bar(${i})`);\n"
              + "        //console.log(Java.use(\"android.util.Log\").getStackTraceString(Java.use(\"java.lang.Exception\").$new()));\n"
              + "    }\n"
              + "\n"
              + "    var baz = Java.use(\"com.Baz\");\n"
              + "    baz.qux.overload().implementation = function(){\n"
              + "        var retval = this.qux();\n"
              + "        console.log(`Baz.qux() => ${retval}`);\n"
              + "        //console.log(Java.use(\"android.util.Log\").getStackTraceString(Java.use(\"java.lang.Exception\").$new()));\n"
              + "        return retval;\n"
              + "    }\n"
              + "});";

        assertEquals(expected, result.getScriptText());
    }

    @Test
    public void snapshot_NativeHookWithSetTimeoutAndPerform() {
        composer.addRequest(nativeExportRequest("libfoo.so", "secret", 1));

        CompositionOptions options = CompositionOptions.builder()
                .wrapInPerform(true)
                .setTimeoutMs(500)
                .build();
        GeneratedScript result = composer.compose(options);

        String expected =
                "setTimeout(function() {\n"
              + "    Java.perform(function(){\n"
              + "        Interceptor.attach(Module.findExportByName(\"libfoo.so\", \"secret\"), {\n"
              + "            onEnter: function(args) {\n"
              + "                console.log(\"[*] Called secret\");\n"
              + "                console.log(\"Arg 0: \" + args[0]);\n"
              + "            },\n"
              + "            onLeave: function(retval) {\n"
              + "                console.log(\"Return: \" + retval);\n"
              + "            }\n"
              + "        });\n"
              + "    });\n"
              + "}, 500);";

        assertEquals(expected, result.getScriptText());
    }

    // ========== Double-wrap prevention ==========

    @Test
    public void waitForLoadNotDoubleWrappedByCompositionOptions() {
        NativeSymbol symbol = new NativeSymbol.Builder()
                .libName("libnative.so")
                .exportName("func")
                .argCount(0)
                .waitForLoad(true)
                .setTimeoutMs(500)
                .build();
        composer.addRequest(HookRequest.nativeHook(symbol));

        // Even with wrapInPerform + setTimeout on CompositionOptions, the waitForLoad hook
        // should NOT be double-wrapped. Its own setTimeout (500ms) comes from the symbol,
        // while CompositionOptions' wrappers only apply to the wrapped section (which is empty here).
        CompositionOptions options = CompositionOptions.builder()
                .wrapInPerform(true)
                .setTimeoutMs(1000)
                .build();
        GeneratedScript result = composer.compose(options);
        String script = result.getScriptText();

        // Only ONE setTimeout (from the symbol's own 500ms, not the composition's 1000ms)
        assertEquals("Only one setTimeout from waitForLoad hook's own config",
                1, countOccurrences(script, "setTimeout(function() {"));
        assertTrue(script.contains("}, 500);"));
        assertFalse("Composition setTimeout should not apply to top-level hooks",
                script.contains("}, 1000);"));

        // No Java.perform wrapping on the waitForLoad hook
        assertFalse(script.contains("Java.perform"));
    }

    @Test
    public void waitForLoadHookIsolatedFromWrappedSection() {
        // Mix: 1 waitForLoad + 1 normal native hook with composition wrappers
        NativeSymbol waitSymbol = new NativeSymbol.Builder()
                .libName("libnative.so")
                .exportName("jni_func")
                .argCount(0)
                .waitForLoad(true)
                .build();
        composer.addRequest(HookRequest.nativeHook(waitSymbol));
        composer.addRequest(nativeExportRequest("libother.so", "other_func", 1));

        CompositionOptions options = CompositionOptions.builder()
                .wrapInPerform(true)
                .setTimeoutMs(300)
                .build();
        GeneratedScript result = composer.compose(options);
        String script = result.getScriptText();

        // waitForLoad section is NOT inside setTimeout or Java.perform
        int waitForLoadCallIdx = script.indexOf("waitForLibLoading(\"libnative.so\");");
        int setTimeoutIdx = script.indexOf("setTimeout(function() {");
        assertTrue("waitForLoad section before setTimeout",
                waitForLoadCallIdx < setTimeoutIdx);

        // The wrapped section has its own setTimeout (300ms) + Java.perform
        assertTrue(script.contains("}, 300);"));
        assertTrue(script.contains("Java.perform(function(){"));
        assertTrue(script.contains("Module.findExportByName(\"libother.so\", \"other_func\")"));
    }

    // ========== generateBody() contract tests ==========

    @Test
    public void generateBodyJavaProducesUnwrappedOutput() {
        JavaHookGenerator gen = new JavaHookGenerator();
        HookRequest request = javaRequest("com.Foo", "bar");

        GeneratedScript body = gen.generateBody(request);
        GeneratedScript full = gen.generate(request);

        // For Java hooks, generate() and generateBody() produce the same output
        assertEquals(full.getScriptText(), body.getScriptText());
        assertFalse(body.getScriptText().contains("Java.perform"));
    }

    @Test
    public void generateBodyNativeProducesRawInterceptor() {
        NativeHookGenerator gen = new NativeHookGenerator();
        NativeSymbol symbol = new NativeSymbol.Builder()
                .libName("libfoo.so")
                .exportName("func")
                .argCount(0)
                .setTimeoutMs(500)
                .waitForLoad(false)
                .build();
        HookRequest request = HookRequest.nativeHook(symbol);

        GeneratedScript body = gen.generateBody(request);

        // Body has no setTimeout wrapper
        assertFalse(body.getScriptText().contains("setTimeout"));
        // Body is just the Interceptor.attach block
        assertTrue(body.getScriptText().startsWith("Interceptor.attach("));
        assertTrue(body.getScriptText().endsWith("});"));
    }

    @Test
    public void generateBodyNativeWithWaitForLoadStillRaw() {
        NativeHookGenerator gen = new NativeHookGenerator();
        NativeSymbol symbol = new NativeSymbol.Builder()
                .libName("lib.so")
                .exportName("func")
                .argCount(0)
                .waitForLoad(true)
                .build();
        HookRequest request = HookRequest.nativeHook(symbol);

        GeneratedScript body = gen.generateBody(request);

        // Body has no waitForLoad wrapper
        assertFalse(body.getScriptText().contains("waitForLibLoading"));
        assertFalse(body.getScriptText().contains("onLibLoaded"));
        assertTrue(body.getScriptText().startsWith("Interceptor.attach("));
    }

    // ========== Helper methods ==========

    private HookRequest javaRequest(String className, String methodName) {
        SmaliMethod method = new SmaliMethod(className, methodName,
                Collections.emptyList(), "void");
        return HookRequest.java(method);
    }

    private HookRequest nativeExportRequest(String lib, String export, int args) {
        NativeSymbol symbol = NativeSymbol.export(lib, export, args);
        return HookRequest.nativeHook(symbol);
    }

    private int countOccurrences(String text, String search) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(search, idx)) != -1) {
            count++;
            idx += search.length();
        }
        return count;
    }
}
