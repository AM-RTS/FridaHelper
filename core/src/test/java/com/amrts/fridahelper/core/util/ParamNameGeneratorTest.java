package com.amrts.fridahelper.core.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link ParamNameGenerator}.
 */
public class ParamNameGeneratorTest {

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
}
