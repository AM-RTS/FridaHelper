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
        assertTrue(ObfuscationDetector.isObfuscated("\u0140"));
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

    // ========== isUnsuitableForVariable ==========

    @Test
    public void shortNamesAreUnsuitable() {
        assertTrue(ObfuscationDetector.isUnsuitableForVariable("a"));
        assertTrue(ObfuscationDetector.isUnsuitableForVariable("b0"));
    }

    @Test
    public void threeCharNamesAreSuitable() {
        assertFalse(ObfuscationDetector.isUnsuitableForVariable("App"));
        assertFalse(ObfuscationDetector.isUnsuitableForVariable("Foo"));
        assertFalse(ObfuscationDetector.isUnsuitableForVariable("Log"));
    }

    @Test
    public void obfuscatedNameIsUnsuitable() {
        assertTrue(ObfuscationDetector.isUnsuitableForVariable("\u0430\u0431\u0432"));
    }

    @Test
    public void nullAndEmptyAreUnsuitable() {
        assertTrue(ObfuscationDetector.isUnsuitableForVariable(null));
        assertTrue(ObfuscationDetector.isUnsuitableForVariable(""));
    }

    @Test
    public void normalLongNamesAreSuitable() {
        assertFalse(ObfuscationDetector.isUnsuitableForVariable("NetworkManager"));
        assertFalse(ObfuscationDetector.isUnsuitableForVariable("Utils"));
    }
}
