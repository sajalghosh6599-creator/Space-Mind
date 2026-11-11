package com.strictmode.app

import android.content.Context
import android.text.InputType
import android.widget.EditText

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun EditText.applySecretType(pin: Boolean) {
    inputType = if (pin) {
        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    } else {
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }
}
