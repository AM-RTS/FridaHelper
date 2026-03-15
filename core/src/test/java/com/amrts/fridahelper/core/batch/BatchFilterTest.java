package com.amrts.fridahelper.core.batch;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class BatchFilterTest {

    private static SmaliMethodEntry entry(String classDesc, String methodName,
                                          String signature, String... flags) {
        Set<String> flagSet = new HashSet<>(Arrays.asList(flags));
        return new SmaliMethodEntry(classDesc, methodName, signature, flagSet);
    }

    // --- Hard rules (always enforced) ---

    @Test
    public void abstractAlwaysRejected() {
        BatchFilter filter = BatchFilter.acceptAll();
        SmaliMethodEntry entry = entry("Lcom/example/Foo;", "doWork",
                "Lcom/example/Foo;->doWork()V", "public", "abstract");

        assertFalse(filter.accepts(entry));
    }

    @Test
    public void syntheticAlwaysRejected() {
        BatchFilter filter = BatchFilter.acceptAll();
        SmaliMethodEntry entry = entry("Lcom/example/Foo;", "access$000",
                "Lcom/example/Foo;->access$000()V", "private", "synthetic");

        assertFalse(filter.accepts(entry));
    }

    @Test
    public void bridgeAlwaysRejected() {
        BatchFilter filter = BatchFilter.acceptAll();
        SmaliMethodEntry entry = entry("Lcom/example/Foo;", "access$100",
                "Lcom/example/Foo;->access$100()V", "public", "static", "bridge", "synthetic");

        assertFalse(filter.accepts(entry));
    }

    @Test
    public void nullEntryRejected() {
        assertFalse(BatchFilter.acceptAll().accepts(null));
    }

    // --- Normal methods pass through ---

    @Test
    public void normalMethodAccepted() {
        BatchFilter filter = BatchFilter.acceptAll();
        SmaliMethodEntry entry = entry("Lcom/example/Foo;", "bar",
                "Lcom/example/Foo;->bar(I)V", "public");

        assertTrue(filter.accepts(entry));
    }

    @Test
    public void nativeMethodAccepted() {
        BatchFilter filter = BatchFilter.acceptAll();
        SmaliMethodEntry entry = entry("Lcom/example/Foo;", "getStatus",
                "Lcom/example/Foo;->getStatus()Z", "public", "static", "native");

        assertTrue("Native methods should be hookable via Java.use", filter.accepts(entry));
    }

    // --- skipConstructors ---

    @Test
    public void constructorAcceptedByDefault() {
        BatchFilter filter = BatchFilter.acceptAll();
        SmaliMethodEntry entry = entry("Lcom/example/Foo;", "<init>",
                "Lcom/example/Foo;-><init>()V", "public", "constructor");

        assertTrue(filter.accepts(entry));
    }

    @Test
    public void constructorRejectedWhenSkipped() {
        BatchFilter filter = BatchFilter.builder().skipConstructors(true).build();

        SmaliMethodEntry init = entry("Lcom/example/Foo;", "<init>",
                "Lcom/example/Foo;-><init>()V", "public", "constructor");
        SmaliMethodEntry clinit = entry("Lcom/example/Foo;", "<clinit>",
                "Lcom/example/Foo;-><clinit>()V", "static", "constructor");

        assertFalse(filter.accepts(init));
        assertFalse(filter.accepts(clinit));
    }

    // --- Class name filtering ---

    @Test
    public void includeClassPattern_matchesOnly() {
        BatchFilter filter = BatchFilter.builder()
                .includeClasses("com\\.example\\.app\\..*")
                .build();

        SmaliMethodEntry match = entry("Lcom/example/app/Foo;", "bar",
                "Lcom/example/app/Foo;->bar()V", "public");
        SmaliMethodEntry noMatch = entry("Lcom/other/Foo;", "bar",
                "Lcom/other/Foo;->bar()V", "public");

        assertTrue(filter.accepts(match));
        assertFalse(filter.accepts(noMatch));
    }

    @Test
    public void excludeClassPattern_rejectsMatches() {
        BatchFilter filter = BatchFilter.builder()
                .excludeClasses(".*BuildConfig")
                .build();

        SmaliMethodEntry normal = entry("Lcom/example/Foo;", "bar",
                "Lcom/example/Foo;->bar()V", "public");
        SmaliMethodEntry buildConfig = entry("Lcom/example/BuildConfig;", "bar",
                "Lcom/example/BuildConfig;->bar()V", "public");

        assertTrue(filter.accepts(normal));
        assertFalse(filter.accepts(buildConfig));
    }

    // --- Method name filtering ---

    @Test
    public void includeMethodPattern_matchesOnly() {
        BatchFilter filter = BatchFilter.builder()
                .includeMethods("get.*|set.*")
                .build();

        SmaliMethodEntry getter = entry("Lcom/example/Foo;", "getName",
                "Lcom/example/Foo;->getName()Ljava/lang/String;", "public");
        SmaliMethodEntry other = entry("Lcom/example/Foo;", "doWork",
                "Lcom/example/Foo;->doWork()V", "public");

        assertTrue(filter.accepts(getter));
        assertFalse(filter.accepts(other));
    }

    @Test
    public void excludeMethodPattern_rejectsMatches() {
        BatchFilter filter = BatchFilter.builder()
                .excludeMethods("access\\$.*")
                .build();

        SmaliMethodEntry normal = entry("Lcom/example/Foo;", "bar",
                "Lcom/example/Foo;->bar()V", "public");
        SmaliMethodEntry accessor = entry("Lcom/example/Foo;", "access$000",
                "Lcom/example/Foo;->access$000()V", "private");

        assertTrue(filter.accepts(normal));
        assertFalse(filter.accepts(accessor));
    }

    // --- Combined filters ---

    @Test
    public void combinedFilters_allApplied() {
        BatchFilter filter = BatchFilter.builder()
                .skipConstructors(true)
                .includeClasses("com\\.example\\.app\\..*")
                .excludeMethods("access\\$.*")
                .build();

        SmaliMethodEntry good = entry("Lcom/example/app/Foo;", "bar",
                "Lcom/example/app/Foo;->bar()V", "public");
        SmaliMethodEntry wrongPackage = entry("Lcom/other/Foo;", "bar",
                "Lcom/other/Foo;->bar()V", "public");
        SmaliMethodEntry constructor = entry("Lcom/example/app/Foo;", "<init>",
                "Lcom/example/app/Foo;-><init>()V", "public", "constructor");
        SmaliMethodEntry synthetic = entry("Lcom/example/app/Foo;", "access$000",
                "Lcom/example/app/Foo;->access$000()V", "private", "synthetic");

        assertTrue(filter.accepts(good));
        assertFalse(filter.accepts(wrongPackage));
        assertFalse(filter.accepts(constructor));
        assertFalse("Synthetic rejected by hard rule", filter.accepts(synthetic));
    }

    // --- apply() ---

    @Test
    public void apply_filtersListCorrectly() {
        BatchFilter filter = BatchFilter.builder().skipConstructors(true).build();

        List<SmaliMethodEntry> input = Arrays.asList(
                entry("Lcom/example/Foo;", "<init>",
                        "Lcom/example/Foo;-><init>()V", "public", "constructor"),
                entry("Lcom/example/Foo;", "bar",
                        "Lcom/example/Foo;->bar()V", "public"),
                entry("Lcom/example/Foo;", "doWork",
                        "Lcom/example/Foo;->doWork()V", "public", "abstract")
        );

        List<SmaliMethodEntry> result = filter.apply(input);

        assertEquals(1, result.size());
        assertEquals("bar", result.get(0).getMethodName());
    }

    @Test
    public void apply_emptyInput_returnsEmpty() {
        List<SmaliMethodEntry> result = BatchFilter.acceptAll()
                .apply(Collections.<SmaliMethodEntry>emptyList());
        assertTrue(result.isEmpty());
    }
}
