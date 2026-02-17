package com.amrts.fridahelper.core.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link ObfuscationDetector}.
 */
public class ObfuscationDetectorTest {

    @Test
    public void normalNameNotObfuscated() {
        assertFalse(ObfuscationDetector.isObfuscated("bar"));
        assertFalse(ObfuscationDetector.isObfuscated("onCreate"));
        assertFalse(ObfuscationDetector.isObfuscated("a"));
    }

    @Test
    public void highCodePointIsObfuscated() {
        // Code point 320 = U+0140 (latin small letter l with middle dot)
        assertTrue(ObfuscationDetector.isObfuscated("\u0140"));
        // Common obfuscator output
        assertTrue(ObfuscationDetector.isObfuscated("\u0430\u0431")); // Cyrillic а, б
    }

    @Test
    public void nullAndEmptyReturnFalse() {
        assertFalse(ObfuscationDetector.isObfuscated(null));
        assertFalse(ObfuscationDetector.isObfuscated(""));
    }

    @Test
    public void initMethodNotObfuscated() {
        assertFalse(ObfuscationDetector.isObfuscated("<init>"));
    }
}
