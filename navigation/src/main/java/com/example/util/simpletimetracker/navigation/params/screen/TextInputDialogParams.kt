package com.example.util.simpletimetracker.navigation.params.screen

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TextInputDialogParams(
    val tag: String? = null,
    val title: String,
    val hint: String = "",
    val value: String = "",
    val inputType: Int = android.text.InputType.TYPE_CLASS_TEXT,
    val btnPositive: String,
    val btnNegative: String,
) : Parcelable, ScreenParams {

    companion object {
        val Empty = TextInputDialogParams(
            title = "",
            btnPositive = "",
            btnNegative = "",
        )
    }
}
