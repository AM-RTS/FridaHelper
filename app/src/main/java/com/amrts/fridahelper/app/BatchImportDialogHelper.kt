package com.amrts.fridahelper.app

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.amrts.fridahelper.core.batch.BatchFilter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.regex.PatternSyntaxException

/**
 * Shows a dialog to collect a .smali file/directory path and filter options,
 * then triggers batch import into the hook queue.
 */
object BatchImportDialogHelper {

    fun interface OnImportListener {
        fun onImport(path: String, filter: BatchFilter)
    }

    fun show(context: Context, listener: OnImportListener) {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_batch_import, null)

        val layoutPath = dialogView.findViewById<TextInputLayout>(R.id.layout_import_path)
        val editPath = dialogView.findViewById<TextInputEditText>(R.id.edit_import_path)
        val switchSkipCtors = dialogView.findViewById<MaterialSwitch>(R.id.switch_skip_constructors)
        val layoutInclude = dialogView.findViewById<TextInputLayout>(R.id.layout_include_classes)
        val editInclude = dialogView.findViewById<TextInputEditText>(R.id.edit_include_classes)
        val layoutExclude = dialogView.findViewById<TextInputLayout>(R.id.layout_exclude_methods)
        val editExclude = dialogView.findViewById<TextInputEditText>(R.id.edit_exclude_methods)

        switchSkipCtors.isChecked = true

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_import_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_import_positive, null)
            .setNegativeButton(R.string.dialog_import_negative, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                layoutPath.error = null
                layoutInclude.error = null
                layoutExclude.error = null

                val path = editPath.text?.toString()?.trim().orEmpty()
                val includeClasses = editInclude.text?.toString()?.trim().orEmpty()
                val excludeMethods = editExclude.text?.toString()?.trim().orEmpty()

                if (path.isEmpty()) {
                    layoutPath.error = context.getString(R.string.msg_error_empty_path)
                    return@setOnClickListener
                }
                if (includeClasses.isNotEmpty() && !isValidRegex(includeClasses)) {
                    layoutInclude.error = context.getString(R.string.msg_error_invalid_regex)
                    return@setOnClickListener
                }
                if (excludeMethods.isNotEmpty() && !isValidRegex(excludeMethods)) {
                    layoutExclude.error = context.getString(R.string.msg_error_invalid_regex)
                    return@setOnClickListener
                }

                val builder = BatchFilter.builder()
                    .skipConstructors(switchSkipCtors.isChecked)
                if (includeClasses.isNotEmpty()) builder.includeClasses(includeClasses)
                if (excludeMethods.isNotEmpty()) builder.excludeMethods(excludeMethods)

                dialog.dismiss()
                listener.onImport(path, builder.build())
            }
        }

        dialog.show()
    }

    private fun isValidRegex(regex: String): Boolean = try {
        java.util.regex.Pattern.compile(regex)
        true
    } catch (_: PatternSyntaxException) {
        false
    }
}
