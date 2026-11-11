package com.strictmode.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import java.util.concurrent.Executors

class GuardService : AccessibilityService() {

    companion object {
        private const val SCAN_MS = 1200L   // protita koto millisecond e screen check
    }

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val overlay by lazy { BlockOverlay(this) }
    private val navBar by lazy {
        NavBarOverlay(
            this,
            onRecents = { openLock(recents = true, force = true) },
            onHome = { openLock(force = true) },
            onBack = { performGlobalAction(GLOBAL_ACTION_BACK) }
        )
    }

    @Volatile private var detector: NsfwDetector? = null
    @Volatile private var busy = false

    private var launchers: Set<String> = emptySet()
    private var imes: Set<String> = emptySet()
    private var lastRefresh = 0L
    private var strikes = 0
    private var strikePkg: String? = null
    private var lastGuardAt = 0L
    private var lastContentCheckAt = 0L
    private var lastLockAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            syncNavBar()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) scanOnce()
            handler.postDelayed(this, SCAN_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        refreshSets()
        Monitor.preprocess = Prefs.preprocess(this)
        worker.execute {
            try {
                detector = NsfwDetector(this)
                Monitor.modelStatus = "ready"
            } catch (t: Throwable) {
                Monitor.modelStatus = "failed to load: ${t.javaClass.simpleName} (is assets/nsfw.tflite present?)"
            }
        }
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, SCAN_MS)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        Monitor.target = null
        overlay.dismiss()
        navBar.hide()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        overlay.dismiss()
        navBar.hide()
        worker.shutdown()
        detector?.close()
        super.onDestroy()
    }

    // ---------------- App detect + Settings guard ----------------

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null) return
        val pkg = e.packageName?.toString() ?: return

        // Settings er content bodlale (fragment change) guard abar dekho
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (pkg in Monitor.NEUTRAL) {
                val now = System.currentTimeMillis()
                if (now - lastContentCheckAt > 700) {
                    lastContentCheckAt = now
                    scheduleGuard(300)
                }
            }
            return
        }
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        if (System.currentTimeMillis() - lastRefresh > 60_000) refreshSets()

        if (pkg in Monitor.NEUTRAL) {
            Monitor.target = null
            scheduleGuard(400, 1200)
            return
        }
        if (pkg == packageName) {
            Monitor.target = null
            return
        }
        // Home screen ba Recents: Lock Mode ON thakle Space Mind er Lock screen e pathao
        val isHome = pkg in launchers ||
            (pkg == "com.android.systemui" && e.className?.contains("Recents", true) == true)
        if (isHome) {
            Monitor.target = null
            if (Prefs.isLockMode(this) && Prefs.isSetupComplete(this)) openLock()
            return
        }
        if (pkg in Monitor.TRANSIENT || pkg in imes) return

        Monitor.lastApp = pkg
        Monitor.addRecent(pkg)

        if (!Prefs.isStrict(this) || Prefs.isTrusted(this, pkg)) {
            Monitor.target = null
            return
        }
        if (Prefs.isBlocked(this, pkg)) {
            Monitor.target = null
            blockScreen(pkg, "This app is blocked. Kindly enter your PIN to unblock it.")
            return
        }
        Monitor.target = pkg
    }

    private fun openLock(recents: Boolean = false, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastLockAt < 400) return
        lastLockAt = now
        startActivity(
            Intent(this, LockActivity::class.java)
                .putExtra("recents", recents)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
        )
    }

    /** Lock Mode ON + screen on + phone unlocked: Space Mind er 3 button bar dekhao. */
    private fun syncNavBar() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val want = Prefs.isLockMode(this) && Prefs.isSetupComplete(this) &&
            pm.isInteractive && !km.isKeyguardLocked
        if (want) navBar.show() else navBar.hide()
    }

    private fun scheduleGuard(vararg delaysMs: Long) {
        if (!Prefs.isSetupDone(this) || Session.isUnlocked()) return
        for (d in delaysMs) handler.postDelayed({ guardCheck() }, d)
    }

    /** Settings e "Space Mind" nam dekhale PIN chai. PIN na dile Home e jay. */
    private fun guardCheck() {
        if (Session.isUnlocked()) return
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg !in Monitor.NEUTRAL) return
        val hits = root.findAccessibilityNodeInfosByText(getString(R.string.app_name))
        if (hits.isNullOrEmpty()) return

        val now = System.currentTimeMillis()
        if (now - lastGuardAt < 1500) return
        lastGuardAt = now
        startActivity(
            Intent(this, PinActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("guard", true)
                .putExtra("reason", "Enter PIN to access Space Mind settings")
        )
    }

    // ---------------- Screen check ----------------

    @RequiresApi(Build.VERSION_CODES.R)
    private fun scanOnce() {
        val pkg = Monitor.target ?: return
        val det = detector ?: return
        if (busy) return

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!pm.isInteractive || km.isKeyguardLocked) return

        busy = true
        takeScreenshot(Display.DEFAULT_DISPLAY, worker, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                try {
                    val hb = result.hardwareBuffer
                    val hw = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                    val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    hw?.recycle()
                    hb.close()
                    if (soft != null) {
                        val res = det.score(soft)
                        soft.recycle()
                        handler.post { handleResult(pkg, res) }
                    }
                } catch (t: Throwable) {
                    Monitor.modelStatus = "scan error: ${t.javaClass.simpleName}"
                } finally {
                    busy = false
                }
            }

            override fun onFailure(errorCode: Int) {
                busy = false
            }
        })
    }

    private fun handleResult(pkg: String, r: NsfwDetector.Result) {
        Monitor.lastScore = "unsafe=%.2f  sexy=%.2f  (%s)".format(r.unsafe, r.sexy, r.top)

        if (Monitor.target != pkg) {
            strikes = 0
            return
        }
        if (strikePkg != pkg) {
            strikePkg = pkg
            strikes = 0
        }
        when {
            r.unsafe >= NsfwConfig.HARD -> flag(pkg)
            r.unsafe >= NsfwConfig.SOFT || r.sexy >= NsfwConfig.SEXY -> {
                strikes++
                if (strikes >= 2) flag(pkg)
            }
            else -> strikes = 0
        }
    }

    private fun flag(pkg: String) {
        strikes = 0
        Prefs.setBlocked(this, pkg, true)
        Monitor.target = null
        blockScreen(pkg, "Sexual content was detected. This app has been blocked.")
    }

    // ---------------- Block screen ----------------

    /** Age overlay diye content lukao, tarpor Home e pathao. */
    private fun blockScreen(pkg: String, message: String) {
        overlay.show(pkg, message)
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun refreshSets() {
        lastRefresh = System.currentTimeMillis()
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        launchers = packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }.toSet()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imes = imm.enabledInputMethodList.map { it.packageName }.toSet()
    }
}
