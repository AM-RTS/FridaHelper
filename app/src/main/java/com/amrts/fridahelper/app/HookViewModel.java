package com.amrts.fridahelper.app;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.amrts.fridahelper.core.generator.JavaHookGenerator;
import com.amrts.fridahelper.core.generator.NativeHookGenerator;
import com.amrts.fridahelper.core.generator.ScriptGenerator;
import com.amrts.fridahelper.core.generator.ScriptWrapper;
import com.amrts.fridahelper.core.model.GeneratedScript;
import com.amrts.fridahelper.core.model.HookRequest;
import com.amrts.fridahelper.core.model.NativeSymbol;
import com.amrts.fridahelper.core.model.SmaliMethod;
import com.amrts.fridahelper.core.parser.SmaliSignatureParser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared ViewModel for both Java and Native hook generation.
 *
 * Design notes:
 * - Uses a single-thread executor: requests serialize (no race conditions).
 * - isGenerating LiveData prevents duplicate submissions from button spam.
 * - onCleared() shuts down the executor when ViewModel is destroyed.
 * - Single version stream: app version = core version (FridaHelperVersion.VERSION).
 * - Separate LiveData streams for Java and Native output so tab switching
 *   does not show stale output from the other tab.
 * - Safe integer parsing: values larger than max limits are rejected
 *   with a field-level error rather than crashing.
 * - Wrapping order: Java.perform (inner) then setTimeout (outer).
 *   This produces: setTimeout(function(){ Java.perform(function(){ ... }) }, ms)
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
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Java hook output
    private final MutableLiveData<String> javaScriptOutput = new MutableLiveData<>();
    private final MutableLiveData<String> javaErrorMessage = new MutableLiveData<>();

    // Native hook output
    private final MutableLiveData<String> nativeScriptOutput = new MutableLiveData<>();
    private final MutableLiveData<String> nativeErrorMessage = new MutableLiveData<>();

    // Shared generation guard
    private final MutableLiveData<Boolean> isGenerating = new MutableLiveData<>(false);

    public LiveData<String> getJavaScriptOutput() { return javaScriptOutput; }
    public LiveData<String> getJavaErrorMessage() { return javaErrorMessage; }
    public LiveData<String> getNativeScriptOutput() { return nativeScriptOutput; }
    public LiveData<String> getNativeErrorMessage() { return nativeErrorMessage; }
    public LiveData<Boolean> getIsGenerating() { return isGenerating; }

    /**
     * Generates a Java hook script from a smali signature.
     * Wrapping order: Java.perform (inner) then setTimeout (outer).
     * Runs on background thread, posts result to java-specific LiveData.
     * Ignored if a generation is already in progress.
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
     * Optionally wraps in Java.perform (useful for JNI function hooks).
     *
     * Wrapping order when both wrapInPerform and setTimeout are active:
     *   setTimeout(function(){ Java.perform(function(){ Interceptor.attach... }) }, ms)
     *
     * To achieve this, when wrapInPerform is true we strip setTimeout from the
     * NativeSymbol (let the generator produce the raw hook), wrap in Java.perform,
     * then apply setTimeout from ScriptWrapper.
     *
     * Runs on background thread, posts result to native-specific LiveData.
     * Ignored if a generation is already in progress.
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

    /**
     * Rebuilds a NativeSymbol with setTimeoutMs=0, preserving all other fields.
     * Used when the ViewModel needs to control the setTimeout wrapping order.
     */
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

    /**
     * Safely parses an integer string, returning a {@link ParseResult}.
     * - Empty/blank strings return the default value.
     * - Values exceeding maxValue or negative values are rejected.
     * - Non-numeric strings are rejected.
     *
     * This method is called from Fragments before building the request,
     * keeping validation logic centralized in the ViewModel.
     */
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

    /**
     * Result of a safe integer parse operation.
     * Either holds a valid int value, or an error message for field-level display.
     */
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
