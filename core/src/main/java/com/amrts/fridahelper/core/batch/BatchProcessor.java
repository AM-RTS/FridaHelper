package com.amrts.fridahelper.core.batch;

import com.amrts.fridahelper.core.generator.CompositionOptions;
import com.amrts.fridahelper.core.generator.ScriptComposer;
import com.amrts.fridahelper.core.model.GeneratedScript;
import com.amrts.fridahelper.core.model.HookRequest;
import com.amrts.fridahelper.core.model.SmaliMethod;
import com.amrts.fridahelper.core.parser.SmaliSignatureParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates batch hook generation from a directory of .smali files.
 *
 * <p><b>CLI-only:</b> Uses {@code java.nio.file.Path} APIs which require API 26+ on Android.
 * The Android app module reimplements file reading with {@code java.io.File} and SAF URIs.
 *
 * <p>Pipeline: scan directory → read .smali files → filter methods →
 * parse signatures → generate hooks → compose into single script.
 *
 * <p>Uses the existing {@link SmaliSignatureParser}, generator, and
 * {@link ScriptComposer} pipeline — no duplicated logic.
 */
public final class BatchProcessor {

    private final SmaliFileReader fileReader;
    private final SmaliSignatureParser parser;

    public BatchProcessor() {
        this.fileReader = new SmaliFileReader();
        this.parser = new SmaliSignatureParser();
    }

    /**
     * Processes all .smali files in a directory and generates a composed Frida script.
     *
     * @param directory root directory containing .smali files
     * @param filter   method filter (use {@link BatchFilter#acceptAll()} for no filtering)
     * @param options  composition options (wrapping, timeout)
     * @return composed script, or a comment script if no hookable methods found
     * @throws IOException if the directory cannot be read
     * @throws IllegalArgumentException if directory or filter or options is null
     */
    public GeneratedScript process(Path directory, BatchFilter filter,
                                   CompositionOptions options) throws IOException {
        if (directory == null) throw new IllegalArgumentException("directory must not be null");
        if (filter == null) throw new IllegalArgumentException("filter must not be null");
        if (options == null) throw new IllegalArgumentException("options must not be null");

        List<SmaliMethodEntry> allEntries = fileReader.readDirectory(directory);
        List<SmaliMethodEntry> filtered = filter.apply(allEntries);

        List<HookRequest> requests = new ArrayList<>();
        for (SmaliMethodEntry entry : filtered) {
            try {
                SmaliMethod method = parser.parse(entry.getFullSignature());
                requests.add(HookRequest.java(method));
            } catch (IllegalArgumentException ignored) {
                // Skip signatures that SmaliSignatureParser can't handle
            }
        }

        if (requests.isEmpty()) {
            return new GeneratedScript(
                    "// No hookable methods found", HookRequest.Type.JAVA);
        }

        ScriptComposer composer = new ScriptComposer(requests);
        return composer.compose(options);
    }

    /**
     * Overload that reads from pre-parsed entries (useful when caller already has entries,
     * e.g. for UI display before generating).
     */
    public GeneratedScript processEntries(List<SmaliMethodEntry> entries,
                                          BatchFilter filter,
                                          CompositionOptions options) {
        if (entries == null) throw new IllegalArgumentException("entries must not be null");
        if (filter == null) throw new IllegalArgumentException("filter must not be null");
        if (options == null) throw new IllegalArgumentException("options must not be null");

        List<SmaliMethodEntry> filtered = filter.apply(entries);

        List<HookRequest> requests = new ArrayList<>();
        for (SmaliMethodEntry entry : filtered) {
            try {
                SmaliMethod method = parser.parse(entry.getFullSignature());
                requests.add(HookRequest.java(method));
            } catch (IllegalArgumentException ignored) {
                // Skip unparseable signatures
            }
        }

        if (requests.isEmpty()) {
            return new GeneratedScript(
                    "// No hookable methods found", HookRequest.Type.JAVA);
        }

        ScriptComposer composer = new ScriptComposer(requests);
        return composer.compose(options);
    }
}
