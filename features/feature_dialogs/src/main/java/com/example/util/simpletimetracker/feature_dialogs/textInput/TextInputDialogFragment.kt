package com.example.util.simpletimetracker.feature_dialogs.textInput

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import com.example.util.simpletimetracker.core.dialog.TextInputDialogListener
import com.example.util.simpletimetracker.core.extension.findListeners
import com.example.util.simpletimetracker.core.utils.fragmentArgumentDelegate
import com.example.util.simpletimetracker.feature_dialogs.R
import com.example.util.simpletimetracker.feature_dialogs.databinding.TextInputDialogFragmentBinding
import com.example.util.simpletimetracker.navigation.params.screen.TextInputDialogParams
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TextInputDialogFragment :
    AppCompatDialogFragment(),
    DialogInterface.OnClickListener {

    private var listeners: List<TextInputDialogListener> = emptyList()
    private val params: TextInputDialogParams by fragmentArgumentDelegate(
        key = ARGS_PARAMS,
        default = TextInputDialogParams.Empty,
    )

    private val binding: TextInputDialogFragmentBinding
        get() = _binding!!
    private var _binding: TextInputDialogFragmentBinding? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = TextInputDialogFragmentBinding.inflate(layoutInflater)

        binding.inputTextInputDialog.hint = params.hint
        binding.etTextInputDialog.apply {
            setText(params.value)
            setSelection(params.value.length)
            inputType = params.inputType
        }

        return MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogRounded).apply {
            if (params.title.isNotEmpty()) setTitle(params.title)
            setView(binding.root)
            if (params.btnPositive.isNotEmpty()) {
                setPositiveButton(params.btnPositive, this@TextInputDialogFragment)
            }
            if (params.btnNegative.isNotEmpty()) {
                setNegativeButton(params.btnNegative, this@TextInputDialogFragment)
            }
        }.create()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listeners = context.findListeners<TextInputDialogListener>()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun onClick(dialog: DialogInterface?, which: Int) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            val text = binding.etTextInputDialog.text?.toString().orEmpty()
            listeners.forEach { it.onTextInputConfirmed(text, params.tag) }
        }
    }

    companion object {
        private const val ARGS_PARAMS = "args_params"

        fun createBundle(data: TextInputDialogParams): Bundle = Bundle().apply {
            putParcelable(ARGS_PARAMS, data)
        }
    }
}
