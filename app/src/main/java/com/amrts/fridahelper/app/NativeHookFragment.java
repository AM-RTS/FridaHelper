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
import com.google.android.material.snackbar.Snackbar;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amrts.fridahelper.core.model.HookRequest;
import com.amrts.fridahelper.core.model.NativeSymbol;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

/**
 * Fragment for generating native hook scripts.
 * Supports both single-hook generation and multi-hook queue composition.
 */
public class NativeHookFragment extends Fragment {

    private HookViewModel viewModel;
    private RadioGroup radioTargetMode;
    private TextInputLayout layoutLibName;
    private TextInputLayout layoutExportName;
    private TextInputLayout layoutAddressField;
    private TextInputLayout layoutArgCount;
    private TextInputLayout layoutTimeout;
    private TextInputEditText editLibName;
    private TextInputEditText editExportName;
    private TextInputEditText editAddress;
    private TextInputEditText editArgCount;
    private TextInputEditText editTimeout;
    private MaterialSwitch switchWaitForLoad;
    private MaterialSwitch switchWrapPerform;
    private TextView textOutput;
    private TextView labelOutput;
    private MaterialButton btnCopy;
    private MaterialButton btnExport;

    // Queue UI
    private TextView labelQueue;
    private RecyclerView recyclerQueue;
    private View layoutQueueActions;
    private HookQueueAdapter queueAdapter;

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
        layoutLibName = view.findViewById(R.id.layout_lib_name);
        layoutExportName = view.findViewById(R.id.layout_export_name);
        layoutAddressField = view.findViewById(R.id.layout_address_field);
        layoutArgCount = view.findViewById(R.id.layout_arg_count);
        layoutTimeout = view.findViewById(R.id.layout_timeout);
        editLibName = view.findViewById(R.id.edit_lib_name);
        editExportName = view.findViewById(R.id.edit_export_name);
        editAddress = view.findViewById(R.id.edit_address);
        editArgCount = view.findViewById(R.id.edit_arg_count);
        editTimeout = view.findViewById(R.id.edit_timeout);
        switchWaitForLoad = view.findViewById(R.id.switch_wait_for_load);
        switchWrapPerform = view.findViewById(R.id.switch_wrap_perform);
        textOutput = view.findViewById(R.id.text_output);
        labelOutput = view.findViewById(R.id.label_output);
        btnCopy = view.findViewById(R.id.btn_copy);
        btnExport = view.findViewById(R.id.btn_export);
        MaterialButton btnGenerate = view.findViewById(R.id.btn_generate);
        MaterialButton btnAddToQueue = view.findViewById(R.id.btn_add_to_queue);

        // Queue UI
        labelQueue = view.findViewById(R.id.label_queue);
        recyclerQueue = view.findViewById(R.id.recycler_queue);
        layoutQueueActions = view.findViewById(R.id.layout_queue_actions);
        MaterialButton btnCompose = view.findViewById(R.id.btn_compose);
        MaterialButton btnClearQueue = view.findViewById(R.id.btn_clear_queue);

        recyclerQueue.setLayoutManager(new LinearLayoutManager(requireContext()));
        queueAdapter = new HookQueueAdapter();
        queueAdapter.setOnDeleteListener(position -> viewModel.removeHook(position));
        recyclerQueue.setAdapter(queueAdapter);

        radioTargetMode.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isExport = checkedId == R.id.radio_export;
            layoutExportName.setVisibility(isExport ? View.VISIBLE : View.GONE);
            layoutAddressField.setVisibility(isExport ? View.GONE : View.VISIBLE);
        });

        btnGenerate.setOnClickListener(v -> onGenerate());
        btnAddToQueue.setOnClickListener(v -> onAddToQueue());
        btnCompose.setOnClickListener(v -> onCompose());
        btnClearQueue.setOnClickListener(v -> {
            viewModel.clearHooks();
            Snackbar.make(view, R.string.msg_queue_cleared, Snackbar.LENGTH_SHORT).show();
        });
        btnCopy.setOnClickListener(v -> copyToClipboard());
        btnExport.setOnClickListener(v -> exportToFile());

        viewModel.getIsGenerating().observe(getViewLifecycleOwner(), generating -> {
            btnGenerate.setEnabled(!Boolean.TRUE.equals(generating));
            btnAddToQueue.setEnabled(!Boolean.TRUE.equals(generating));
        });

        viewModel.getNativeScriptOutput().observe(getViewLifecycleOwner(), script -> {
            if (script != null) {
                showOutput(script);
            }
        });

        viewModel.getNativeErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                hideOutput();
                if (getView() != null) {
                    Snackbar.make(getView(), error, Snackbar.LENGTH_LONG).show();
                }
            }
        });

        viewModel.getComposedScriptOutput().observe(getViewLifecycleOwner(), script -> {
            if (script != null && isResumed()) {
                showOutput(script);
            }
        });

        viewModel.getComposedErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && isResumed() && getView() != null) {
                Snackbar.make(getView(), error, Snackbar.LENGTH_LONG).show();
            }
        });

        viewModel.getHookQueue().observe(getViewLifecycleOwner(), this::updateQueueUI);
    }

    private void onGenerate() {
        NativeSymbol symbol = buildSymbolFromInput();
        if (symbol == null) return;
        viewModel.generateNativeHook(symbol, switchWrapPerform.isChecked());
    }

    private void onAddToQueue() {
        NativeSymbol symbol = buildSymbolFromInput();
        if (symbol == null) return;

        HookRequest request = HookRequest.nativeHook(symbol);
        viewModel.addHook(request);
        clearNativeInputFields();
        if (getView() != null) {
            Snackbar.make(getView(),
                    getString(R.string.msg_hook_added, viewModel.getQueueSize()),
                    Snackbar.LENGTH_SHORT).show();
        }
    }

    /**
     * Validates input and builds a NativeSymbol. Returns null if validation fails.
     */
    private NativeSymbol buildSymbolFromInput() {
        clearFieldErrors();

        boolean isExportMode = radioTargetMode.getCheckedRadioButtonId() == R.id.radio_export;
        boolean hasError = false;

        NativeSymbol.Builder builder = new NativeSymbol.Builder();

        String libName = getText(editLibName);
        builder.libName(libName.isEmpty() ? null : libName);

        if (isExportMode) {
            String exportName = getText(editExportName);

            if (exportName.isEmpty()) {
                layoutExportName.setError(getString(R.string.msg_error_empty_export));
                hasError = true;
            }

            if (switchWaitForLoad.isChecked() && libName.isEmpty()) {
                layoutLibName.setError(getString(R.string.msg_error_waitforload_requires_lib));
                hasError = true;
            }

            if (!hasError) {
                builder.exportName(exportName);

                if (switchWaitForLoad.isChecked()) {
                    builder.waitForLoad(true);
                }
            }
        } else {
            String address = getText(editAddress);
            if (address.isEmpty()) {
                layoutAddressField.setError(getString(R.string.msg_error_empty_address));
                hasError = true;
            }
            if (!hasError) {
                builder.address(address);
            }
        }

        HookViewModel.ParseResult argCountResult =
                HookViewModel.safeParseInt(getText(editArgCount), 0, HookViewModel.MAX_ARG_COUNT);
        if (!argCountResult.isValid()) {
            layoutArgCount.setError(argCountResult.getError());
            hasError = true;
        }

        HookViewModel.ParseResult timeoutResult =
                HookViewModel.safeParseInt(getText(editTimeout), 0, HookViewModel.MAX_TIMEOUT_MS);
        if (!timeoutResult.isValid()) {
            layoutTimeout.setError(timeoutResult.getError());
            hasError = true;
        }

        if (hasError) return null;

        builder.argCount(argCountResult.getValue());
        if (timeoutResult.getValue() > 0) {
            builder.setTimeoutMs(timeoutResult.getValue());
        }

        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            layoutExportName.setError(e.getMessage());
            return null;
        }
    }

    private void onCompose() {
        if (viewModel.getQueueSize() == 0) {
            if (getView() != null) {
                Snackbar.make(getView(), R.string.label_hook_queue_empty, Snackbar.LENGTH_SHORT).show();
            }
            return;
        }
        ComposeDialogHelper.show(requireContext(), options -> viewModel.composeHooks(options));
    }

    private void updateQueueUI(List<HookRequest> queue) {
        if (queue == null || queue.isEmpty()) {
            labelQueue.setVisibility(View.GONE);
            recyclerQueue.setVisibility(View.GONE);
            layoutQueueActions.setVisibility(View.GONE);
        } else {
            labelQueue.setText(getString(R.string.label_hook_queue, queue.size()));
            labelQueue.setVisibility(View.VISIBLE);
            recyclerQueue.setVisibility(View.VISIBLE);
            layoutQueueActions.setVisibility(View.VISIBLE);
            queueAdapter.submitList(queue);
        }
    }

    private void showOutput(String script) {
        labelOutput.setVisibility(View.VISIBLE);
        textOutput.setVisibility(View.VISIBLE);
        textOutput.setText(script);
        btnCopy.setVisibility(View.VISIBLE);
        btnExport.setVisibility(View.VISIBLE);
    }

    private void hideOutput() {
        labelOutput.setVisibility(View.GONE);
        textOutput.setVisibility(View.GONE);
        btnCopy.setVisibility(View.GONE);
        btnExport.setVisibility(View.GONE);
    }

    private void clearFieldErrors() {
        layoutLibName.setError(null);
        layoutExportName.setError(null);
        layoutAddressField.setError(null);
        layoutArgCount.setError(null);
        layoutTimeout.setError(null);
    }

    private void clearNativeInputFields() {
        editLibName.setText("");
        editExportName.setText("");
        editAddress.setText("");
        editArgCount.setText("0");
        editTimeout.setText("0");
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

    private static String getText(TextInputEditText edit) {
        return edit.getText() != null ? edit.getText().toString().trim() : "";
    }
}
