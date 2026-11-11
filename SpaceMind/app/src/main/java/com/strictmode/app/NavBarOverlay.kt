package com.strictmode.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Space Mind er nijossho 3 button navigation bar (Recents, Home, Back). Lock Mode ON thakle shob app er niche dekha jay. */
class NavBarOverlay(
    private val svc: AccessibilityService,
    private val onRecents: () -> Unit,
    private val onHome: () -> Unit,
    private val onBack: () -> Unit
) {
    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null

    private fun barHeight(): Int {
        val id = svc.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val h = if (id > 0) svc.resources.getDimensionPixelSize(id) else 0
        return if (h > 0) h else svc.dp(48)
    }

    fun show() {
        if (view != null) return
        val bar = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFFE9EDF2.toInt())
        }
        fun slot(kind: Int, action: () -> Unit) {
            val cell = FrameLayout(svc).apply {
                isClickable = true
                setOnClickListener { action() }
                addView(
                    NavIcon(svc, kind),
                    FrameLayout.LayoutParams(svc.dp(26), svc.dp(26), Gravity.CENTER)
                )
            }
            bar.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        slot(0, onRecents)
        slot(1, onHome)
        slot(2, onBack)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            barHeight(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM
        try {
            wm.addView(bar, lp)
            view = bar
        } catch (e: Exception) {
            // bar na dekhale-o system button gulo Lock screen e pathabe
        }
    }

    fun hide() {
        view?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
            }
        }
        view = null
    }
}

/** kind: 0 = Recents (square), 1 = Home (ring), 2 = Back (triangle) */
class NavIcon(ctx: Context, private val kind: Int) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF757575.toInt() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        when (kind) {
            0 -> {
                paint.style = Paint.Style.FILL
                c.drawRoundRect(RectF(w * 0.1f, h * 0.1f, w * 0.9f, h * 0.9f), w * 0.15f, w * 0.15f, paint)
            }
            1 -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = w * 0.12f
                c.drawCircle(w / 2, h / 2, w * 0.42f, paint)
                paint.style = Paint.Style.FILL
                c.drawCircle(w / 2, h / 2, w * 0.2f, paint)
            }
            else -> {
                paint.style = Paint.Style.FILL
                val p = Path().apply {
                    moveTo(w * 0.1f, h * 0.15f)
                    lineTo(w * 0.9f, h * 0.15f)
                    lineTo(w * 0.5f, h * 0.9f)
                    close()
                }
                c.drawPath(p, paint)
            }
        }
    }
}
