package com.strictmode.app

import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AppsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.isUnlocked()) {
            finish(); return
        }
        val mode = intent.getStringExtra("mode") ?: "trusted"
        val pad = dp(16)

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val all = packageManager.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .distinctBy { it.first }
            .filter { it.first != packageName }
            .sortedBy { it.second.lowercase() }

        // blocked screen e blocked app gulo uporer dike
        val list = if (mode == "blocked") {
            all.sortedBy { if (Prefs.isBlocked(this, it.first)) 0 else 1 }
        } else all

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            textSize = 16f
            text = if (mode == "trusted")
                "Ticked apps will not be checked by AI. Untick to monitor the app."
            else
                "Tick to block an app. Untick to unblock it; it will move to the trusted list."
        })

        for ((pkg, label) in list) {
            root.addView(CheckBox(this).apply {
                text = "$label\n$pkg"
                isChecked = if (mode == "trusted") Prefs.trusted(this@AppsActivity).contains(pkg)
                            else Prefs.isBlocked(this@AppsActivity, pkg)
                setOnCheckedChangeListener { _, on ->
                    if (mode == "trusted") {
                        Prefs.setTrusted(this@AppsActivity, pkg, on)
                    } else if (on) {
                        Prefs.setTrusted(this@AppsActivity, pkg, false)
                        Prefs.setBlocked(this@AppsActivity, pkg, true)
                    } else {
                        Prefs.setBlocked(this@AppsActivity, pkg, false)
                        Prefs.setTrusted(this@AppsActivity, pkg, true)
                    }
                }
            })
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
