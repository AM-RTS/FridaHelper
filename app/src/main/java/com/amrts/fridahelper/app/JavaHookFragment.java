package com.amrts.fridahelper.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Fragment for generating Java hook scripts from smali signatures.
 * Observes HookViewModel LiveData for results and errors.
 */
public class JavaHookFragment extends Fragment {

    private HookViewModel viewModel;
    private TextInputEditText editSignature;
    private MaterialSwitch switchFullScript;
    private TextView textOutput;
    private TextView textError;
    private TextView labelOutput;
    private MaterialButton btnCopy;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_java_hook, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(HookViewModel.class);

        editSignature = view.findViewById(R.id.edit_smali_signature);
        switchFullScript = view.findViewById(R.id.switch_full_script);
        textOutput = view.findViewById(R.id.text_output);
        textError = view.findViewById(R.id.text_error);
        labelOutput = view.findViewById(R.id.label_output);
        btnCopy = view.findViewById(R.id.btn_copy);
        MaterialButton btnGenerate = view.findViewById(R.id.btn_generate);

        btnGenerate.setOnClickListener(v -> onGenerate());
        btnCopy.setOnClickListener(v -> copyToClipboard());

        viewModel.getIsGenerating().observe(getViewLifecycleOwner(), generating -> {
            btnGenerate.setEnabled(!Boolean.TRUE.equals(generating));
        });

        viewModel.getScriptOutput().observe(getViewLifecycleOwner(), script -> {
            if (script != null) {
                labelOutput.setVisibility(View.VISIBLE);
                textOutput.setVisibility(View.VISIBLE);
                textOutput.setText(script);
                btnCopy.setVisibility(View.VISIBLE);
                textError.setVisibility(View.GONE);
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                textError.setVisibility(View.VISIBLE);
                textError.setText(error);
                labelOutput.setVisibility(View.GONE);
                textOutput.setVisibility(View.GONE);
                btnCopy.setVisibility(View.GONE);
            }
        });
    }

    private void onGenerate() {
        String signature = editSignature.getText() != null
                ? editSignature.getText().toString().trim() : "";

        if (signature.isEmpty()) {
            textError.setVisibility(View.VISIBLE);
            textError.setText(R.string.msg_error_empty_signature);
            return;
        }

        viewModel.generateJavaHook(signature, switchFullScript.isChecked());
    }

    private void copyToClipboard() {
        CharSequence text = textOutput.getText();
        if (text == null || text.length() == 0) return;

        ClipboardManager clipboard = (ClipboardManager)
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("FridaHelper Script", text));
            Toast.makeText(requireContext(), R.string.msg_copied, Toast.LENGTH_SHORT).show();
        }
    }
}
