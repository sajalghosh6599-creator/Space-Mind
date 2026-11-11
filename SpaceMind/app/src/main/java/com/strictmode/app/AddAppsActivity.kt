package com.strictmode.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Phone er shob app er list. "+" chaple app Space e add hoy. PIN dewar por-i ei screen khola jay. */
class AddAppsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.isUnlocked()) {
            finish(); return
        }
        render()
    }

    private fun render() {
        val pad = dp(16)
        val pm = packageManager
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad + dp(56))
        }
        root.addView(TextView(this).apply {
            text = "Add apps to Space"
            textSize = 22f
        })
        root.addView(Button(this).apply {
            text = "Done"
            setOnClickListener { finish() }
        })

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcherIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        val space = Prefs.spaceApps(this)

        for (ri in apps) {
            val pkg = ri.activityInfo.packageName
            val added = pkg in space
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            row.addView(TextView(this).apply {
                val icon = ri.loadIcon(pm)
                icon.setBounds(0, 0, dp(40), dp(40))
                setCompoundDrawables(icon, null, null, null)
                compoundDrawablePadding = dp(12)
                gravity = Gravity.CENTER_VERTICAL
                text = ri.loadLabel(pm).toString()
                textSize = 16f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                if (added) {
                    text = "Added ✓"
                    isEnabled = false
                } else {
                    text = "+"
                    textSize = 20f
                    setOnClickListener {
                        Prefs.setSpaceApp(this@AddAppsActivity, pkg, true)
                        render()
                    }
                }
            })
            root.addView(row)
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
