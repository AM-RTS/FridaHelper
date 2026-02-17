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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amrts.fridahelper.core.model.HookRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

/**
 * Fragment for generating Java hook scripts from smali signatures.
 * Supports both single-hook generation and multi-hook queue composition.
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

        viewModel.getJavaScriptOutput().observe(getViewLifecycleOwner(), script -> {
            if (script != null) {
                showOutput(script);
            }
        });

        viewModel.getJavaErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                layoutSignature.setError(error);
                hideOutput();
                if (getView() != null) {
                    Snackbar.make(getView(), error, Snackbar.LENGTH_LONG).show();
                }
            }
        });

        viewModel.getComposedScriptOutput().observe(getViewLifecycleOwner(), script -> {
            if (script != null) {
                showOutput(script);
            }
        });

        viewModel.getComposedErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && getView() != null) {
                Snackbar.make(getView(), error, Snackbar.LENGTH_LONG).show();
            }
        });

        viewModel.getHookQueue().observe(getViewLifecycleOwner(), this::updateQueueUI);
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

    private void onAddToQueue() {
        layoutSignature.setError(null);

        String signature = editSignature.getText() != null
                ? editSignature.getText().toString().trim() : "";

        if (signature.isEmpty()) {
            layoutSignature.setError(getString(R.string.msg_error_empty_signature));
            return;
        }

        try {
            HookRequest request = viewModel.createJavaHookRequest(signature);
            viewModel.addHook(request);
            editSignature.setText("");
            if (getView() != null) {
                Snackbar.make(getView(),
                        getString(R.string.msg_hook_added, viewModel.getQueueSize()),
                        Snackbar.LENGTH_SHORT).show();
            }
        } catch (IllegalArgumentException e) {
            layoutSignature.setError(e.getMessage());
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
