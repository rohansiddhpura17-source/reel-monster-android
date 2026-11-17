package com.reelmonster.app.domain.model.focus

import java.util.Locale

data class FocusSessionProgress(
    val status: FocusStatus,
    val type: FocusType,
    val targetDurationMs: Long,
    val elapsedMs: Long,
    val startWallTime: Long = 0L
) {
    val remainingMs: Long get() = (targetDurationMs - elapsedMs).coerceAtLeast(0L)
    val progressPercentage: Int get() = if (targetDurationMs > 0) {
        ((elapsedMs.toFloat() / targetDurationMs.toFloat()) * 100f).toInt().coerceIn(0, 100)
    } else 0
    val progressFraction: Float get() = if (targetDurationMs > 0) {
        (elapsedMs.toFloat() / targetDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val isFinished: Boolean get() = elapsedMs >= targetDurationMs && targetDurationMs > 0

    val formattedRemaining: String get() {
        val remSec = (remainingMs + 999L) / 1000L
        val mins = remSec / 60
        val secs = remSec % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    val formattedElapsed: String get() {
        val elSec = elapsedMs / 1000L
        val mins = elSec / 60
        val secs = elSec % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    companion object {
        fun idle(): FocusSessionProgress = FocusSessionProgress(
            status = FocusStatus.IDLE,
            type = FocusType.DEEP_FOCUS,
            targetDurationMs = 25 * 60_000L,
            elapsedMs = 0L
        )
    }
}
