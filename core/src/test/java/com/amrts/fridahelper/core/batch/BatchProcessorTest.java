package com.amrts.fridahelper.core.batch;

import com.amrts.fridahelper.core.generator.CompositionOptions;
import com.amrts.fridahelper.core.model.GeneratedScript;
import com.amrts.fridahelper.core.model.HookRequest;

import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

public class BatchProcessorTest {

    private final BatchProcessor processor = new BatchProcessor();

    private Path getTestSmaliDir() throws URISyntaxException {
        return Paths.get(getClass().getClassLoader()
                .getResource("smali").toURI());
    }

    // --- Full directory processing ---

    @Test
    public void processDirectory_generatesScript() throws Exception {
        Path dir = getTestSmaliDir();
        BatchFilter filter = BatchFilter.builder()
                .skipConstructors(true)
                .build();

        GeneratedScript result = processor.process(
                dir, filter, CompositionOptions.withPerform());

        assertNotNull(result);
        String script = result.getScriptText();
        assertEquals(HookRequest.Type.JAVA, result.getHookType());

        assertTrue("Should contain Java.perform wrapper",
                script.contains("Java.perform(function(){"));

        // LoginManager methods (excluding constructor and synthetic)
        assertTrue("Should hook authenticate",
                script.contains("authenticate"));
        assertTrue("Should hook logout",
                script.contains("logout"));
        assertTrue("Should hook native getStatus",
                script.contains("getStatus"));

        // NetworkClient methods (excluding constructor and bridge synthetic)
        assertTrue("Should hook sendRequest",
                script.contains("sendRequest"));
        assertTrue("Should hook getBaseUrl",
                script.contains("getBaseUrl"));

        // Should NOT contain synthetic/bridge
        assertFalse("Should not contain synthetic access$000",
                script.contains("access$000"));
        assertFalse("Should not contain bridge access$100",
                script.contains("access$100"));

        // Should NOT contain abstract interface methods
        assertFalse("Should not contain abstract start",
                script.contains("IService") && script.contains(".start"));
    }

    @Test
    public void processDirectory_withClassFilter_onlyMatchingClasses() throws Exception {
        Path dir = getTestSmaliDir();
        BatchFilter filter = BatchFilter.builder()
                .skipConstructors(true)
                .includeClasses(".*LoginManager")
                .build();

        GeneratedScript result = processor.process(
                dir, filter, CompositionOptions.withPerform());

        String script = result.getScriptText();
        assertTrue(script.contains("LoginManager"));
        assertFalse("Should not contain NetworkClient",
                script.contains("NetworkClient"));
    }

    @Test
    public void processDirectory_allAbstractClass_returnsEmpty() throws Exception {
        BatchFilter filter = BatchFilter.acceptAll();
        List<SmaliMethodEntry> entries = Arrays.asList(
                new SmaliMethodEntry("Lcom/example/IFoo;", "doWork",
                        "Lcom/example/IFoo;->doWork()V",
                        new HashSet<>(Arrays.asList("public", "abstract"))),
                new SmaliMethodEntry("Lcom/example/IFoo;", "stop",
                        "Lcom/example/IFoo;->stop()V",
                        new HashSet<>(Arrays.asList("public", "abstract")))
        );

        GeneratedScript result = processor.processEntries(
                entries, filter, CompositionOptions.withPerform());

        assertEquals("// No hookable methods found", result.getScriptText());
    }

    @Test
    public void processEntries_emptyList_returnsComment() {
        GeneratedScript result = processor.processEntries(
                Collections.<SmaliMethodEntry>emptyList(),
                BatchFilter.acceptAll(),
                CompositionOptions.withPerform());

        assertEquals("// No hookable methods found", result.getScriptText());
    }

    @Test
    public void processEntries_withMethodExclude() {
        List<SmaliMethodEntry> entries = Arrays.asList(
                new SmaliMethodEntry("Lcom/example/Foo;", "bar",
                        "Lcom/example/Foo;->bar()V",
                        new HashSet<>(Collections.singletonList("public"))),
                new SmaliMethodEntry("Lcom/example/Foo;", "toString",
                        "Lcom/example/Foo;->toString()Ljava/lang/String;",
                        new HashSet<>(Collections.singletonList("public")))
        );

        BatchFilter filter = BatchFilter.builder()
                .excludeMethods("toString|hashCode|equals")
                .build();

        GeneratedScript result = processor.processEntries(
                entries, filter, CompositionOptions.none());

        String script = result.getScriptText();
        assertTrue(script.contains("bar"));
        assertFalse(script.contains("toString"));
    }

    // --- Null safety ---

    @Test(expected = IllegalArgumentException.class)
    public void processNullDirectory_throws() throws IOException {
        processor.process(null, BatchFilter.acceptAll(), CompositionOptions.none());
    }

    @Test(expected = IllegalArgumentException.class)
    public void processNullFilter_throws() throws IOException {
        processor.process(Paths.get("."), null, CompositionOptions.none());
    }

    @Test(expected = IllegalArgumentException.class)
    public void processNullOptions_throws() throws IOException {
        processor.process(Paths.get("."), BatchFilter.acceptAll(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void processEntriesNullEntries_throws() {
        processor.processEntries(null, BatchFilter.acceptAll(), CompositionOptions.none());
    }
}
