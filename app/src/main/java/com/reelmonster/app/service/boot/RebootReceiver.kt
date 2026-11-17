package com.reelmonster.app.service.boot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.reelmonster.app.MainActivity
import com.reelmonster.app.R
import com.reelmonster.app.ReelMonsterApp
import com.reelmonster.app.service.overlay.FloatingMonsterOverlayService

/**
 * RebootReceiver
 *
 * Listens for ACTION_BOOT_COMPLETED and ACTION_MY_PACKAGE_REPLACED broadcasts.
 *
 * Design & Android Security Compliance:
 * 1. Checks if the user previously enabled the overlay.
 * 2. Checks if overlay permission (canDrawOverlays) is currently granted.
 * 3. Respects Android 12+ (API 31+) background execution limits:
 *    - On Android 12+, starting foreground services from a background receiver is restricted
 *      (ForegroundServiceStartNotAllowedException). A tap-to-resume notification is posted.
 *    - On Android 11 and lower, starts the foreground service directly if permitted.
 * 4. All SQLite session counts and daily summaries reside on non-volatile flash storage
 *    and remain completely intact across reboots without requiring any manual migration.
 */
class RebootReceiver : BroadcastReceiver() {

    companion object {
        const val BOOT_CHANNEL_ID = "reel_monster_boot_channel"
        const val BOOT_NOTIFICATION_ID = 2001
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val app = context.applicationContext as? ReelMonsterApp ?: return
        val preferences = app.preferencesManager
        val isOverlayEnabled = preferences.isOverlayEnabled.value

        if (!isOverlayEnabled) {
            // User had disabled the overlay; respect user choice and do not start
            return
        }

        // Verify overlay permission
        val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

        if (!hasOverlayPermission) {
            // Cannot show overlay without explicit system permission
            return
        }

        // Android 12+ (API 31+) background-start restriction compliance
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Post clear notification for user to tap and resume overlay safely
            postResumeTrackingNotification(context)
        } else {
            // On API < 31, direct foreground service startup is permitted
            try {
                val serviceIntent = Intent(context, FloatingMonsterOverlayService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (_: Exception) {
                postResumeTrackingNotification(context)
            }
        }
    }

    private fun postResumeTrackingNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BOOT_CHANNEL_ID,
                "Reel Monster Restart Recovery",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when device restarts so Munchie overlay can resume"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(context, BOOT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_munchie_monster)
            .setContentTitle("Munchie is ready! 👾")
            .setContentText("Tap to resume your floating screen companion")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(BOOT_NOTIFICATION_ID, notification)
    }
}
