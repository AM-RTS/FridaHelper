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
 *
 * No logic duplication from CLI — uses the exact same ScriptGenerator interface.
 */
public class HookViewModel extends ViewModel {

    private final SmaliSignatureParser parser = new SmaliSignatureParser();
    private final ScriptGenerator javaGenerator = new JavaHookGenerator();
    private final ScriptGenerator nativeGenerator = new NativeHookGenerator();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<String> scriptOutput = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isGenerating = new MutableLiveData<>(false);

    public LiveData<String> getScriptOutput() { return scriptOutput; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getIsGenerating() { return isGenerating; }

    /**
     * Generates a Java hook script from a smali signature.
     * Runs on background thread, posts result to LiveData.
     * Ignored if a generation is already in progress.
     */
    public void generateJavaHook(String smaliSignature, boolean wrapInPerform) {
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

                scriptOutput.postValue(result.getScriptText());
                errorMessage.postValue(null);
            } catch (Exception e) {
                errorMessage.postValue(e.getMessage());
                scriptOutput.postValue(null);
            } finally {
                isGenerating.postValue(false);
            }
        });
    }

    /**
     * Generates a native hook script from the given parameters.
     * Runs on background thread, posts result to LiveData.
     * Ignored if a generation is already in progress.
     */
    public void generateNativeHook(NativeSymbol symbol) {
        if (Boolean.TRUE.equals(isGenerating.getValue())) return;
        isGenerating.setValue(true);

        executor.execute(() -> {
            try {
                HookRequest request = HookRequest.nativeHook(symbol);
                GeneratedScript result = nativeGenerator.generate(request);

                scriptOutput.postValue(result.getScriptText());
                errorMessage.postValue(null);
            } catch (Exception e) {
                errorMessage.postValue(e.getMessage());
                scriptOutput.postValue(null);
            } finally {
                isGenerating.postValue(false);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }
}
