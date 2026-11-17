package com.reelmonster.app.domain.model.focus

data class FocusPeriodStats(
    val totalFocusTimeMs: Long,
    val completedSessionsCount: Int,
    val totalSessionsCount: Int,
    val averageDurationMs: Long
) {
    val completionRatePercentage: Int get() = if (totalSessionsCount > 0) {
        ((completedSessionsCount.toFloat() / totalSessionsCount.toFloat()) * 100f).toInt().coerceIn(0, 100)
    } else 0

    val formattedTotalTime: String get() {
        val totalMins = totalFocusTimeMs / 60_000L
        val hours = totalMins / 60
        val mins = totalMins % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    companion object {
        fun empty(): FocusPeriodStats = FocusPeriodStats(
            totalFocusTimeMs = 0L,
            completedSessionsCount = 0,
            totalSessionsCount = 0,
            averageDurationMs = 0L
        )
    }
}
