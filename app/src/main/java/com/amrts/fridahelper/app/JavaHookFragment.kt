package com.amrts.fridahelper.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amrts.fridahelper.core.model.HookRequest
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Fragment for generating Java hook scripts from smali signatures.
 * Supports both single-hook generation and multi-hook queue composition.
 */
class JavaHookFragment : Fragment() {

    private lateinit var viewModel: HookViewModel
    private lateinit var layoutSignature: TextInputLayout
    private lateinit var layoutTimeout: TextInputLayout
    private lateinit var editSignature: TextInputEditText
    private lateinit var editTimeout: TextInputEditText
    private lateinit var switchFullScript: MaterialSwitch
    private lateinit var outputHelper: ScriptOutputHelper

    private lateinit var labelQueue: TextView
    private lateinit var recyclerQueue: RecyclerView
    private lateinit var layoutQueueActions: View
    private lateinit var queueAdapter: HookQueueAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_java_hook, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[HookViewModel::class.java]

        layoutSignature = view.findViewById(R.id.layout_smali_signature)
        layoutTimeout = view.findViewById(R.id.layout_timeout)
        editSignature = view.findViewById(R.id.edit_smali_signature)
        editTimeout = view.findViewById(R.id.edit_timeout)
        switchFullScript = view.findViewById(R.id.switch_full_script)
        val btnGenerate: MaterialButton = view.findViewById(R.id.btn_generate)
        val btnAddToQueue: MaterialButton = view.findViewById(R.id.btn_add_to_queue)
        val btnImport: MaterialButton = view.findViewById(R.id.btn_import_smali)

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

        btnGenerate.setOnClickListener { onGenerate() }
        btnAddToQueue.setOnClickListener { onAddToQueue() }
        btnImport.setOnClickListener { onImportClicked() }
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

        viewModel.javaScriptOutput.observe(viewLifecycleOwner) { script ->
            if (script != null) outputHelper.show(script)
        }

        viewModel.javaErrorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                layoutSignature.error = error
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

        viewModel.importResult.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe
            when (result.status) {
                HookViewModel.ImportResult.Status.SUCCESS ->
                    snackbar(getString(R.string.msg_import_success, result.count), Snackbar.LENGTH_SHORT)
                HookViewModel.ImportResult.Status.EMPTY ->
                    snackbar(R.string.msg_import_empty, Snackbar.LENGTH_LONG)
                HookViewModel.ImportResult.Status.ERROR ->
                    snackbar(result.errorMessage ?: "Unknown error", Snackbar.LENGTH_LONG)
            }
        }
    }

    // ========== Import ==========

    private fun onImportClicked() {
        if (!hasStorageAccess()) {
            requestStorageAccess()
            return
        }
        BatchImportDialogHelper.show(requireContext()) { path, filter ->
            viewModel.importSmaliMethods(path, filter)
        }
    }

    private fun hasStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            snackbar("Grant storage access, then try importing again", Snackbar.LENGTH_LONG)
        } else {
            @Suppress("DEPRECATION")
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE
            && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            onImportClicked()
        } else {
            snackbar("Storage permission required to read .smali files", Snackbar.LENGTH_LONG)
        }
    }

    // ========== Single-hook generation ==========

    private fun onGenerate() {
        layoutSignature.error = null
        layoutTimeout.error = null

        val signature = editSignature.text?.toString()?.trim().orEmpty()
        if (signature.isEmpty()) {
            layoutSignature.error = getString(R.string.msg_error_empty_signature)
            return
        }

        val timeoutText = editTimeout.text?.toString()?.trim().orEmpty()
        val timeoutResult = HookViewModel.safeParseInt(timeoutText, 0, HookViewModel.MAX_TIMEOUT_MS)
        if (!timeoutResult.isValid) {
            layoutTimeout.error = timeoutResult.error
            return
        }

        viewModel.generateJavaHook(signature, switchFullScript.isChecked, timeoutResult.value)
    }

    // ========== Queue management ==========

    private fun onAddToQueue() {
        layoutSignature.error = null

        val signature = editSignature.text?.toString()?.trim().orEmpty()
        if (signature.isEmpty()) {
            layoutSignature.error = getString(R.string.msg_error_empty_signature)
            return
        }

        try {
            val request = viewModel.createJavaHookRequest(signature)
            viewModel.addHook(request)
            editSignature.setText("")
            snackbar(getString(R.string.msg_hook_added, viewModel.queueSize), Snackbar.LENGTH_SHORT)
        } catch (e: IllegalArgumentException) {
            layoutSignature.error = e.message
        }
    }

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

    // ========== Snackbar helpers ==========

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
        private const val PERMISSION_REQUEST_CODE = 100
    }
}
