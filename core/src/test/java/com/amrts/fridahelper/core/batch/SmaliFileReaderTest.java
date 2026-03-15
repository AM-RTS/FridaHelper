package com.amrts.fridahelper.core.batch;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class SmaliFileReaderTest {

    private final SmaliFileReader reader = new SmaliFileReader();

    @Test
    public void parseNormalClass_extractsAllMethods() {
        List<String> lines = Arrays.asList(
                ".class public Lcom/example/Foo;",
                ".super Ljava/lang/Object;",
                "",
                ".method public constructor <init>()V",
                "    return-void",
                ".end method",
                "",
                ".method public bar(ILjava/lang/String;)V",
                "    return-void",
                ".end method",
                "",
                ".method public static native nativeCall()Z",
                ".end method"
        );

        List<SmaliMethodEntry> entries = reader.parseLines(lines);

        assertEquals(3, entries.size());

        SmaliMethodEntry init = entries.get(0);
        assertEquals("Lcom/example/Foo;", init.getClassDescriptor());
        assertEquals("com.example.Foo", init.getClassName());
        assertEquals("<init>", init.getMethodName());
        assertEquals("Lcom/example/Foo;-><init>()V", init.getFullSignature());
        assertTrue(init.isConstructor());
        assertTrue(init.getAccessFlags().contains("public"));
        assertTrue(init.getAccessFlags().contains("constructor"));

        SmaliMethodEntry bar = entries.get(1);
        assertEquals("bar", bar.getMethodName());
        assertEquals("Lcom/example/Foo;->bar(ILjava/lang/String;)V", bar.getFullSignature());
        assertFalse(bar.isConstructor());
        assertFalse(bar.isAbstract());
        assertFalse(bar.isNative());

        SmaliMethodEntry nativeMethod = entries.get(2);
        assertEquals("nativeCall", nativeMethod.getMethodName());
        assertTrue(nativeMethod.isNative());
        assertTrue(nativeMethod.getAccessFlags().contains("static"));
    }

    @Test
    public void parseAbstractInterface_marksAbstract() {
        List<String> lines = Arrays.asList(
                ".class public abstract interface Lcom/example/IFoo;",
                ".super Ljava/lang/Object;",
                "",
                ".method public abstract doWork()V",
                ".end method"
        );

        List<SmaliMethodEntry> entries = reader.parseLines(lines);

        assertEquals(1, entries.size());
        assertTrue(entries.get(0).isAbstract());
    }

    @Test
    public void parseSyntheticAndBridge_marksCorrectly() {
        List<String> lines = Arrays.asList(
                ".class public Lcom/example/Foo;",
                ".super Ljava/lang/Object;",
                "",
                ".method private synthetic access$000()V",
                "    return-void",
                ".end method",
                "",
                ".method public static bridge synthetic access$100(Lcom/example/Foo;)Ljava/lang/String;",
                "    return-object v0",
                ".end method"
        );

        List<SmaliMethodEntry> entries = reader.parseLines(lines);

        assertEquals(2, entries.size());
        assertTrue(entries.get(0).isSynthetic());
        assertTrue(entries.get(1).isBridge());
        assertTrue(entries.get(1).isSynthetic());
    }

    @Test
    public void parseEmptyClass_returnsEmptyList() {
        List<String> lines = Arrays.asList(
                ".class public Lcom/example/Empty;",
                ".super Ljava/lang/Object;"
        );

        List<SmaliMethodEntry> entries = reader.parseLines(lines);
        assertTrue(entries.isEmpty());
    }

    @Test
    public void parseMissingClassDirective_returnsEmptyList() {
        List<String> lines = Arrays.asList(
                ".super Ljava/lang/Object;",
                "",
                ".method public doSomething()V",
                "    return-void",
                ".end method"
        );

        List<SmaliMethodEntry> entries = reader.parseLines(lines);
        assertTrue("Methods before .class directive should be ignored", entries.isEmpty());
    }

    @Test
    public void parseNullLines_returnsEmptyList() {
        assertTrue(reader.parseLines(null).isEmpty());
    }

    @Test
    public void parseEmptyLines_returnsEmptyList() {
        assertTrue(reader.parseLines(Collections.<String>emptyList()).isEmpty());
    }

    @Test
    public void parseMethodWithNoAccessFlags() {
        List<String> lines = Arrays.asList(
                ".class public Lcom/example/Foo;",
                ".super Ljava/lang/Object;",
                "",
                ".method doSomething()V",
                "    return-void",
                ".end method"
        );

        List<SmaliMethodEntry> entries = reader.parseLines(lines);

        assertEquals(1, entries.size());
        assertEquals("doSomething", entries.get(0).getMethodName());
        assertTrue(entries.get(0).getAccessFlags().isEmpty());
    }

    @Test
    public void parseMethodWithArrayParams() {
        List<String> lines = Arrays.asList(
                ".class public Lcom/example/Foo;",
                ".super Ljava/lang/Object;",
                "",
                ".method public process([B[Ljava/lang/String;)V",
                "    return-void",
                ".end method"
        );

        List<SmaliMethodEntry> entries = reader.parseLines(lines);

        assertEquals(1, entries.size());
        assertEquals("Lcom/example/Foo;->process([B[Ljava/lang/String;)V",
                entries.get(0).getFullSignature());
    }

    @Test
    public void parseClinit_isConstructor() {
        List<String> lines = Arrays.asList(
                ".class public Lcom/example/Foo;",
                ".super Ljava/lang/Object;",
                "",
                ".method static constructor <clinit>()V",
                "    return-void",
                ".end method"
        );

        List<SmaliMethodEntry> entries = reader.parseLines(lines);

        assertEquals(1, entries.size());
        assertTrue(entries.get(0).isConstructor());
        assertEquals("<clinit>", entries.get(0).getMethodName());
    }

    @Test
    public void parseMultipleAccessFlags_allCaptured() {
        List<String> lines = Arrays.asList(
                ".class public Lcom/example/Foo;",
                ".super Ljava/lang/Object;",
                "",
                ".method public static final synchronized doWork(I)V",
                "    return-void",
                ".end method"
        );

        List<SmaliMethodEntry> entries = reader.parseLines(lines);

        assertEquals(1, entries.size());
        SmaliMethodEntry entry = entries.get(0);
        assertEquals("doWork", entry.getMethodName());
        assertTrue(entry.getAccessFlags().contains("public"));
        assertTrue(entry.getAccessFlags().contains("static"));
        assertTrue(entry.getAccessFlags().contains("final"));
        assertTrue(entry.getAccessFlags().contains("synchronized"));
    }
}
