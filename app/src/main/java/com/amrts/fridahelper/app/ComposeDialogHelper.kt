package com.amrts.fridahelper.app

import android.content.Context
import android.view.LayoutInflater
import android.widget.CheckBox
import com.amrts.fridahelper.core.generator.CompositionOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

/**
 * Shows a dialog to collect CompositionOptions (wrapInPerform, setTimeoutMs)
 * before composing a multi-hook script.
 */
object ComposeDialogHelper {

    fun interface OnComposeListener {
        fun onCompose(options: CompositionOptions)
    }

    fun show(context: Context, listener: OnComposeListener) {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_compose_options, null)

        val checkPerform = dialogView.findViewById<CheckBox>(R.id.check_wrap_perform)
        val editTimeout = dialogView.findViewById<TextInputEditText>(R.id.edit_compose_timeout)

        checkPerform.isChecked = true

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_compose_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_compose_positive) { _, _ ->
                val wrapInPerform = checkPerform.isChecked
                val text = editTimeout.text?.toString()?.trim().orEmpty()
                val result = HookViewModel.safeParseInt(text, 0, HookViewModel.MAX_TIMEOUT_MS)
                val timeoutMs = if (result.isValid) result.value else 0

                val options = CompositionOptions.builder()
                    .wrapInPerform(wrapInPerform)
                    .setTimeoutMs(timeoutMs)
                    .build()
                listener.onCompose(options)
            }
            .setNegativeButton(R.string.dialog_compose_negative, null)
            .show()
    }
}
