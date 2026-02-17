package com.amrts.fridahelper.core.parser;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for {@link ParamTypeResolver}.
 */
public class ParamTypeResolverTest {

    private final ParamTypeResolver resolver = new ParamTypeResolver();

    @Test
    public void resolvePrimitives() {
        assertEquals("boolean", resolver.resolveSingle("Z"));
        assertEquals("byte", resolver.resolveSingle("B"));
        assertEquals("char", resolver.resolveSingle("C"));
        assertEquals("short", resolver.resolveSingle("S"));
        assertEquals("int", resolver.resolveSingle("I"));
        assertEquals("float", resolver.resolveSingle("F"));
        assertEquals("long", resolver.resolveSingle("J"));
        assertEquals("double", resolver.resolveSingle("D"));
        assertEquals("void", resolver.resolveSingle("V"));
    }

    @Test
    public void resolveObjectType() {
        assertEquals("java.lang.String", resolver.resolveSingle("Ljava/lang/String;"));
        assertEquals("com.example.Foo", resolver.resolveSingle("Lcom/example/Foo;"));
    }

    @Test
    public void resolveArrayTypes() {
        assertEquals("int[]", resolver.resolveSingle("[I"));
        assertEquals("byte[]", resolver.resolveSingle("[B"));
        assertEquals("java.lang.String[]", resolver.resolveSingle("[Ljava/lang/String;"));
        assertEquals("int[][]", resolver.resolveSingle("[[I"));
    }

    @Test
    public void resolveAllMixed() {
        List<String> result = resolver.resolveAll("ILjava/lang/String;Z");
        assertEquals(Arrays.asList("int", "java.lang.String", "boolean"), result);
    }

    @Test
    public void resolveAllEmpty() {
        assertEquals(Collections.emptyList(), resolver.resolveAll(""));
        assertEquals(Collections.emptyList(), resolver.resolveAll(null));
    }

    @Test
    public void resolveSingleEmpty() {
        assertEquals("", resolver.resolveSingle(""));
        assertEquals("", resolver.resolveSingle(null));
    }
}
