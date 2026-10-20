package com.cortana.hologram

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Restarts the hologram after reboot or after the app is updated,
 * as long as the overlay permission is still granted.
 *
 * Note: on Android 12+ some devices restrict foreground-service starts
 * from boot receivers; failures are swallowed here and the user can
 * simply tap the app icon instead. See README.md.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        if (!Settings.canDrawOverlays(context)) return

        try {
            val serviceIntent = Intent(context, HolographicOverlayService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            // Foreground-service start restricted on this device/OS build.
            // The hologram can still be launched from the app icon.
        }
    }
}
