package com.reelmonster.app.domain.model.weekly

import com.reelmonster.app.domain.model.ViewingSession

/**
 * Phase 15 — Actual Viewing-Time Reconciliation (proposal §5).
 *
 * Uses actual persisted `viewing_sessions` durations only.
 * Distinguishes:
 * - Actual measured viewing duration.
 * - Untimed manual Reel logs (a count only — no duration is invented
 *   for them, ever).
 * - Missing or unavailable duration records.
 *
 * Average dwell per Reel states its denominator and includes only
 * valid measured sessions. Deduplication by session id prevents
 * duplicate session counting.
 */
data class ActualViewingTimeReport(
    /** Sum of distinct measured session durations, in ms. */
    val actualMeasuredMs: Long,
    /** Number of distinct measured sessions. */
    val measuredSessionCount: Int,
    /** Manual Reel logs with no measured session, or null when unknown. */
    val untimedManualReelCount: Int?,
    /** Average measured duration per session, or null when there are none. */
    val averageDwellMs: Long?,
    /** True when there are untimed manual logs with no duration. */
    val hasMissingDurations: Boolean
)

object ActualViewingTimeReconciler {

    fun reconcile(
        sessions: List<ViewingSession>,
        untimedManualReelCount: Int? = null
    ): ActualViewingTimeReport {
        val distinct = sessions.distinctBy { it.id }
        val measuredMs = distinct.sumOf { it.durationMs.coerceAtLeast(0L) }
        val count = distinct.size
        val untimed = untimedManualReelCount?.coerceAtLeast(0)
        return ActualViewingTimeReport(
            actualMeasuredMs = measuredMs,
            measuredSessionCount = count,
            untimedManualReelCount = untimed,
            averageDwellMs = if (count > 0) measuredMs / count else null,
            hasMissingDurations = (untimed ?: 0) > 0
        )
    }

    /** Total measured duration for a day's sessions, deduplicated by id. */
    fun totalMeasuredMs(sessions: List<ViewingSession>): Long {
        return sessions.distinctBy { it.id }.sumOf { it.durationMs.coerceAtLeast(0L) }
    }
}
