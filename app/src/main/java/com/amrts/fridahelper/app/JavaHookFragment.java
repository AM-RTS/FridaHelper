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
import com.google.android.material.snackbar.Snackbar;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Fragment for generating Java hook scripts from smali signatures.
 * Observes HookViewModel java-specific LiveData for results and errors.
 * Validation errors are shown inline on the TextInputLayout (field-level).
 */
public class JavaHookFragment extends Fragment {

    private HookViewModel viewModel;
    private TextInputLayout layoutSignature;
    private TextInputLayout layoutTimeout;
    private TextInputEditText editSignature;
    private TextInputEditText editTimeout;
    private MaterialSwitch switchFullScript;
    private TextView textOutput;
    private TextView labelOutput;
    private MaterialButton btnCopy;
    private MaterialButton btnExport;

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

        layoutSignature = view.findViewById(R.id.layout_smali_signature);
        layoutTimeout = view.findViewById(R.id.layout_timeout);
        editSignature = view.findViewById(R.id.edit_smali_signature);
        editTimeout = view.findViewById(R.id.edit_timeout);
        switchFullScript = view.findViewById(R.id.switch_full_script);
        textOutput = view.findViewById(R.id.text_output);
        labelOutput = view.findViewById(R.id.label_output);
        btnCopy = view.findViewById(R.id.btn_copy);
        btnExport = view.findViewById(R.id.btn_export);
        MaterialButton btnGenerate = view.findViewById(R.id.btn_generate);

        btnGenerate.setOnClickListener(v -> onGenerate());
        btnCopy.setOnClickListener(v -> copyToClipboard());
        btnExport.setOnClickListener(v -> exportToFile());

        viewModel.getIsGenerating().observe(getViewLifecycleOwner(), generating -> {
            btnGenerate.setEnabled(!Boolean.TRUE.equals(generating));
        });

        viewModel.getJavaScriptOutput().observe(getViewLifecycleOwner(), script -> {
            if (script != null) {
                labelOutput.setVisibility(View.VISIBLE);
                textOutput.setVisibility(View.VISIBLE);
                textOutput.setText(script);
                btnCopy.setVisibility(View.VISIBLE);
                btnExport.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getJavaErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                layoutSignature.setError(error);
                labelOutput.setVisibility(View.GONE);
                textOutput.setVisibility(View.GONE);
                btnCopy.setVisibility(View.GONE);
                btnExport.setVisibility(View.GONE);
                if (getView() != null) {
                    Snackbar.make(getView(), error, Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    private void onGenerate() {
        layoutSignature.setError(null);
        layoutTimeout.setError(null);

        String signature = editSignature.getText() != null
                ? editSignature.getText().toString().trim() : "";

        if (signature.isEmpty()) {
            layoutSignature.setError(getString(R.string.msg_error_empty_signature));
            return;
        }

        String timeoutText = editTimeout.getText() != null
                ? editTimeout.getText().toString().trim() : "";
        HookViewModel.ParseResult timeoutResult =
                HookViewModel.safeParseInt(timeoutText, 0, HookViewModel.MAX_TIMEOUT_MS);
        if (!timeoutResult.isValid()) {
            layoutTimeout.setError(timeoutResult.getError());
            return;
        }

        viewModel.generateJavaHook(signature, switchFullScript.isChecked(), timeoutResult.getValue());
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

    private void exportToFile() {
        CharSequence text = textOutput.getText();
        if (text == null || text.length() == 0) return;

        ScriptExporter.ExportResult result =
                ScriptExporter.export(requireContext(), text.toString());

        if (getView() == null) return;

        if (result.isSuccess()) {
            Snackbar.make(getView(),
                    getString(R.string.msg_exported, result.getFilename()),
                    Snackbar.LENGTH_LONG).show();
        } else {
            Snackbar.make(getView(),
                    getString(R.string.msg_export_failed, result.getError()),
                    Snackbar.LENGTH_LONG).show();
        }
    }
}
