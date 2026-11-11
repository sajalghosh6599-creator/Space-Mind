package com.strictmode.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * Prothom setup er shesh dhap. Shob dorkari permission ✓ na hole "Finish" hobe na,
 * ar Setup complete na hole Space Mind app er baki ongsho khulbe na.
 */
class PermissionsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Back dile app theke berie jao (Main e firle abar ei screen ashbe)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishAffinity()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (!Session.isUnlocked()) {
            finish(); return
        }
        Session.unlock(15) // Settings e ghurte shomoy lagbe
        render()
    }

    private fun render() {
        val pad = dp(20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Setup: Grant all permissions"; textSize = 22f })
        root.addView(TextView(this).apply {
            text = "The app will not work until everything is ✓."
            setPadding(0, pad / 2, 0, pad / 2)
        })

        fun status(title: String, ok: Boolean, help: String) {
            root.addView(TextView(this).apply {
                text = (if (ok) "✓ " else "✗ ") + title
                textSize = 18f
                setTextColor(if (ok) 0xFF2E7D32.toInt() else 0xFFCC0000.toInt())
                setPadding(0, pad / 2, 0, 0)
            })
            if (!ok) root.addView(TextView(this).apply { text = help; textSize = 13f })
        }
        fun button(label: String, onClick: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                setOnClickListener { onClick() }
            })
        }

        // ---------- 1. Accessibility ----------
        val acc = Perms.accessibility(this)
        status(
            "Accessibility (app detection and AI check)", acc,
            "Android 13: First open App info, tap the 3 dots at the top and select Allow restricted settings. " +
                "If you do not see this option, tap Space Mind once in Accessibility; the 3 dots will then appear."
        )
        if (!acc) {
            if (Build.VERSION.SDK_INT >= 33) button("1. App info (Allow restricted settings)") { openAppDetails() }
            button("2. Open Accessibility Settings") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        // ---------- 2. Device admin ----------
        val adm = Perms.admin(this)
        status("Uninstall protection (Device Admin)", adm, "Tap Activate.")
        if (!adm) button("Turn on Device Admin") {
            startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this, AdminReceiver::class.java))
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "A PIN is required to uninstall or turn off Space Mind.")
            )
        }

        // ---------- 3. Battery ----------
        val bat = Perms.battery(this)
        status("Battery: keep running in background", bat, "Tap Allow.")
        if (!bat) button("Remove battery restriction") {
            tryOpen(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
        }

        // ---------- 4. Overlay ----------
        val ovl = Perms.overlay(this)
        status("Display over other apps (block screen and PIN screen)", ovl, "Allow Space Mind.")
        if (!ovl) button("Open Display over other apps") {
            tryOpen(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        // ---------- Xiaomi / Redmi: app check korte pare na, tai tumi confirm korbe ----------
        var manualOk = true
        if (Perms.isXiaomi()) {
            root.addView(TextView(this).apply {
                text = "\nExtra steps for Redmi/Xiaomi (the app cannot verify these, so kindly do them manually and tick):"
                textSize = 16f
            })
            fun manual(key: String, text: String, open: (() -> Unit)?) {
                val done = Prefs.flag(this, key)
                if (!done) manualOk = false
                root.addView(CheckBox(this).apply {
                    this.text = text
                    isChecked = done
                    setOnCheckedChangeListener { _, v ->
                        Prefs.setFlag(this@PermissionsActivity, key, v)
                        render()
                    }
                })
                if (open != null) button("Open") { open() }
            }
            manual("m_autostart", "Autostart: Space Mind ON", {
                tryOpen(
                    Intent().setComponent(
                        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                    )
                )
            })
            manual("m_battery", "Battery saver: No restrictions (in App info)", { openAppDetails() })
            manual("m_popup", "Other permissions: Display pop-up windows while running in background + Show on lock screen ON", {
                tryOpen(
                    Intent("miui.intent.action.APP_PERM_EDITOR")
                        .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                        .putExtra("extra_pkgname", packageName)
                )
            })
            manual("m_recents", "Lock the Space Mind card in Recent apps (press and hold the card, then tap Lock)", null)
        }

        // ---------- Finish ----------
        val allOk = Perms.allRequired(this) && manualOk
        root.addView(Button(this).apply {
            text = if (allOk) "Finish" else "Complete all ✓ to finish"
            isEnabled = allOk
            setOnClickListener {
                Prefs.setSetupComplete(this@PermissionsActivity, true)
                finish()
            }
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun openAppDetails() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    /** Prothom intent na khulle porer-ta, shesh e App info. */
    private fun tryOpen(vararg intents: Intent) {
        for (i in intents) {
            try {
                startActivity(i); return
            } catch (e: Exception) {
            }
        }
        openAppDetails()
    }
}
