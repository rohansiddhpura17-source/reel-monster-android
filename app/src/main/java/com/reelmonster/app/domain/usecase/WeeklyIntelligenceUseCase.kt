package com.reelmonster.app.domain.usecase

import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.domain.model.weekly.HourlyBucket
import com.reelmonster.app.domain.model.weekly.HourlyScrollingPatternCalculator
import com.reelmonster.app.domain.model.weekly.WeeklyDayInput
import com.reelmonster.app.domain.model.weekly.WeeklyDigest
import com.reelmonster.app.domain.model.weekly.WeeklyDigestCalculator
import com.reelmonster.app.domain.model.weekly.WeeklyTrendCalculator
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Phase 15 — Weekly Intelligence use case (proposal §§2–6, §11).
 *
 * Mirrors the existing [HistoryAnalyticsUseCase] convention: a
 * synchronous use case over [ReelDbHelper]. Callers must run it on
 * `Dispatchers.IO` (proposal §6) and expose results through the
 * existing reactive architecture.
 *
 * Data sources (all local, schema v6 — no new tables):
 * - Reel counts: `daily` summaries via
 *   [ReelDbHelper.getDailySummariesInRange].
 * - Actual viewing duration: persisted `viewing_sessions` via
 *   [ReelDbHelper.getViewingTimeForDate] (null = unavailable, never
 *   zero-filled or estimated).
 * - Verified coverage: `daily_monitoring_coverage` via
 *   [ReelDbHelper.getDailyMonitoringCoverage].
 * - Focus duration: `focus_sessions` via
 *   [ReelDbHelper.getFocusSessionsForDate] (kept separate from viewing).
 * - Hourly pattern: per-date events, viewing sessions, and monitoring
 *   intervals for the current seven complete days.
 */
class WeeklyIntelligenceUseCase(private val dbHelper: ReelDbHelper) {

    fun buildDayInputs(dates: List<LocalDate>): Map<LocalDate, WeeklyDayInput> {
        if (dates.isEmpty()) return emptyMap()
        val startKey = dates.min().toString()
        val endKey = dates.max().toString()
        val summaries = dbHelper.getDailySummariesInRange(startKey, endKey)
        return dates.associateWith { date ->
            val key = date.toString()
            val summary = summaries[key]
            WeeklyDayInput(
                date = date,
                reelCount = summary?.totalReels ?: 0,
                instagramCount = summary?.instagramCount ?: 0,
                youtubeCount = summary?.youtubeCount ?: 0,
                actualViewingMs = dbHelper.getViewingTimeForDate(key),
                verifiedCoverageMs = dbHelper.getDailyMonitoringCoverage(key)
                    .estimatedVerifiedMonitoringMs.coerceAtLeast(0L),
                focusMs = dbHelper.getFocusSessionsForDate(key)
                    .sumOf { it.actualDurationMs.coerceAtLeast(0L) }
            )
        }
    }

    /** Weekly digest for today [today] (current `[D-7, D-1]` vs previous `[D-14, D-8]`). */
    fun buildWeeklyDigest(today: LocalDate): WeeklyDigest {
        val (currentDates, previousDates) = WeeklyTrendCalculator.windowsFor(today)
        val inputs = buildDayInputs(currentDates + previousDates)
        return WeeklyDigestCalculator.buildDigest(today, inputs)
    }

    /**
     * Hourly pattern over the current seven complete days
     * `[D-7, D-1]`, in [zoneId]. Window is
     * `[start of D-7, start of D)` in that zone.
     */
    fun buildHourlyPattern(
        today: LocalDate,
        zoneId: ZoneId
    ): List<HourlyBucket> {
        val windowStartMs = ZonedDateTime.of(
            today.minusDays(7), LocalTime.MIDNIGHT, zoneId
        ).toInstant().toEpochMilli()
        val windowEndMs = ZonedDateTime.of(
            today, LocalTime.MIDNIGHT, zoneId
        ).toInstant().toEpochMilli()
        val dates = (1..7).map { today.minusDays(it.toLong()) }
        val events = dates.flatMap { dbHelper.getEventsForDate(it.toString()) }
        val sessions = dates.flatMap { dbHelper.getViewingSessionsForDate(it.toString()) }
        val intervals = dates.flatMap { dbHelper.getMonitoringIntervalsForDate(it.toString()) }
        return HourlyScrollingPatternCalculator.computeHourlyPattern(
            events = events,
            sessions = sessions,
            intervals = intervals,
            windowStartMs = windowStartMs,
            windowEndMs = windowEndMs,
            zoneId = zoneId
        )
    }
}
