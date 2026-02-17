package com.amrts.fridahelper.core.parser;

import com.amrts.fridahelper.core.model.SmaliMethod;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Tests for {@link SmaliSignatureParser}.
 * Each test documents the exact input and expected output.
 */
public class SmaliSignatureParserTest {

    private final SmaliSignatureParser parser = new SmaliSignatureParser();

    @Test
    public void parseMethodWithMultipleParams() {
        // Sample smali: class com.example.Foo, method bar, params (int, String), returns void
        String input = "Lcom/example/Foo;->bar(ILjava/lang/String;)V";
        SmaliMethod result = parser.parse(input);

        assertEquals("com.example.Foo", result.getClassName());
        assertEquals("bar", result.getMethodName());
        assertEquals(Arrays.asList("int", "java.lang.String"), result.getParamTypes());
        assertEquals("void", result.getReturnType());
    }

    @Test
    public void parseMethodWithNoParams() {
        String input = "Lcom/example/Foo;->getName()Ljava/lang/String;";
        SmaliMethod result = parser.parse(input);

        assertEquals("com.example.Foo", result.getClassName());
        assertEquals("getName", result.getMethodName());
        assertEquals(Collections.emptyList(), result.getParamTypes());
        assertEquals("java.lang.String", result.getReturnType());
    }

    @Test
    public void parseConstructor() {
        String input = "Lcom/example/Foo;-><init>(Ljava/lang/String;I)V";
        SmaliMethod result = parser.parse(input);

        assertEquals("com.example.Foo", result.getClassName());
        assertEquals("<init>", result.getMethodName());
        assertEquals(Arrays.asList("java.lang.String", "int"), result.getParamTypes());
        assertEquals("void", result.getReturnType());
    }

    @Test
    public void parseWithArrayParams() {
        String input = "Lcom/example/Foo;->process([B[Ljava/lang/String;)V";
        SmaliMethod result = parser.parse(input);

        assertEquals("com.example.Foo", result.getClassName());
        assertEquals("process", result.getMethodName());
        assertEquals(Arrays.asList("byte[]", "java.lang.String[]"), result.getParamTypes());
        assertEquals("void", result.getReturnType());
    }

    @Test
    public void parseWithAllPrimitiveTypes() {
        String input = "Lcom/example/Foo;->allPrims(ZBCSIJFD)V";
        SmaliMethod result = parser.parse(input);

        assertEquals(Arrays.asList("boolean", "byte", "char", "short", "int", "long", "float", "double"),
                result.getParamTypes());
    }

    @Test
    public void parseDeepPackageName() {
        String input = "Lcom/amrts/fridahelper/core/model/SmaliMethod;->getClassName()Ljava/lang/String;";
        SmaliMethod result = parser.parse(input);

        assertEquals("com.amrts.fridahelper.core.model.SmaliMethod", result.getClassName());
        assertEquals("getClassName", result.getMethodName());
    }

    @Test
    public void parseInnerClass() {
        String input = "Lcom/foo/Bar$Inner;->doWork(I)V";
        SmaliMethod result = parser.parse(input);

        assertEquals("com.foo.Bar$Inner", result.getClassName());
        assertEquals("doWork", result.getMethodName());
        assertEquals(Arrays.asList("int"), result.getParamTypes());
    }

    @Test
    public void parseSingleLetterClass() {
        String input = "La/b/c;->a(I)V";
        SmaliMethod result = parser.parse(input);

        assertEquals("a.b.c", result.getClassName());
        assertEquals("a", result.getMethodName());
    }

    @Test
    public void parseObjectArrayParam() {
        String input = "Lcom/example/Foo;->test([Ljava/lang/String;)V";
        SmaliMethod result = parser.parse(input);

        assertEquals(Arrays.asList("java.lang.String[]"), result.getParamTypes());
    }

    @Test
    public void parseMultiDimensionalArray() {
        String input = "Lcom/example/Foo;->test([[I)V";
        SmaliMethod result = parser.parse(input);

        assertEquals(Arrays.asList("int[][]"), result.getParamTypes());
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseNullInputThrows() {
        parser.parse(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseEmptyInputThrows() {
        parser.parse("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseInvalidFormatThrows() {
        parser.parse("this is not smali");
    }
}
