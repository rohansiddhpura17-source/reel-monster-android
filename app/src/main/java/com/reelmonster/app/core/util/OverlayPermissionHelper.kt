package com.reelmonster.app.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * OverlayPermissionHelper
 *
 * Safe utility to check and request the Android SYSTEM_ALERT_WINDOW permission
 * required to display the floating Munchie companion above Instagram and YouTube.
 */
object OverlayPermissionHelper {

    /**
     * Checks if the application currently has permission to draw overlays over other apps.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Builds the explicit intent to take the user directly to the application's
     * "Display over other apps" settings toggle.
     */
    fun createRequestOverlayPermissionIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent()
        }
    }

    /**
     * Launches the system settings screen for overlay permission.
     */
    fun requestOverlayPermission(context: Context) {
        val intent = createRequestOverlayPermissionIntent(context)
        if (intent.action != null) {
            context.startActivity(intent)
        }
    }
}
