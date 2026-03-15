package com.amrts.fridahelper.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amrts.fridahelper.core.model.HookRequest
import com.amrts.fridahelper.core.model.NativeSymbol
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Fragment for generating native hook scripts.
 * Supports both single-hook generation and multi-hook queue composition.
 */
class NativeHookFragment : Fragment() {

    private lateinit var viewModel: HookViewModel
    private lateinit var radioTargetMode: RadioGroup
    private lateinit var layoutLibName: TextInputLayout
    private lateinit var layoutExportName: TextInputLayout
    private lateinit var layoutAddressField: TextInputLayout
    private lateinit var layoutArgCount: TextInputLayout
    private lateinit var layoutTimeout: TextInputLayout
    private lateinit var editLibName: TextInputEditText
    private lateinit var editExportName: TextInputEditText
    private lateinit var editAddress: TextInputEditText
    private lateinit var editArgCount: TextInputEditText
    private lateinit var editTimeout: TextInputEditText
    private lateinit var switchWaitForLoad: MaterialSwitch
    private lateinit var switchWrapPerform: MaterialSwitch
    private lateinit var outputHelper: ScriptOutputHelper

    private lateinit var labelQueue: TextView
    private lateinit var recyclerQueue: RecyclerView
    private lateinit var layoutQueueActions: View
    private lateinit var queueAdapter: HookQueueAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_native_hook, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[HookViewModel::class.java]

        radioTargetMode = view.findViewById(R.id.radio_target_mode)
        layoutLibName = view.findViewById(R.id.layout_lib_name)
        layoutExportName = view.findViewById(R.id.layout_export_name)
        layoutAddressField = view.findViewById(R.id.layout_address_field)
        layoutArgCount = view.findViewById(R.id.layout_arg_count)
        layoutTimeout = view.findViewById(R.id.layout_timeout)
        editLibName = view.findViewById(R.id.edit_lib_name)
        editExportName = view.findViewById(R.id.edit_export_name)
        editAddress = view.findViewById(R.id.edit_address)
        editArgCount = view.findViewById(R.id.edit_arg_count)
        editTimeout = view.findViewById(R.id.edit_timeout)
        switchWaitForLoad = view.findViewById(R.id.switch_wait_for_load)
        switchWrapPerform = view.findViewById(R.id.switch_wrap_perform)
        val btnGenerate: MaterialButton = view.findViewById(R.id.btn_generate)
        val btnAddToQueue: MaterialButton = view.findViewById(R.id.btn_add_to_queue)

        outputHelper = ScriptOutputHelper(view.findViewById(R.id.coordinator))

        labelQueue = view.findViewById(R.id.label_queue)
        recyclerQueue = view.findViewById(R.id.recycler_queue)
        layoutQueueActions = view.findViewById(R.id.layout_queue_actions)
        val btnCompose: MaterialButton = view.findViewById(R.id.btn_compose)
        val btnClearQueue: MaterialButton = view.findViewById(R.id.btn_clear_queue)

        recyclerQueue.layoutManager = LinearLayoutManager(requireContext())
        queueAdapter = HookQueueAdapter()
        queueAdapter.onDeleteListener = HookQueueAdapter.OnDeleteListener { position ->
            viewModel.removeHook(position)
        }
        recyclerQueue.adapter = queueAdapter

        radioTargetMode.setOnCheckedChangeListener { _, checkedId ->
            val isExport = checkedId == R.id.radio_export
            layoutExportName.visibility = if (isExport) View.VISIBLE else View.GONE
            layoutAddressField.visibility = if (isExport) View.GONE else View.VISIBLE
        }

        btnGenerate.setOnClickListener { onGenerate() }
        btnAddToQueue.setOnClickListener { onAddToQueue() }
        btnCompose.setOnClickListener { onCompose() }
        btnClearQueue.setOnClickListener {
            viewModel.clearHooks()
            snackbar(R.string.msg_queue_cleared, Snackbar.LENGTH_SHORT)
        }

        viewModel.isGenerating.observe(viewLifecycleOwner) { generating ->
            val enabled = generating != true
            btnGenerate.isEnabled = enabled
            btnAddToQueue.isEnabled = enabled
        }

        viewModel.nativeScriptOutput.observe(viewLifecycleOwner) { script ->
            if (script != null) outputHelper.show(script)
        }

        viewModel.nativeErrorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                outputHelper.hide()
                snackbar(error, Snackbar.LENGTH_LONG)
            }
        }

        viewModel.composedScriptOutput.observe(viewLifecycleOwner) { script ->
            if (script != null && isResumed) outputHelper.show(script)
        }

        viewModel.composedErrorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null && isResumed) snackbar(error, Snackbar.LENGTH_LONG)
        }

        viewModel.hookQueue.observe(viewLifecycleOwner) { queue -> updateQueueUI(queue) }
    }

    // ========== Single-hook generation ==========

    private fun onGenerate() {
        val symbol = buildSymbolFromInput() ?: return
        viewModel.generateNativeHook(symbol, switchWrapPerform.isChecked)
    }

    private fun onAddToQueue() {
        val symbol = buildSymbolFromInput() ?: return
        val request = HookRequest.nativeHook(symbol)
        viewModel.addHook(request)
        clearNativeInputFields()
        snackbar(getString(R.string.msg_hook_added, viewModel.queueSize), Snackbar.LENGTH_SHORT)
    }

    private fun buildSymbolFromInput(): NativeSymbol? {
        clearFieldErrors()

        val isExportMode = radioTargetMode.checkedRadioButtonId == R.id.radio_export
        var hasError = false

        val builder = NativeSymbol.Builder()

        val libName = getText(editLibName)
        builder.libName(libName.ifEmpty { null })

        if (isExportMode) {
            val exportName = getText(editExportName)

            if (exportName.isEmpty()) {
                layoutExportName.error = getString(R.string.msg_error_empty_export)
                hasError = true
            }

            if (switchWaitForLoad.isChecked && libName.isEmpty()) {
                layoutLibName.error = getString(R.string.msg_error_waitforload_requires_lib)
                hasError = true
            }

            if (!hasError) {
                builder.exportName(exportName)
                if (switchWaitForLoad.isChecked) builder.waitForLoad(true)
            }
        } else {
            val address = getText(editAddress)
            if (address.isEmpty()) {
                layoutAddressField.error = getString(R.string.msg_error_empty_address)
                hasError = true
            }
            if (!hasError) builder.address(address)
        }

        val argCountResult = HookViewModel.safeParseInt(getText(editArgCount), 0, HookViewModel.MAX_ARG_COUNT)
        if (!argCountResult.isValid) {
            layoutArgCount.error = argCountResult.error
            hasError = true
        }

        val timeoutResult = HookViewModel.safeParseInt(getText(editTimeout), 0, HookViewModel.MAX_TIMEOUT_MS)
        if (!timeoutResult.isValid) {
            layoutTimeout.error = timeoutResult.error
            hasError = true
        }

        if (hasError) return null

        builder.argCount(argCountResult.value)
        if (timeoutResult.value > 0) builder.setTimeoutMs(timeoutResult.value)

        return try {
            builder.build()
        } catch (e: IllegalArgumentException) {
            layoutExportName.error = e.message
            null
        }
    }

    // ========== Compose ==========

    private fun onCompose() {
        if (viewModel.queueSize == 0) {
            snackbar(R.string.label_hook_queue_empty, Snackbar.LENGTH_SHORT)
            return
        }
        ComposeDialogHelper.show(requireContext()) { options -> viewModel.composeHooks(options) }
    }

    private fun updateQueueUI(queue: List<HookRequest>?) {
        if (queue.isNullOrEmpty()) {
            labelQueue.visibility = View.GONE
            recyclerQueue.visibility = View.GONE
            layoutQueueActions.visibility = View.GONE
        } else {
            labelQueue.text = getString(R.string.label_hook_queue, queue.size)
            labelQueue.visibility = View.VISIBLE
            recyclerQueue.visibility = View.VISIBLE
            layoutQueueActions.visibility = View.VISIBLE
            queueAdapter.submitList(queue)
        }
    }

    // ========== Helpers ==========

    private fun clearFieldErrors() {
        layoutLibName.error = null
        layoutExportName.error = null
        layoutAddressField.error = null
        layoutArgCount.error = null
        layoutTimeout.error = null
    }

    private fun clearNativeInputFields() {
        editLibName.setText("")
        editExportName.setText("")
        editAddress.setText("")
        editArgCount.setText("0")
        editTimeout.setText("0")
    }

    private fun snackbar(resId: Int, duration: Int) {
        view?.findViewById<View>(R.id.coordinator)?.let {
            Snackbar.make(it, resId, duration).show()
        }
    }

    private fun snackbar(text: CharSequence, duration: Int) {
        view?.findViewById<View>(R.id.coordinator)?.let {
            Snackbar.make(it, text, duration).show()
        }
    }

    companion object {
        private fun getText(edit: TextInputEditText): String =
            edit.text?.toString()?.trim().orEmpty()
    }
}
