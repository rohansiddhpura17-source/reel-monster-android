package com.reelmonster.app.service.tracking

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.io.File

/**
 * BootSessionProvider
 *
 * Provides a reliable, persistent identifier for the current Android device boot session.
 *
 * Supported Android resolution order:
 * 1. Linux kernel `/proc/sys/kernel/random/boot_id` (standard boot UUID generated on every system boot; API 26+).
 * 2. Android `Settings.Global.BOOT_COUNT` (system server boot counter; API 24+).
 * 3. Fallback: Boot epoch timestamp derived from `(currentTimeMillis - elapsedRealtime) / 1000`.
 *
 * Includes test hook `setTestBootSessionId` for deterministic unit testing across mock boot cycles.
 */
object BootSessionProvider {

    @Volatile
    private var testBootSessionId: String? = null

    @Volatile
    private var cachedBootSessionId: String? = null

    /**
     * Overrides the boot session ID for testing. Pass null to restore live resolution.
     */
    fun setTestBootSessionId(id: String?) {
        testBootSessionId = id
        if (id != null) {
            cachedBootSessionId = id
        } else {
            clearCache()
        }
    }

    /**
     * Resolves the current boot session ID.
     */
    fun getBootSessionId(context: Context? = null): String {
        testBootSessionId?.let { return it }
        cachedBootSessionId?.let { return it }

        val resolved = resolveBootSessionId(context)
        cachedBootSessionId = resolved
        return resolved
    }

    /**
     * Clears cached session ID (e.g. after mock reboot in tests).
     */
    fun clearCache() {
        cachedBootSessionId = null
    }

    private fun resolveBootSessionId(context: Context?): String {
        // Method 1: Kernel boot_id UUID
        try {
            val bootIdFile = File("/proc/sys/kernel/random/boot_id")
            if (bootIdFile.exists() && bootIdFile.canRead()) {
                val text = bootIdFile.readText().trim()
                if (text.isNotEmpty()) {
                    return text
                }
            }
        } catch (_: Throwable) {}

        // Method 2: Settings.Global.BOOT_COUNT
        if (context != null) {
            try {
                val count = Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.BOOT_COUNT,
                    -1
                )
                if (count > 0) {
                    return "boot_count_$count"
                }
            } catch (_: Throwable) {}
        }

        // Method 3: Approximate boot epoch in seconds
        val bootEpochSec = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 1000L
        return "boot_epoch_$bootEpochSec"
    }
}
