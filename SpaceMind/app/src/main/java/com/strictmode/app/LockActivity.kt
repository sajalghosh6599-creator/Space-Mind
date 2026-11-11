package com.strictmode.app

import android.app.ActivityManager
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Space screen. Ekhane sudhu "+" diye add kora app gulo dekha jay.
 * Add korte ("+") PIN lage. Remove ("-") korte PIN lage na. Lock Mode ON thakle Home/Recents chaple ei screen e fire ashe.
 */
class LockActivity : AppCompatActivity() {

    private lateinit var pinLauncher: ActivityResultLauncher<Intent>
    private var pending: (() -> Unit)? = null
    private var recentsMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recentsMode = intent.getBooleanExtra("recents", false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (recentsMode) {
                    recentsMode = false
                    render()
                } else if (!Prefs.isLockMode(this@LockActivity)) {
                    finish()
                }
            }
        })
        pinLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val action = pending
            pending = null
            if (r.resultCode == RESULT_OK) action?.invoke()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recentsMode = intent.getBooleanExtra("recents", false)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun askPin(reason: String, action: () -> Unit) {
        pending = action
        pinLauncher.launch(Intent(this, PinActivity::class.java).putExtra("reason", reason))
    }

    private fun exitLock() {
        Prefs.setLockMode(this, false)
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun render() {
        val pad = dp(16)
        val pm = packageManager
        val lock = Prefs.isLockMode(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad + dp(56)) // niche nav bar er jaiga
        }
        root.addView(TextView(this).apply {
            text = if (recentsMode) "Recent apps" else "Space Mind"
            textSize = 24f
        })
        if (!recentsMode) {
            root.addView(TextView(this).apply {
                text = if (lock) "Lock Mode: ON" else "Lock Mode: OFF"
                textSize = 13f
            })
        }

        // Phone er shob launchable app
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installed = pm.queryIntentActivities(launcherIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
        val byPkg = installed.associateBy { it.activityInfo.packageName }
        val space = Prefs.spaceApps(this)

        val list: List<ResolveInfo>
        if (recentsMode) {
            root.addView(Button(this).apply {
                text = "Close all"
                setOnClickListener {
                    val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                    Monitor.recents().forEach { am.killBackgroundProcesses(it) }
                    Monitor.clearRecents()
                    render()
                }
            })
            root.addView(Button(this).apply {
                text = "Space home"
                setOnClickListener {
                    recentsMode = false
                    render()
                }
            })
            list = Monitor.recents().filter { it in space }.mapNotNull { byPkg[it] }
        } else {
            root.addView(Button(this).apply {
                text = "+ Add app (PIN)"
                setOnClickListener {
                    askPin("Enter PIN to add an app to Space") {
                        startActivity(Intent(this@LockActivity, AddAppsActivity::class.java))
                    }
                }
            })
            root.addView(Button(this).apply {
                text = "Recent apps"
                setOnClickListener {
                    recentsMode = true
                    render()
                }
            })
            root.addView(Button(this).apply {
                text = if (lock) "Exit Lock Mode (PIN)" else "Close Space"
                setOnClickListener {
                    if (lock) askPin("Enter PIN to exit Lock Mode") { exitLock() } else finish()
                }
            })
            root.addView(Button(this).apply {
                text = "Space Mind settings (PIN)"
                setOnClickListener { startActivity(Intent(this@LockActivity, MainActivity::class.java)) }
            })
            list = installed.filter { it.activityInfo.packageName in space }
                .sortedBy { it.loadLabel(pm).toString().lowercase() }
        }

        if (list.isEmpty()) {
            root.addView(TextView(this).apply {
                text = if (recentsMode) "\nNo recent apps."
                else "\nNo apps in Space yet. Tap \"+ Add app\" and choose apps from your phone."
                textSize = 16f
            })
        }

        for (ri in list) {
            val pkg = ri.activityInfo.packageName
            val label = ri.loadLabel(pm).toString()
            val blocked = Prefs.isBlocked(this, pkg)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(Button(this).apply {
                val icon = ri.loadIcon(pm)
                icon.setBounds(0, 0, dp(40), dp(40))
                setCompoundDrawables(icon, null, null, null)
                compoundDrawablePadding = dp(12)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                text = label + if (blocked) "  (blocked)" else ""
                isAllCaps = false
                setOnClickListener {
                    if (blocked) {
                        Toast.makeText(
                            this@LockActivity,
                            "This app is blocked. Unblock it from Space Mind settings.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        pm.getLaunchIntentForPackage(pkg)?.let {
                            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            if (!recentsMode) {
                row.addView(Button(this).apply {
                    text = "−"
                    textSize = 20f
                    minWidth = dp(48)
                    minimumWidth = dp(48)
                    setOnClickListener {
                        // Remove korte PIN lage na (app phone theke uninstall hoy na, shudhu Space theke soray)
                        Prefs.setSpaceApp(this@LockActivity, pkg, false)
                        render()
                    }
                })
            }
            root.addView(row)
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
