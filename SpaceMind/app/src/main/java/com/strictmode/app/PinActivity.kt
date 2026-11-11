package com.strictmode.app

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged

class PinActivity : AppCompatActivity() {

    private var fails = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val guard = intent.getBooleanExtra("guard", false)          // Settings guard
        val unblockPkg = intent.getStringExtra("unblock_pkg")       // app unblock
        val mode = Prefs.mode(this)
        val isPin = mode != "password"
        val n = if (isPin) mode.removePrefix("pin").toInt() else 0
        val pad = dp(24)
        val reason = intent.getStringExtra("reason") ?: "Kindly enter your PIN"

        // Guard e back dile Home e pathao
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (guard) goHome()
                finish()
            }
        })

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }
        val title = TextView(this).apply {
            text = reason
            textSize = 20f
            gravity = Gravity.CENTER
        }
        val edit = EditText(this).apply {
            applySecretType(isPin)
            gravity = Gravity.CENTER
            hint = if (isPin) "$n-digit PIN" else "Password"
        }
        val err = TextView(this).apply {
            setTextColor(0xFFCC0000.toInt())
            gravity = Gravity.CENTER
        }
        val ok = Button(this).apply { text = "OK" }
        listOf(title, edit, err, ok).forEach { root.addView(it) }
        setContentView(root)

        fun submit() {
            val s = edit.text.toString()
            if (s.isEmpty()) return
            if (Prefs.checkSecret(this, s)) {
                Session.unlock(if (guard) 5 else 3)
                if (unblockPkg != null) {
                    Prefs.setBlocked(this, unblockPkg, false)
                    Prefs.setTrusted(this, unblockPkg, true)
                    Toast.makeText(this, "App unblocked", Toast.LENGTH_SHORT).show()
                }
                setResult(RESULT_OK)
                finish()
                return
            }
            fails++
            edit.text.clear()
            if (fails % 5 == 0) {
                ok.isEnabled = false
                edit.isEnabled = false
                object : CountDownTimer(30_000, 1000) {
                    override fun onTick(ms: Long) {
                        err.text = "Too many wrong attempts. Please wait ${ms / 1000} seconds"
                    }
                    override fun onFinish() {
                        ok.isEnabled = true
                        edit.isEnabled = true
                        err.text = ""
                    }
                }.start()
            } else {
                err.text = "Wrong PIN. Please try again"
            }
        }

        ok.setOnClickListener { submit() }
        if (isPin) edit.doAfterTextChanged { if (it?.length == n) submit() }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
