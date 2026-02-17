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
 * Calls core generators on a background thread and posts results to LiveData.
 * No logic duplication from CLI — uses the exact same ScriptGenerator interface.
 */
public class HookViewModel extends ViewModel {

    private final SmaliSignatureParser parser = new SmaliSignatureParser();
    private final ScriptGenerator javaGenerator = new JavaHookGenerator();
    private final ScriptGenerator nativeGenerator = new NativeHookGenerator();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<String> scriptOutput = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public LiveData<String> getScriptOutput() { return scriptOutput; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    /**
     * Generates a Java hook script from a smali signature.
     * Runs on background thread, posts result to LiveData.
     *
     * @param smaliSignature the full smali method signature
     * @param wrapInPerform  true to wrap in Java.perform()
     */
    public void generateJavaHook(String smaliSignature, boolean wrapInPerform) {
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
            }
        });
    }

    /**
     * Generates a native hook script from the given parameters.
     * Runs on background thread, posts result to LiveData.
     */
    public void generateNativeHook(NativeSymbol symbol) {
        executor.execute(() -> {
            try {
                HookRequest request = HookRequest.nativeHook(symbol);
                GeneratedScript result = nativeGenerator.generate(request);

                scriptOutput.postValue(result.getScriptText());
                errorMessage.postValue(null);
            } catch (Exception e) {
                errorMessage.postValue(e.getMessage());
                scriptOutput.postValue(null);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }
}
