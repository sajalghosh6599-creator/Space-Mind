package com.strictmode.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** Device Admin thakle app uninstall kora jay na, age admin off korte hoy (ta PIN guard e atkano). */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Space Mind should not be turned off without the PIN."
}
