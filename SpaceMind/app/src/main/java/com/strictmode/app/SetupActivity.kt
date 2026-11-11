package com.strictmode.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {

    private val labels = listOf("4 digit PIN", "6 digit PIN", "8 digit PIN", "10 digit PIN", "Password (letters + numbers)")
    private val modes = listOf("pin4", "pin6", "pin8", "pin10", "password")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val change = intent.getBooleanExtra("change", false)
        // Setup ekbar hoye gele PIN chara abar khola jabe na
        if (Prefs.isSetupDone(this) && !(change && Session.isUnlocked())) {
            finish(); return
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val pad = dp(24)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad + pad / 2, pad, pad)
        }
        val title = TextView(this).apply {
            text = if (change) "New PIN / password" else "First-time setup: Kindly set a PIN or password"
            textSize = 20f
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SetupActivity, android.R.layout.simple_spinner_dropdown_item, labels)
        }
        val first = EditText(this).apply { hint = "PIN / password" }
        val second = EditText(this).apply { hint = "Enter again" }
        val msg = TextView(this).apply { setTextColor(0xFFCC0000.toInt()) }
        val note = TextView(this).apply {
            text = "If you forget the PIN, the app cannot be turned off or uninstalled. Kindly note it down in a safe place."
        }
        val save = Button(this).apply { text = "Save" }

        listOf(title, spinner, first, second, msg, note, save).forEach { root.addView(it) }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val pin = modes[pos] != "password"
                first.applySecretType(pin)
                second.applySecretType(pin)
                first.text.clear()
                second.text.clear()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        save.setOnClickListener {
            val mode = modes[spinner.selectedItemPosition]
            val a = first.text.toString()
            val err = validate(mode, a, second.text.toString())
            if (err != null) {
                msg.text = err
                return@setOnClickListener
            }
            Prefs.setSecret(this, a, mode)
            Session.unlock()
            if (!change) startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun validate(mode: String, a: String, b: String): String? {
        if (a != b) return "Both entries do not match"
        if (mode == "password") {
            if (a.length < 8 || a.none { it.isLetter() } || a.none { it.isDigit() })
                return "Password must have at least 8 characters, with both letters and numbers"
        } else {
            val n = mode.removePrefix("pin").toInt()
            if (a.length != n || a.any { !it.isDigit() }) return "Please enter exactly $n digits"
        }
        return null
    }
}
