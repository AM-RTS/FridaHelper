package com.amrts.fridahelper.core.generator;

import com.amrts.fridahelper.core.model.GeneratedScript;
import com.amrts.fridahelper.core.model.HookRequest;
import com.amrts.fridahelper.core.model.SmaliMethod;
import com.amrts.fridahelper.core.parser.SmaliSignatureParser;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Tests for {@link JavaHookGenerator}.
 * Each test includes the exact expected output for verification.
 */
public class JavaHookGeneratorTest {

    private final JavaHookGenerator generator = new JavaHookGenerator();

    /**
     * Standard test: method with 2 params.
     * Input smali equivalent: Lcom/example/Foo;->bar(ILjava/lang/String;)V
     * Expected: var foo (from "Foo"), params i (int), str (String)
     */
    @Test
    public void generateWithTwoParams() {
        SmaliMethod method = new SmaliMethod(
                "com.example.Foo", "bar",
                Arrays.asList("int", "java.lang.String"), "void");
        HookRequest request = HookRequest.java(method);

        GeneratedScript result = generator.generate(request);

        assertEquals(HookRequest.Type.JAVA, result.getHookType());

        String expected =
                "var foo = Java.use(\"com.example.Foo\");\n"
              + "foo.bar.overload(\"int\", \"java.lang.String\").implementation = function(i, str){\n"
              + "    this.bar(i, str);\n"
              + "    console.log(`Foo.bar(${i}, ${str})`);\n"
              + "}";

        assertEquals(expected, result.getScriptText());
    }

    /**
     * No-param method test.
     */
    @Test
    public void generateWithNoParams() {
        SmaliMethod method = new SmaliMethod(
                "com.example.Foo", "getName",
                Collections.emptyList(), "java.lang.String");
        HookRequest request = HookRequest.java(method);

        GeneratedScript result = generator.generate(request);

        String script = result.getScriptText();
        assertTrue(script.contains(".overload()"));
        assertTrue(script.contains("function()"));
        assertTrue(script.contains("console.log(`Foo.getName() => ${retval}`)")); 
    }

    /**
     * Constructor (<init>) should produce .$init access.
     * Class "Foo" (3 chars) → var foo.
     */
    @Test
    public void generateConstructorHook() {
        SmaliMethod method = new SmaliMethod(
                "com.example.Foo", "<init>",
                Arrays.asList("java.lang.String"), "void");
        HookRequest request = HookRequest.java(method);

        GeneratedScript result = generator.generate(request);

        String script = result.getScriptText();
        assertTrue(script.contains("foo.$init.overload"));
        assertTrue(script.contains("this.$init(str)"));
    }

    /**
     * Script wrapping test: snippet vs full script.
     */
    @Test
    public void wrapInJavaPerform() {
        SmaliMethod method = new SmaliMethod(
                "com.example.Foo", "bar",
                Collections.emptyList(), "void");
        HookRequest request = HookRequest.java(method);

        GeneratedScript snippet = generator.generate(request);
        assertFalse(snippet.getScriptText().contains("Java.perform"));

        GeneratedScript fullScript = ScriptWrapper.wrapIfNeeded(snippet);
        assertTrue(fullScript.getScriptText().startsWith("Java.perform(function(){"));
        assertTrue(fullScript.getScriptText().endsWith("});"));
    }

    /**
     * Double-wrap behavior documentation test.
     */
    @Test
    public void doubleWrapBehavior() {
        SmaliMethod method = new SmaliMethod(
                "com.example.Foo", "bar",
                Collections.emptyList(), "void");
        HookRequest request = HookRequest.java(method);

        GeneratedScript snippet = generator.generate(request);
        GeneratedScript wrapped1 = ScriptWrapper.wrapIfNeeded(snippet);
        GeneratedScript wrapped2 = ScriptWrapper.wrapIfNeeded(wrapped1);

        assertTrue(wrapped2.getScriptText().startsWith("Java.perform(function(){\n    Java.perform("));
    }

    /**
     * Snapshot: full-script mode with proper indentation.
     * Every line inside Java.perform is indented by 4 spaces.
     */
    @Test
    public void snapshot_FullScript() {
        SmaliSignatureParser parser = new SmaliSignatureParser();
        SmaliMethod method = parser.parse("Lcom/example/Foo;->bar(ILjava/lang/String;)V");

        HookRequest request = HookRequest.java(method);
        GeneratedScript snippet = generator.generate(request);
        GeneratedScript fullScript = ScriptWrapper.wrapIfNeeded(snippet);

        String expected =
                "Java.perform(function(){\n"
              + "    var foo = Java.use(\"com.example.Foo\");\n"
              + "    foo.bar.overload(\"int\", \"java.lang.String\").implementation = function(i, str){\n"
              + "        this.bar(i, str);\n"
              + "        console.log(`Foo.bar(${i}, ${str})`);\n"
              + "    }\n"
              + "});";

        assertEquals(expected, fullScript.getScriptText());
    }

    /**
     * Snapshot: snippet mode (no Java.perform wrapper).
     */
    @Test
    public void snapshot_Snippet() {
        SmaliSignatureParser parser = new SmaliSignatureParser();
        SmaliMethod method = parser.parse("Lcom/example/Foo;->getName()Ljava/lang/String;");

        HookRequest request = HookRequest.java(method);
        GeneratedScript snippet = generator.generate(request);

        String expected =
                "var foo = Java.use(\"com.example.Foo\");\n"
              + "foo.getName.overload().implementation = function(){\n"
              + "    var retval = this.getName();\n"
              + "    console.log(`Foo.getName() => ${retval}`);\n"
              + "    return retval;\n"
              + "}";

        assertEquals(expected, snippet.getScriptText());
    }

    /**
     * Obfuscated / short class name falls back to "cls".
     */
    @Test
    public void obfuscatedClassNameFallsToCls() {
        SmaliMethod method = new SmaliMethod(
                "com.example.a", "b",
                Collections.emptyList(), "void");
        HookRequest request = HookRequest.java(method);

        GeneratedScript result = generator.generate(request);
        String script = result.getScriptText();
        assertTrue("Short class 'a' should use sanitized name", script.contains("var example_a = Java.use(\"com.example.a\")"));
    }

    /**
     * Duplicate param types get numbered: i1, i2.
     */
    @Test
    public void duplicateParamTypesGetNumbered() {
        SmaliMethod method = new SmaliMethod(
                "com.example.Foo", "add",
                Arrays.asList("int", "int"), "int");
        HookRequest request = HookRequest.java(method);

        GeneratedScript result = generator.generate(request);
        String script = result.getScriptText();
        assertTrue(script.contains("function(i1, i2)"));
    }

    /**
     * Unknown param type falls back to a, b, c.
     */
    @Test
    public void unknownParamTypeFallsBackToAbc() {
        SmaliMethod method = new SmaliMethod(
                "com.example.Foo", "run",
                Arrays.asList("com.example.a"), "void");
        HookRequest request = HookRequest.java(method);

        GeneratedScript result = generator.generate(request);
        String script = result.getScriptText();
        assertTrue("Short/obfuscated type falls back to sequential", script.contains("function(a)"));
    }

    /**
     * Mixed known and unknown types: known get type-aware names, unknown get fallback.
     */
    @Test
    public void mixedKnownAndUnknownTypes() {
        SmaliMethod method = new SmaliMethod(
                "com.example.Handler", "process",
                Arrays.asList("int", "com.example.x", "java.lang.String"), "void");
        HookRequest request = HookRequest.java(method);

        GeneratedScript result = generator.generate(request);
        String script = result.getScriptText();
        assertTrue(script.contains("function(i, a, str)"));
    }

    /**
     * Void method: no retval capture, no return value log, no return statement.
     */
    @Test
    public void voidMethodOmitsReturnValue() {
        SmaliMethod method = new SmaliMethod(
                "com.example.Foo", "doWork",
                Arrays.asList("int"), "void");
        HookRequest request = HookRequest.java(method);

        GeneratedScript result = generator.generate(request);
        String script = result.getScriptText();

        assertTrue(script.contains("this.doWork(i);"));
        assertTrue(script.contains("console.log(`Foo.doWork(${i})`)"));
        assertFalse(script.contains("var retval"));
        assertFalse(script.contains("=> ${retval}"));
        assertFalse(script.contains("return retval"));
    }

    /**
     * Non-void method: captures retval, logs it, returns it.
     */
    @Test
    public void nonVoidMethodCapturesReturnValue() {
        SmaliMethod method = new SmaliMethod(
                "com.example.Foo", "compute",
                Arrays.asList("int"), "int");
        HookRequest request = HookRequest.java(method);

        GeneratedScript result = generator.generate(request);
        String script = result.getScriptText();

        assertTrue(script.contains("var retval = this.compute(i);"));
        assertTrue(script.contains("console.log(`Foo.compute(${i}) => ${retval}`)"));
        assertTrue(script.contains("return retval;"));
    }

    /**
     * Wrong request type should throw.
     */
    @Test(expected = IllegalArgumentException.class)
    public void generateWithNativeRequestThrows() {
        HookRequest request = HookRequest.nativeHook(
                com.amrts.fridahelper.core.model.NativeSymbol.export("lib.so", "func", 0));
        generator.generate(request);
    }

    /**
     * Class var derivation: various cases.
     */
    @Test
    public void deriveClassVariable_normal() {
        assertEquals("networkManager", JavaHookGenerator.deriveClassVariable("com.example.NetworkManager"));
        assertEquals("foo", JavaHookGenerator.deriveClassVariable("com.example.Foo"));
        assertEquals("app", JavaHookGenerator.deriveClassVariable("com.example.App"));
    }

    @Test
    public void deriveClassVariable_obfuscatedOrShort() {
        assertEquals("example_a", JavaHookGenerator.deriveClassVariable("com.example.a"));
        assertEquals("example_b0", JavaHookGenerator.deriveClassVariable("com.example.b0"));
        assertEquals("a", JavaHookGenerator.deriveClassVariable("a"));
    }

    @Test
    public void deriveClassVariable_deepPackageObfuscated() {
        assertEquals("for_hm", JavaHookGenerator.deriveClassVariable("com.long.clazz.name.this.is.for.hm"));
        assertEquals("am_h", JavaHookGenerator.deriveClassVariable("am.h"));
        assertEquals("A2_A", JavaHookGenerator.deriveClassVariable("A2.A"));
    }

    @Test
    public void deriveClassVariable_edgeCases() {
        assertEquals("cls", JavaHookGenerator.deriveClassVariable(null));
        assertEquals("cls", JavaHookGenerator.deriveClassVariable(""));
    }
}
