package com.amrts.fridahelper.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.amrts.fridahelper.core.model.NativeSymbol;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Fragment for generating native hook scripts.
 * Supports export-based and address-based targeting, waitForLoad, and setTimeout.
 */
public class NativeHookFragment extends Fragment {

    private HookViewModel viewModel;
    private RadioGroup radioTargetMode;
    private View layoutExportFields;
    private TextInputLayout layoutAddressField;
    private TextInputEditText editLibName;
    private TextInputEditText editExportName;
    private TextInputEditText editAddress;
    private TextInputEditText editArgCount;
    private TextInputEditText editTimeout;
    private MaterialSwitch switchWaitForLoad;
    private TextView textOutput;
    private TextView textError;
    private TextView labelOutput;
    private MaterialButton btnCopy;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_native_hook, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(HookViewModel.class);

        radioTargetMode = view.findViewById(R.id.radio_target_mode);
        layoutExportFields = view.findViewById(R.id.layout_export_fields);
        layoutAddressField = view.findViewById(R.id.layout_address_field);
        editLibName = view.findViewById(R.id.edit_lib_name);
        editExportName = view.findViewById(R.id.edit_export_name);
        editAddress = view.findViewById(R.id.edit_address);
        editArgCount = view.findViewById(R.id.edit_arg_count);
        editTimeout = view.findViewById(R.id.edit_timeout);
        switchWaitForLoad = view.findViewById(R.id.switch_wait_for_load);
        textOutput = view.findViewById(R.id.text_output);
        textError = view.findViewById(R.id.text_error);
        labelOutput = view.findViewById(R.id.label_output);
        btnCopy = view.findViewById(R.id.btn_copy);
        MaterialButton btnGenerate = view.findViewById(R.id.btn_generate);

        radioTargetMode.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isExport = checkedId == R.id.radio_export;
            layoutExportFields.setVisibility(isExport ? View.VISIBLE : View.GONE);
            layoutAddressField.setVisibility(isExport ? View.GONE : View.VISIBLE);
        });

        btnGenerate.setOnClickListener(v -> onGenerate());
        btnCopy.setOnClickListener(v -> copyToClipboard());

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
        boolean isExportMode = radioTargetMode.getCheckedRadioButtonId() == R.id.radio_export;

        NativeSymbol.Builder builder = new NativeSymbol.Builder();

        if (isExportMode) {
            String libName = getText(editLibName);
            String exportName = getText(editExportName);

            if (exportName.isEmpty()) {
                showError(getString(R.string.msg_error_empty_export));
                return;
            }

            builder.libName(libName.isEmpty() ? null : libName);
            builder.exportName(exportName);

            if (switchWaitForLoad.isChecked() && !libName.isEmpty()) {
                builder.waitForLoad(true);
            }
        } else {
            String address = getText(editAddress);
            if (address.isEmpty()) {
                showError(getString(R.string.msg_error_empty_address));
                return;
            }
            builder.address(address);
        }

        int argCount = parseIntSafe(getText(editArgCount), 0);
        int timeoutMs = parseIntSafe(getText(editTimeout), 0);
        builder.argCount(argCount);
        if (timeoutMs > 0) builder.setTimeoutMs(timeoutMs);

        try {
            NativeSymbol symbol = builder.build();
            viewModel.generateNativeHook(symbol);
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    private void showError(String message) {
        textError.setVisibility(View.VISIBLE);
        textError.setText(message);
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

    private static String getText(TextInputEditText edit) {
        return edit.getText() != null ? edit.getText().toString().trim() : "";
    }

    private static int parseIntSafe(String s, int defaultValue) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
