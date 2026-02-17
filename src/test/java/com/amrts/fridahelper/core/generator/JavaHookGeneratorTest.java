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
                "var cls = Java.use(\"com.example.Foo\");\n"
              + " cls.bar.overload(\"int\", \"java.lang.String\").implementation = function(a, b){\n"
              + "\t\tconsole.log(\"Param 1: \" + a);\n"
              + "console.log(\"Param 2: \" + b);\n"
              + " var retval = this.bar(a, b);\n"
              + "\t\tconsole.log(\"Return Value: \" + retval);\n"
              + "\t//console.log(Java.use(\"android.util.Log\").getStackTraceString(Java.use(\"java.lang.Exception\").$new())),\n"
              + " return retval;\n"
              + "   }";

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
        assertFalse(script.contains("console.log(\"Param"));
    }

    /**
     * Constructor (<init>) should produce .$init access.
     */
    @Test
    public void generateConstructorHook() {
        SmaliMethod method = new SmaliMethod(
                "com.example.Foo", "<init>",
                Arrays.asList("java.lang.String"), "void");
        HookRequest request = HookRequest.java(method);

        GeneratedScript result = generator.generate(request);

        String script = result.getScriptText();
        assertTrue(script.contains("cls.$init.overload"));
        assertTrue(script.contains("this.$init(a)"));
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
     * Double-wrap safety: wrapping an already-wrapped script should add another layer,
     * but wrapIfNeeded should only wrap once (since it checks hookType, not content).
     * This test documents the behavior.
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

        // wrapIfNeeded checks hookType (still JAVA), so it WILL wrap again.
        // This is expected: caller is responsible for not calling it twice.
        // The test documents this behavior explicitly.
        assertTrue(wrapped2.getScriptText().startsWith("Java.perform(function(){\n  Java.perform("));
    }

    /**
     * Backward-compatibility snapshot: the exact output that FridaHelper 2.0 produced
     * for the input "Lcom/example/Foo;->bar(ILjava/lang/String;)V" in full-script mode.
     *
     * This is the canonical regression test. If this breaks, backward compat is lost.
     */
    @Test
    public void backwardCompatibilitySnapshot_FullScript() {
        // Simulate the exact flow the old tool did:
        // 1. Parse smali signature
        SmaliSignatureParser parser = new SmaliSignatureParser();
        SmaliMethod method = parser.parse("Lcom/example/Foo;->bar(ILjava/lang/String;)V");

        // 2. Generate snippet
        HookRequest request = HookRequest.java(method);
        GeneratedScript snippet = generator.generate(request);

        // 3. Wrap in Java.perform (full script mode)
        GeneratedScript fullScript = ScriptWrapper.wrapIfNeeded(snippet);

        String expected =
                "Java.perform(function(){\n"
              + "  var cls = Java.use(\"com.example.Foo\");\n"
              + " cls.bar.overload(\"int\", \"java.lang.String\").implementation = function(a, b){\n"
              + "\t\tconsole.log(\"Param 1: \" + a);\n"
              + "console.log(\"Param 2: \" + b);\n"
              + " var retval = this.bar(a, b);\n"
              + "\t\tconsole.log(\"Return Value: \" + retval);\n"
              + "\t//console.log(Java.use(\"android.util.Log\").getStackTraceString(Java.use(\"java.lang.Exception\").$new())),\n"
              + " return retval;\n"
              + "   }\n"
              + "});";

        assertEquals(expected, fullScript.getScriptText());
    }

    /**
     * Backward-compatibility snapshot: snippet mode (no Java.perform wrapper).
     */
    @Test
    public void backwardCompatibilitySnapshot_Snippet() {
        SmaliSignatureParser parser = new SmaliSignatureParser();
        SmaliMethod method = parser.parse("Lcom/example/Foo;->getName()Ljava/lang/String;");

        HookRequest request = HookRequest.java(method);
        GeneratedScript snippet = generator.generate(request);

        String expected =
                "var cls = Java.use(\"com.example.Foo\");\n"
              + " cls.getName.overload().implementation = function(){\n"
              + " var retval = this.getName();\n"
              + "\t\tconsole.log(\"Return Value: \" + retval);\n"
              + "\t//console.log(Java.use(\"android.util.Log\").getStackTraceString(Java.use(\"java.lang.Exception\").$new())),\n"
              + " return retval;\n"
              + "   }";

        assertEquals(expected, snippet.getScriptText());
    }

    /**
     * Wrong request type should throw.
     */
    @Test(expected = IllegalArgumentException.class)
    public void generateWithNativeRequestThrows() {
        HookRequest request = HookRequest.nativeHook(
                new com.amrts.fridahelper.core.model.NativeSymbol("lib.so", "func", 0));
        generator.generate(request);
    }
}
