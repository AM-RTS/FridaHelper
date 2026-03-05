package com.amrts.fridahelper.core.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Tests for {@link ParamNameGenerator}.
 */
public class ParamNameGeneratorTest {

    // ========== Simple mode (count-based) ==========

    @Test
    public void generateZero() {
        assertEquals("", ParamNameGenerator.generate(0));
    }

    @Test
    public void generateOne() {
        assertEquals("a", ParamNameGenerator.generate(1));
    }

    @Test
    public void generateThree() {
        assertEquals("a, b, c", ParamNameGenerator.generate(3));
    }

    @Test
    public void generateTwentySix() {
        String result = ParamNameGenerator.generate(26);
        assertTrue(result.startsWith("a, b, c"));
        assertTrue(result.endsWith("z"));
        assertEquals(26, result.split(", ").length);
    }

    @Test
    public void generateOverTwentySixWraps() {
        String result = ParamNameGenerator.generate(27);
        assertTrue(result.endsWith("a0"));
        assertEquals(27, result.split(", ").length);
    }

    @Test
    public void nameAtBasic() {
        assertEquals("a", ParamNameGenerator.nameAt(0));
        assertEquals("z", ParamNameGenerator.nameAt(25));
        assertEquals("a0", ParamNameGenerator.nameAt(26));
        assertEquals("b0", ParamNameGenerator.nameAt(27));
    }

    // ========== Type-aware mode ==========

    @Test
    public void typeAware_singleInt() {
        assertEquals("i", ParamNameGenerator.generate(Arrays.asList("int")));
    }

    @Test
    public void typeAware_singleString() {
        assertEquals("str", ParamNameGenerator.generate(Arrays.asList("java.lang.String")));
    }

    @Test
    public void typeAware_mixedUnique() {
        assertEquals("i, str", ParamNameGenerator.generate(
                Arrays.asList("int", "java.lang.String")));
    }

    @Test
    public void typeAware_duplicateInts() {
        assertEquals("i1, i2, i3", ParamNameGenerator.generate(
                Arrays.asList("int", "int", "int")));
    }

    @Test
    public void typeAware_duplicateStrings() {
        assertEquals("str1, str2", ParamNameGenerator.generate(
                Arrays.asList("java.lang.String", "java.lang.String")));
    }

    @Test
    public void typeAware_booleanAndLong() {
        assertEquals("b, l", ParamNameGenerator.generate(
                Arrays.asList("boolean", "long")));
    }

    @Test
    public void typeAware_unknownFallsBackToAbc() {
        assertEquals("a", ParamNameGenerator.generate(
                Arrays.asList("com.example.a")));
    }

    @Test
    public void typeAware_mixedKnownAndUnknown() {
        assertEquals("i, a, str", ParamNameGenerator.generate(
                Arrays.asList("int", "com.example.x", "java.lang.String")));
    }

    @Test
    public void typeAware_knownClassType() {
        assertEquals("context", ParamNameGenerator.generate(
                Arrays.asList("android.content.Context")));
    }

    @Test
    public void typeAware_multipleKnownClassType() {
        assertEquals("view1, view2", ParamNameGenerator.generate(
                Arrays.asList("android.view.View", "android.view.View")));
    }

    @Test
    public void typeAware_emptyList() {
        assertEquals("", ParamNameGenerator.generate(Collections.emptyList()));
    }

    @Test
    public void typeAware_nullList() {
        assertEquals("", ParamNameGenerator.generate((java.util.List<String>) null));
    }

    @Test
    public void typeAware_objectType() {
        assertEquals("obj", ParamNameGenerator.generate(
                Arrays.asList("java.lang.Object")));
    }

    @Test
    public void typeAware_byteAndChar() {
        assertEquals("by, ch", ParamNameGenerator.generate(
                Arrays.asList("byte", "char")));
    }

    @Test
    public void typeAware_floatAndDouble() {
        assertEquals("f, d", ParamNameGenerator.generate(
                Arrays.asList("float", "double")));
    }
}
