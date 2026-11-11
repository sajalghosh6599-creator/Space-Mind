package com.strictmode.app

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/** Service ar baki app er majhe shared state. */
object Monitor {
    /** Je app ke ekhon AI diye check korte hobe. null = kichu check korar dorkar nei. */
    @Volatile var target: String? = null

    /** Shesh je app detect hoyeche (test er jonno Main screen e dekhano hoy). */
    @Volatile var lastApp: String? = null

    /** AI model er obostha (Main screen e dekhano hoy). */
    @Volatile var modelStatus: String = "not loaded (turn Guard ON)"

    /** Model er input/output info (debug). */
    @Volatile var modelInfo: String = "-"

    /** Input preprocess: "bgr_mean" (Yahoo/OpenNSFW style) ba "rgb01" (pixel/255). */
    @Volatile var preprocess: String = "bgr_mean"

    private val recentList = ArrayList<String>()

    @Synchronized fun addRecent(pkg: String) {
        recentList.remove(pkg)
        recentList.add(0, pkg)
        while (recentList.size > 10) recentList.removeAt(recentList.size - 1)
    }

    @Synchronized fun recents(): List<String> = recentList.toList()

    @Synchronized fun clearRecents() = recentList.clear()

    /** Shesh AI score. */
    @Volatile var lastScore: String = "-"

    /** Ei app e AI check hobe na, kintu ager state bodlabe na (keyboard, notification shade). */
    val TRANSIENT = setOf("com.android.systemui", "android")

    /** Settings type screen: AI check dorkar nei (Dhap 4 e ekhane PIN guard boshbe). */
    val NEUTRAL = setOf(
        "com.android.settings",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.miui.securitycenter"
    )

    fun isGuardEnabled(ctx: Context): Boolean {
        val cn = ComponentName(ctx, GuardService::class.java)
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any {
            it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true)
        }
    }
}
