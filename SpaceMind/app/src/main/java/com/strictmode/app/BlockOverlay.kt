package com.strictmode.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Pura screen dhaka overlay: sathe sathe content lukay (Android 12+ e blur, onno phone e gora kalo).
 * Overlay e "OK" ar "Unblock with PIN" button thake. 8 second por nijei soriye jay.
 */
class BlockOverlay(private val svc: AccessibilityService) {

    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private val autoHide = Runnable { dismiss() }

    fun show(pkg: String, message: String) {
        dismiss()
        val pm = svc.packageManager
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
        val pad = svc.dp(24)

        val root = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xF0101010.toInt())
            isClickable = true
        }
        fun text(t: String, size: Float, bold: Boolean = false) = TextView(svc).apply {
            text = t
            textSize = size
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, pad / 4, 0, pad / 4)
        }
        root.addView(text("Blocked", 26f, true))
        root.addView(text(label, 18f, true))
        root.addView(text(message, 15f))
        root.addView(Button(svc).apply {
            text = "OK"
            setOnClickListener { dismiss() }
        })
        root.addView(Button(svc).apply {
            text = "Unblock with PIN"
            setOnClickListener {
                dismiss()
                svc.startActivity(
                    Intent(svc, PinActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra("unblock_pkg", pkg)
                        .putExtra("reason", "Enter PIN to unblock $label")
                )
            }
        })

        @Suppress("DEPRECATION")
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) lp.blurBehindRadius = 80

        try {
            wm.addView(root, lp)
            view = root
            handler.postDelayed(autoHide, 8000)
        } catch (e: Exception) {
            // overlay dekhano na gele-o app Home e jabe
        }
    }

    fun dismiss() {
        handler.removeCallbacks(autoHide)
        view?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
            }
        }
        view = null
    }
}
