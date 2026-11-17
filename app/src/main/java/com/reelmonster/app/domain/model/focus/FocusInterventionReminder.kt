package com.reelmonster.app.domain.model.focus

import com.reelmonster.app.domain.model.TargetApp

/**
 * FocusInterventionReminder
 *
 * Gentle, optional reminder when a user opens a monitored video feed
 * while a Focus Mode session is currently running.
 * Never performs forced app blocking or process termination.
 */
data class FocusInterventionReminder(
    val title: String,
    val message: String,
    val remainingFormatted: String,
    val targetApp: TargetApp,
    val timestamp: Long,
    val targetDurationMs: Long = 0L
) {
    val appName: String get() = targetApp.displayName
}
