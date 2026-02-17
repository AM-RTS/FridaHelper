package com.amrts.fridahelper.core.generator;

import com.amrts.fridahelper.core.model.GeneratedScript;
import com.amrts.fridahelper.core.model.HookRequest;
import com.amrts.fridahelper.core.model.NativeSymbol;
import com.amrts.fridahelper.core.model.SmaliMethod;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Tests for {@link ScriptWrapper}.
 * Verifies wrapping behavior for Java.perform, setTimeout, and both hook types.
 */
public class ScriptWrapperTest {

    private final JavaHookGenerator javaGen = new JavaHookGenerator();
    private final NativeHookGenerator nativeGen = new NativeHookGenerator();

    @Test
    public void wrapIfNeeded_wrapsJavaHook() {
        SmaliMethod method = new SmaliMethod("com.Foo", "bar", Collections.emptyList(), "void");
        GeneratedScript snippet = javaGen.generate(HookRequest.java(method));

        GeneratedScript wrapped = ScriptWrapper.wrapIfNeeded(snippet);

        assertTrue(wrapped.getScriptText().startsWith("Java.perform(function(){"));
        assertTrue(wrapped.getScriptText().endsWith("});"));
        assertEquals(HookRequest.Type.JAVA, wrapped.getHookType());
    }

    @Test
    public void wrapIfNeeded_doesNotWrapNativeHook() {
        NativeSymbol symbol = NativeSymbol.export("libfoo.so", "func", 1);
        GeneratedScript script = nativeGen.generate(HookRequest.nativeHook(symbol));

        GeneratedScript afterWrap = ScriptWrapper.wrapIfNeeded(script);

        assertSame(script, afterWrap);
        assertFalse(afterWrap.getScriptText().contains("Java.perform"));
    }

    @Test
    public void wrapInJavaPerform_wrapsNativeHookExplicitly() {
        NativeSymbol symbol = NativeSymbol.export("libfoo.so", "func", 0);
        GeneratedScript script = nativeGen.generate(HookRequest.nativeHook(symbol));

        GeneratedScript wrapped = ScriptWrapper.wrapInJavaPerform(script);

        assertTrue(wrapped.getScriptText().startsWith("Java.perform(function(){"));
        assertEquals(HookRequest.Type.NATIVE, wrapped.getHookType());
    }

    @Test
    public void wrapIfNeeded_preservesHookType() {
        SmaliMethod method = new SmaliMethod("com.Foo", "bar",
                Arrays.asList("int"), "void");
        GeneratedScript snippet = javaGen.generate(HookRequest.java(method));

        GeneratedScript wrapped = ScriptWrapper.wrapIfNeeded(snippet);

        assertEquals(HookRequest.Type.JAVA, wrapped.getHookType());
    }

    @Test
    public void wrapInSetTimeout_wrapsScript() {
        NativeSymbol symbol = NativeSymbol.export("lib.so", "test", 0);
        GeneratedScript script = nativeGen.generate(HookRequest.nativeHook(symbol));

        GeneratedScript wrapped = ScriptWrapper.wrapInSetTimeout(script, 500);

        assertTrue(wrapped.getScriptText().startsWith("setTimeout(function() {"));
        assertTrue(wrapped.getScriptText().endsWith("}, 500);"));
        assertTrue(wrapped.getScriptText().contains("Interceptor.attach"));
    }

    @Test
    public void wrapInSetTimeout_zeroDelayReturnsUnchanged() {
        NativeSymbol symbol = NativeSymbol.export("lib.so", "test", 0);
        GeneratedScript script = nativeGen.generate(HookRequest.nativeHook(symbol));

        GeneratedScript result = ScriptWrapper.wrapInSetTimeout(script, 0);

        assertSame(script, result);
    }

    @Test
    public void wrapInSetTimeout_negativeDelayReturnsUnchanged() {
        NativeSymbol symbol = NativeSymbol.export("lib.so", "test", 0);
        GeneratedScript script = nativeGen.generate(HookRequest.nativeHook(symbol));

        GeneratedScript result = ScriptWrapper.wrapInSetTimeout(script, -100);

        assertSame(script, result);
    }
}
