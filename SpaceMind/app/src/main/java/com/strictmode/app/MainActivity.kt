package com.strictmode.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class MainActivity : AppCompatActivity() {

    private lateinit var pinLauncher: ActivityResultLauncher<Intent>
    private var waitingPin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pinLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            waitingPin = false
            if (r.resultCode == RESULT_OK) routeAfterUnlock() else finish()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!Prefs.isSetupDone(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        if (Session.isUnlocked()) {
            routeAfterUnlock()
        } else if (!waitingPin) {
            waitingPin = true
            pinLauncher.launch(
                Intent(this, PinActivity::class.java).putExtra("reason", "Enter PIN to open settings")
            )
        }
    }

    /** Setup poripurno na hole ba kono dorkari permission gele Permissions screen e pathao. */
    private fun routeAfterUnlock() {
        if (!Prefs.isSetupComplete(this) || !Perms.allRequired(this)) {
            startActivity(Intent(this, PermissionsActivity::class.java))
        } else {
            render()
        }
    }

    private fun render() {
        val pad = dp(20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }

        root.addView(TextView(this).apply { text = "Space Mind"; textSize = 24f })

        // ---- Guard (Accessibility) status ----
        val on = Monitor.isGuardEnabled(this)
        root.addView(TextView(this).apply {
            text = if (on) "Guard: ON" else "Guard: OFF (turn on Accessibility)"
            textSize = 18f
            setTextColor(if (on) 0xFF2E7D32.toInt() else 0xFFCC0000.toInt())
            setPadding(0, pad / 2, 0, pad / 2)
        })
        if (!on) {
            root.addView(btn("1. Open Accessibility Settings") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
            root.addView(btn("2. Open App info (Allow restricted settings)") {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            })
            root.addView(TextView(this).apply {
                text = "Android 13: If Space Mind is greyed out in Accessibility, or you see \"Restricted setting\", " +
                    "open App info, tap the ⋮ (3 dots) at the top right and select Allow restricted settings. " +
                    "Then go to Accessibility and turn Space Mind ON."
                setPadding(0, pad / 2, 0, pad / 2)
            })
        }

        // ---- Uninstall protection (Device Admin) ----
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, AdminReceiver::class.java)
        val adminOn = dpm.isAdminActive(admin)
        root.addView(TextView(this).apply {
            text = if (adminOn) "Uninstall protection: ON" else "Uninstall protection: OFF"
            textSize = 18f
            setTextColor(if (adminOn) 0xFF2E7D32.toInt() else 0xFFCC0000.toInt())
            setPadding(0, 0, 0, pad / 2)
        })
        if (!adminOn) {
            root.addView(btn("Turn on uninstall protection (Device Admin)") {
                startActivity(
                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                        .putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "A PIN is required to uninstall or turn off Space Mind."
                        )
                )
            })
        }

        root.addView(SwitchCompat(this).apply {
            text = "AI protection ON"
            isChecked = Prefs.isStrict(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setStrict(this@MainActivity, v) }
        })

        root.addView(SwitchCompat(this).apply {
            text = "Lock Mode (Home / Recents returns to Space Mind)"
            isChecked = Prefs.isLockMode(this@MainActivity)
            setOnCheckedChangeListener { _, v ->
                Prefs.setLockMode(this@MainActivity, v)
                if (v) startActivity(Intent(this@MainActivity, LockActivity::class.java))
            }
        })

        root.addView(TextView(this).apply {
            text = "Trusted apps: ${Prefs.trusted(this@MainActivity).size}   |   Blocked apps: ${Prefs.blocked(this@MainActivity).size}"
            setPadding(0, pad / 2, 0, 0)
        })
        root.addView(TextView(this).apply {
            text = "Last detected app: ${Monitor.lastApp ?: "none"}"
        })
        root.addView(TextView(this).apply {
            text = "AI model: ${Monitor.modelStatus}\nLast AI score: ${Monitor.lastScore}"
            setPadding(0, 0, 0, pad / 2)
        })

        root.addView(TextView(this).apply {
            text = "Model info: ${Monitor.modelInfo}"
            textSize = 12f
        })
        root.addView(btn("AI preprocess: ${Prefs.preprocess(this)} (tap to change)") {
            val next = if (Prefs.preprocess(this) == "bgr_mean") "rgb01" else "bgr_mean"
            Prefs.setPreprocess(this, next)
            Monitor.preprocess = next
            render()
        })
        root.addView(btn("Open Space") {
            startActivity(Intent(this, LockActivity::class.java))
        })
        root.addView(btn("Trusted apps (no AI check)") {
            startActivity(Intent(this, AppsActivity::class.java).putExtra("mode", "trusted"))
        })
        root.addView(btn("Block / unblock apps") {
            startActivity(Intent(this, AppsActivity::class.java).putExtra("mode", "blocked"))
        })
        root.addView(btn("Change PIN / password") {
            startActivity(Intent(this, SetupActivity::class.java).putExtra("change", true))
        })
        root.addView(btn("Lock and exit") {
            Session.lock()
            finish()
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }
}
