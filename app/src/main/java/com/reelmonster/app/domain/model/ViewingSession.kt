package com.reelmonster.app.domain.model

/**
 * ViewingSession
 *
 * Persisted record of actual active on-screen Reel or Short viewing duration.
 * Uses monotonic clock (SystemClock.elapsedRealtime) for accurate elapsed duration measurement.
 *
 * @property id Auto-incrementing unique session ID in SQLite
 * @property targetApp Target platform (Instagram or YouTube)
 * @property fingerprint Unique structural content fingerprint if available
 * @property startTimestamp Wall-clock timestamp (ms) when session started
 * @property endTimestamp Wall-clock timestamp (ms) when session closed
 * @property durationMs Actual elapsed viewing duration in milliseconds
 * @property completionStatus Status code ("COMPLETED", "INTERRUPTED_APP_SWITCH", "INTERRUPTED_SCREEN_OFF", "INTERRUPTED_PAUSED", "INTERRUPTED_GLOBAL_OFF", "INTERRUPTED_RESET")
 * @property sessionDate Date key in YYYY-MM-DD local calendar format
 */
data class ViewingSession(
    val id: Long = 0,
    val targetApp: TargetApp,
    val fingerprint: String? = null,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val durationMs: Long,
    val completionStatus: String = "COMPLETED",
    val sessionDate: String
)

/**
 * TodayViewingStats
 *
 * Aggregated actual viewing statistics for the current calendar day.
 */
data class TodayViewingStats(
    val totalViewingTimeMs: Long = 0L,
    val instagramViewingTimeMs: Long = 0L,
    val youtubeViewingTimeMs: Long = 0L,
    val sessionCount: Int = 0,
    val averageDurationPerReelMs: Long = 0L
)

/**
 * Result of attempting a daily tracking reset.
 */
data class DailyResetResult(
    val success: Boolean,
    val message: String,
    val remainingResets: Int
)
