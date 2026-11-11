package com.strictmode.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Jei permission gulo app program diye check korte pare. */
object Perms {
    fun accessibility(c: Context) = Monitor.isGuardEnabled(c)

    fun admin(c: Context): Boolean {
        val dpm = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ComponentName(c, AdminReceiver::class.java))
    }

    fun battery(c: Context): Boolean {
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(c.packageName)
    }

    fun overlay(c: Context) = Settings.canDrawOverlays(c)

    fun allRequired(c: Context) = accessibility(c) && admin(c) && battery(c) && overlay(c)

    fun isXiaomi(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")
    }
}
