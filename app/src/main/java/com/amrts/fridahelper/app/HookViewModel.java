package com.amrts.fridahelper.app;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.amrts.fridahelper.core.batch.BatchFilter;
import com.amrts.fridahelper.core.batch.SmaliFileReader;
import com.amrts.fridahelper.core.batch.SmaliMethodEntry;
import com.amrts.fridahelper.core.generator.CompositionOptions;
import com.amrts.fridahelper.core.generator.JavaHookGenerator;
import com.amrts.fridahelper.core.generator.NativeHookGenerator;
import com.amrts.fridahelper.core.generator.ScriptComposer;
import com.amrts.fridahelper.core.generator.ScriptGenerator;
import com.amrts.fridahelper.core.generator.ScriptWrapper;
import com.amrts.fridahelper.core.model.GeneratedScript;
import com.amrts.fridahelper.core.model.HookRequest;
import com.amrts.fridahelper.core.model.NativeSymbol;
import com.amrts.fridahelper.core.model.SmaliMethod;
import com.amrts.fridahelper.core.parser.SmaliSignatureParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared ViewModel for both Java and Native hook generation and multi-hook composition.
 *
 * Design notes:
 * - Uses a single-thread executor: requests serialize (no race conditions).
 * - isGenerating LiveData prevents duplicate submissions from button spam.
 * - onCleared() shuts down the executor when ViewModel is destroyed.
 * - Separate LiveData streams for Java and Native output so tab switching
 *   does not show stale output from the other tab.
 * - Hook queue is maintained internally via ScriptComposer and exposed as LiveData.
 *   Fragments must not manipulate ScriptComposer directly.
 * - Queue survives rotation (ViewModel lifecycle).
 * - Composed script output uses dedicated LiveData separate from single-hook output.
 *
 * No logic duplication from CLI — uses the exact same ScriptGenerator interface.
 */
public class HookViewModel extends ViewModel {

    /** Maximum allowed value for argument count (matches NativeSymbol.MAX_ARG_COUNT). */
    public static final int MAX_ARG_COUNT = NativeSymbol.MAX_ARG_COUNT;

    /** Maximum allowed value for setTimeout delay in ms. */
    public static final int MAX_TIMEOUT_MS = 999999;

    private final SmaliSignatureParser parser = new SmaliSignatureParser();
    private final ScriptGenerator javaGenerator = new JavaHookGenerator();
    private final ScriptGenerator nativeGenerator = new NativeHookGenerator();
    private final ScriptComposer composer = new ScriptComposer();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Java hook output (single-hook mode)
    private final MutableLiveData<String> javaScriptOutput = new MutableLiveData<>();
    private final MutableLiveData<String> javaErrorMessage = new MutableLiveData<>();

    // Native hook output (single-hook mode)
    private final MutableLiveData<String> nativeScriptOutput = new MutableLiveData<>();
    private final MutableLiveData<String> nativeErrorMessage = new MutableLiveData<>();

    // Composed script output (multi-hook mode)
    private final MutableLiveData<String> composedScriptOutput = new MutableLiveData<>();
    private final MutableLiveData<String> composedErrorMessage = new MutableLiveData<>();

    // Hook queue state
    private final MutableLiveData<List<HookRequest>> hookQueue = new MutableLiveData<>(new ArrayList<>());

    // Batch import result: positive int string = success count, other string = error message
    private final MutableLiveData<ImportResult> importResult = new MutableLiveData<>();

    // Shared generation guard
    private final MutableLiveData<Boolean> isGenerating = new MutableLiveData<>(false);

    // Single-hook LiveData getters
    public LiveData<String> getJavaScriptOutput() { return javaScriptOutput; }
    public LiveData<String> getJavaErrorMessage() { return javaErrorMessage; }
    public LiveData<String> getNativeScriptOutput() { return nativeScriptOutput; }
    public LiveData<String> getNativeErrorMessage() { return nativeErrorMessage; }

    // Composed script LiveData getters
    public LiveData<String> getComposedScriptOutput() { return composedScriptOutput; }
    public LiveData<String> getComposedErrorMessage() { return composedErrorMessage; }

    // Queue LiveData getter
    public LiveData<List<HookRequest>> getHookQueue() { return hookQueue; }

    public LiveData<Boolean> getIsGenerating() { return isGenerating; }

    public LiveData<ImportResult> getImportResult() { return importResult; }

    // ========== Batch import ==========

    /**
     * Imports hookable methods from .smali file(s) into the composition queue.
     * Uses java.io.File (not NIO) for Android API 24+ compatibility.
     */
    public void importSmaliMethods(String pathString, BatchFilter filter) {
        if (Boolean.TRUE.equals(isGenerating.getValue())) return;
        isGenerating.setValue(true);

        executor.execute(() -> {
            try {
                File file = new File(pathString);

                if (!file.exists()) {
                    importResult.postValue(ImportResult.error("Path does not exist: " + pathString));
                    return;
                }
                if (!file.canRead()) {
                    importResult.postValue(ImportResult.error(
                            "Cannot read path (check storage permissions): " + pathString));
                    return;
                }

                SmaliFileReader reader = new SmaliFileReader();
                List<SmaliMethodEntry> entries = new ArrayList<>();

                if (file.isDirectory()) {
                    collectSmaliEntries(file, reader, entries);
                } else if (file.getName().endsWith(".smali")) {
                    List<String> lines = readLines(file);
                    entries.addAll(reader.parseLines(lines));
                } else {
                    importResult.postValue(ImportResult.error("Not a .smali file or directory"));
                    return;
                }

                List<SmaliMethodEntry> filtered = filter.apply(entries);

                SmaliSignatureParser localParser = new SmaliSignatureParser();
                int added = 0;
                for (SmaliMethodEntry entry : filtered) {
                    try {
                        SmaliMethod method = localParser.parse(entry.getFullSignature());
                        composer.addRequest(HookRequest.java(method));
                        added++;
                    } catch (IllegalArgumentException ignored) { }
                }

                hookQueue.postValue(Collections.unmodifiableList(
                        new ArrayList<>(composer.getRequests())));

                if (added > 0) {
                    importResult.postValue(ImportResult.success(added));
                } else {
                    importResult.postValue(ImportResult.empty());
                }
            } catch (Exception e) {
                importResult.postValue(ImportResult.error(
                        "Import failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
            } finally {
                isGenerating.postValue(false);
            }
        });
    }

    /** Recursively collects SmaliMethodEntry from all .smali files in a directory. */
    private void collectSmaliEntries(File dir, SmaliFileReader reader,
                                     List<SmaliMethodEntry> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collectSmaliEntries(child, reader, out);
            } else if (child.getName().endsWith(".smali") && child.canRead()) {
                try {
                    List<String> lines = readLines(child);
                    out.addAll(reader.parseLines(lines));
                } catch (IOException ignored) { }
            }
        }
    }

    /** Reads all lines from a file using java.io (works on all Android versions). */
    private static List<String> readLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** Typed result for batch import operations. */
    public static final class ImportResult {
        public enum Status { SUCCESS, EMPTY, ERROR }

        private final Status status;
        private final int count;
        private final String errorMessage;

        private ImportResult(Status status, int count, String errorMessage) {
            this.status = status;
            this.count = count;
            this.errorMessage = errorMessage;
        }

        public static ImportResult success(int count) {
            return new ImportResult(Status.SUCCESS, count, null);
        }

        public static ImportResult empty() {
            return new ImportResult(Status.EMPTY, 0, null);
        }

        public static ImportResult error(String message) {
            return new ImportResult(Status.ERROR, 0, message);
        }

        public Status getStatus() { return status; }
        public int getCount() { return count; }
        public String getErrorMessage() { return errorMessage; }
    }

    // ========== Hook queue management ==========

    /**
     * Adds a hook request to the composition queue and updates LiveData.
     */
    public void addHook(HookRequest request) {
        composer.addRequest(request);
        publishQueue();
    }

    /**
     * Removes a hook at the given index and updates LiveData.
     */
    public void removeHook(int index) {
        if (index >= 0 && index < composer.size()) {
            composer.removeRequest(index);
            publishQueue();
        }
    }

    /**
     * Clears all hooks from the queue and updates LiveData.
     */
    public void clearHooks() {
        composer.clear();
        publishQueue();
    }

    /**
     * Returns the current queue size.
     */
    public int getQueueSize() {
        return composer.size();
    }

    /**
     * Composes all queued hooks into a single script.
     * Takes a snapshot of the queue on the main thread, then composes on background.
     * This prevents race conditions if the queue is mutated during composition.
     */
    public void composeHooks(CompositionOptions options) {
        if (Boolean.TRUE.equals(isGenerating.getValue())) return;
        if (composer.size() == 0) {
            composedErrorMessage.setValue("Queue is empty. Add hooks first.");
            return;
        }
        isGenerating.setValue(true);

        // Snapshot on main thread to avoid race with queue mutations
        List<HookRequest> snapshot = new ArrayList<>(composer.getRequests());

        executor.execute(() -> {
            try {
                GeneratedScript result = new ScriptComposer(snapshot).compose(options);
                composedScriptOutput.postValue(result.getScriptText());
                composedErrorMessage.postValue(null);
            } catch (Exception e) {
                composedErrorMessage.postValue(e.getMessage());
                composedScriptOutput.postValue(null);
            } finally {
                isGenerating.postValue(false);
            }
        });
    }

    /**
     * Publishes a snapshot of the current queue to LiveData.
     */
    private void publishQueue() {
        hookQueue.setValue(Collections.unmodifiableList(
                new ArrayList<>(composer.getRequests())));
    }

    // ========== Single-hook generation (unchanged) ==========

    /**
     * Generates a Java hook script from a smali signature.
     */
    public void generateJavaHook(String smaliSignature, boolean wrapInPerform, int timeoutMs) {
        if (Boolean.TRUE.equals(isGenerating.getValue())) return;
        isGenerating.setValue(true);

        executor.execute(() -> {
            try {
                SmaliMethod method = parser.parse(smaliSignature);
                HookRequest request = HookRequest.java(method);
                GeneratedScript result = javaGenerator.generate(request);

                if (wrapInPerform) {
                    result = ScriptWrapper.wrapIfNeeded(result);
                }
                if (timeoutMs > 0) {
                    result = ScriptWrapper.wrapInSetTimeout(result, timeoutMs);
                }

                javaScriptOutput.postValue(result.getScriptText());
                javaErrorMessage.postValue(null);
            } catch (Exception e) {
                javaErrorMessage.postValue(e.getMessage());
                javaScriptOutput.postValue(null);
            } finally {
                isGenerating.postValue(false);
            }
        });
    }

    /**
     * Generates a native hook script from the given parameters.
     */
    public void generateNativeHook(NativeSymbol symbol, boolean wrapInPerform) {
        if (Boolean.TRUE.equals(isGenerating.getValue())) return;
        isGenerating.setValue(true);

        executor.execute(() -> {
            try {
                int timeoutMs = symbol.getSetTimeoutMs();
                NativeSymbol genSymbol = symbol;

                if (wrapInPerform && timeoutMs > 0) {
                    genSymbol = rebuildWithoutTimeout(symbol);
                }

                HookRequest request = HookRequest.nativeHook(genSymbol);
                GeneratedScript result = nativeGenerator.generate(request);

                if (wrapInPerform) {
                    result = ScriptWrapper.wrapInJavaPerform(result);
                }
                if (wrapInPerform && timeoutMs > 0) {
                    result = ScriptWrapper.wrapInSetTimeout(result, timeoutMs);
                }

                nativeScriptOutput.postValue(result.getScriptText());
                nativeErrorMessage.postValue(null);
            } catch (Exception e) {
                nativeErrorMessage.postValue(e.getMessage());
                nativeScriptOutput.postValue(null);
            } finally {
                isGenerating.postValue(false);
            }
        });
    }

    private NativeSymbol rebuildWithoutTimeout(NativeSymbol original) {
        NativeSymbol.Builder builder = new NativeSymbol.Builder()
                .argCount(original.getArgCount());

        if (original.getTargetMode() == NativeSymbol.TargetMode.EXPORT) {
            builder.libName(original.getLibName())
                   .exportName(original.getExportName());
            if (original.isWaitForLoad()) {
                builder.waitForLoad(true);
            }
        } else {
            builder.address(original.getAddress());
        }

        return builder.build();
    }

    // ========== HookRequest creation helpers (used by fragments) ==========

    /**
     * Parses a smali signature and creates a Java HookRequest.
     * Returns null on parse failure (caller should handle error).
     */
    public HookRequest createJavaHookRequest(String smaliSignature) {
        SmaliMethod method = parser.parse(smaliSignature);
        return HookRequest.java(method);
    }

    // ========== Utility ==========

    public static ParseResult safeParseInt(String text, int defaultValue, int maxValue) {
        if (text == null || text.trim().isEmpty()) {
            return ParseResult.success(defaultValue);
        }
        String trimmed = text.trim();
        try {
            long value = Long.parseLong(trimmed);
            if (value < 0) {
                return ParseResult.error("Value cannot be negative");
            }
            if (value > maxValue) {
                return ParseResult.error("Value too large (max " + maxValue + ")");
            }
            return ParseResult.success((int) value);
        } catch (NumberFormatException e) {
            return ParseResult.error("Not a valid number");
        }
    }

    public static final class ParseResult {
        private final int value;
        private final String error;

        private ParseResult(int value, String error) {
            this.value = value;
            this.error = error;
        }

        public static ParseResult success(int value) {
            return new ParseResult(value, null);
        }

        public static ParseResult error(String message) {
            return new ParseResult(0, message);
        }

        public boolean isValid() { return error == null; }
        public int getValue() { return value; }
        public String getError() { return error; }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }
}
