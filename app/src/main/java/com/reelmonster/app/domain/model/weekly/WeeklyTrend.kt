package com.reelmonster.app.domain.model.weekly

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Phase 15 — Seven-Day Intelligence (proposal §§3–4).
 *
 * Windows, with `D` = today in the device timezone:
 * - Current window: `[D-7, D-1]` inclusive.
 * - Previous window: `[D-14, D-8]` inclusive.
 * - Today `D` is displayed separately and excluded from both windows.
 *
 * A day is adequately monitored when it has at least 120 minutes of
 * eligible verified coverage (Phase 14 threshold). That is a minimum
 * eligibility threshold, not proof of perfect detection.
 *
 * Confidence is applied independently to each window:
 * - 0–3 adequately monitored days: Insufficient — suppress percentage
 *   comparisons; show observed totals with an incomplete-data notice.
 * - 4–5: Limited — show totals and coverage-normalized rates, label
 *   Limited data, avoid strong directional language.
 * - 6–7: High — show totals, normalized rates, and percentage deltas,
 *   with coverage visible.
 *
 * When windows differ in confidence, the lower confidence governs the
 * combined comparison. Missing days are never filled with zero.
 * Language stays neutral ("observed viewing rate increased/decreased").
 * Increases are never framed as personal failure, decreases never as
 * clinical improvement.
 */
enum class WeeklyConfidence(val displayLabel: String) {
    INSUFFICIENT("Insufficient data"),
    LIMITED("Limited data"),
    HIGH("High confidence")
}

/** One calendar day of already-aggregated Phase 15 inputs. */
data class WeeklyDayInput(
    val date: LocalDate,
    val reelCount: Int,
    val instagramCount: Int,
    val youtubeCount: Int,
    /** Actual measured viewing duration, or null when unavailable (never zero-filled). */
    val actualViewingMs: Long?,
    /** Eligible verified monitoring coverage for the day, in ms. */
    val verifiedCoverageMs: Long,
    /** Actual focus-session duration for the day, in ms. */
    val focusMs: Long
) {
    init {
        require(reelCount == instagramCount + youtubeCount) {
            "Total reels ($reelCount) must equal Instagram ($instagramCount) + YouTube ($youtubeCount)"
        }
    }
}

data class WeeklyWindow(
    val dates: List<LocalDate>,
    val days: List<WeeklyDayInput>,
    val reelCount: Int,
    val instagramCount: Int,
    val youtubeCount: Int,
    /** Sum of actual measured viewing durations over days that have one. */
    val actualViewingMs: Long,
    /** Days with no measured viewing duration (shown as unavailable, not zero). */
    val missingViewingDays: Int,
    val focusMs: Long,
    val verifiedCoverageMs: Long,
    val verifiedHours: Double,
    val adequateDays: Int,
    val confidence: WeeklyConfidence
) {
    /** Coverage-normalized Reel rate, or null when there is no verified coverage. */
    val reelsPerVerifiedHour: Double?
        get() = if (verifiedHours > 0.0) reelCount / verifiedHours else null

    /** Coverage-normalized viewing rate (ms per verified hour), or null when no coverage. */
    val viewingMsPerVerifiedHour: Double?
        get() = if (verifiedHours > 0.0) actualViewingMs / verifiedHours else null
}

data class WeeklyComparison(
    val current: WeeklyWindow,
    val previous: WeeklyWindow,
    /** Lower of the two windows' confidences; governs the comparison. */
    val comparisonConfidence: WeeklyConfidence,
    /** True when either window is Insufficient: percentage deltas are suppressed. */
    val percentagesSuppressed: Boolean,
    /** Reel-rate delta in percent, or null when suppressed / zero baseline. */
    val reelRateDeltaPercent: Double?,
    /** Viewing-rate delta in percent, or null when suppressed / zero baseline. */
    val viewRateDeltaPercent: Double?,
    /** Absolute reel-rate difference (reels per verified hour), for zero-baseline cases. */
    val reelRateDifference: Double?,
    /** Absolute viewing-rate difference (ms per verified hour), for zero-baseline cases. */
    val viewRateDifference: Double?,
    val summary: String
)

data class WeekdayWeekendGroup(
    val dayCount: Int,
    val adequateDays: Int,
    val verifiedCoverageMs: Long,
    val reelCount: Int,
    val actualViewingMs: Long,
    val reelsPerMonitoredHour: Double?,
    val viewingMinutesPerMonitoredHour: Double?
)

data class WeekdayWeekendComparison(
    val weekday: WeekdayWeekendGroup,
    val weekend: WeekdayWeekendGroup
)

object WeeklyTrendCalculator {

    /** Adequate monitoring for weekly comparison: >= 120 minutes of eligible verified coverage. */
    const val ADEQUATE_DAY_THRESHOLD_MS = 7_200_000L

    /** Current `[D-7, D-1]` and previous `[D-14, D-8]` date lists for today `D`. */
    fun windowsFor(today: LocalDate): Pair<List<LocalDate>, List<LocalDate>> {
        val current = (1..7).map { today.minusDays(it.toLong()) }.sorted()
        val previous = (8..14).map { today.minusDays(it.toLong()) }.sorted()
        return current to previous
    }

    fun buildWindow(
        dates: List<LocalDate>,
        inputsByDate: Map<LocalDate, WeeklyDayInput>
    ): WeeklyWindow {
        val days = dates.map { date ->
            inputsByDate[date] ?: WeeklyDayInput(
                date = date,
                reelCount = 0,
                instagramCount = 0,
                youtubeCount = 0,
                actualViewingMs = null,
                verifiedCoverageMs = 0L,
                focusMs = 0L
            )
        }
        // A date with no input row is a missing day, not a zero-use day:
        // it contributes no reels, no viewing time, and no coverage, and
        // it is not an adequately monitored day.
        val adequateDays = days.count { it.verifiedCoverageMs >= ADEQUATE_DAY_THRESHOLD_MS }
        val verifiedMs = days.sumOf { it.verifiedCoverageMs }
        val confidence = when (adequateDays) {
            in 0..3 -> WeeklyConfidence.INSUFFICIENT
            in 4..5 -> WeeklyConfidence.LIMITED
            else -> WeeklyConfidence.HIGH
        }
        return WeeklyWindow(
            dates = dates,
            days = days,
            reelCount = days.sumOf { it.reelCount },
            instagramCount = days.sumOf { it.instagramCount },
            youtubeCount = days.sumOf { it.youtubeCount },
            actualViewingMs = days.sumOf { it.actualViewingMs ?: 0L },
            missingViewingDays = days.count { it.actualViewingMs == null },
            focusMs = days.sumOf { it.focusMs },
            verifiedCoverageMs = verifiedMs,
            verifiedHours = verifiedMs / 3_600_000.0,
            adequateDays = adequateDays,
            confidence = confidence
        )
    }

    fun compare(current: WeeklyWindow, previous: WeeklyWindow): WeeklyComparison {
        val comparisonConfidence = if (
            current.confidence.ordinal < previous.confidence.ordinal
        ) {
            current.confidence
        } else {
            previous.confidence
        }
        val suppressed = comparisonConfidence == WeeklyConfidence.INSUFFICIENT

        val currentReelRate = current.reelsPerVerifiedHour ?: 0.0
        val previousReelRate = previous.reelsPerVerifiedHour ?: 0.0
        val currentViewRate = current.viewingMsPerVerifiedHour ?: 0.0
        val previousViewRate = previous.viewingMsPerVerifiedHour ?: 0.0

        val reelDelta = deltaPercent(currentReelRate, previousReelRate, suppressed)
        val viewDelta = deltaPercent(currentViewRate, previousViewRate, suppressed)

        val summary = when {
            suppressed -> "There is not enough monitoring data to compare these periods reliably."
            previousReelRate == 0.0 && currentReelRate == 0.0 &&
                previousViewRate == 0.0 && currentViewRate == 0.0 ->
                "No observed activity in either period."
            previousReelRate == 0.0 && currentReelRate > 0.0 ->
                "New observed activity in the current period."
            else -> buildString {
                append("Observed viewing rate ")
                when {
                    viewDelta != null && viewDelta > 0.0 -> append("increased")
                    viewDelta != null && viewDelta < 0.0 -> append("decreased")
                    else -> append("was unchanged")
                }
                append(" compared with the previous period.")
            }
        }

        return WeeklyComparison(
            current = current,
            previous = previous,
            comparisonConfidence = comparisonConfidence,
            percentagesSuppressed = suppressed,
            reelRateDeltaPercent = reelDelta,
            viewRateDeltaPercent = viewDelta,
            reelRateDifference = if (suppressed) null else currentReelRate - previousReelRate,
            viewRateDifference = if (suppressed) null else currentViewRate - previousViewRate,
            summary = summary
        )
    }

    /**
     * Percentage delta with the proposal's zero-baseline rules:
     * - Positive previous rate: standard delta.
     * - Both rates zero: no percentage (null).
     * - Previous zero, current positive: no percentage/infinity (null);
     *   callers use the absolute rate difference instead.
     * - Previous positive, current zero: -100% (only when not suppressed;
     *   suppression is handled by the caller passing `suppressed = true`).
     * - Suppressed: always null.
     */
    fun deltaPercent(currentRate: Double, previousRate: Double, suppressed: Boolean): Double? {
        if (suppressed) return null
        if (previousRate == 0.0) return null
        return ((currentRate - previousRate) / previousRate) * 100.0
    }

    /** Monday–Friday vs Saturday–Sunday for the given window's days. */
    fun weekdayWeekend(days: List<WeeklyDayInput>): WeekdayWeekendComparison {
        return WeekdayWeekendComparison(
            weekday = group(days.filter { it.date.dayOfWeek !in WEEKEND }),
            weekend = group(days.filter { it.date.dayOfWeek in WEEKEND })
        )
    }

    private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    private fun group(days: List<WeeklyDayInput>): WeekdayWeekendGroup {
        val verifiedMs = days.sumOf { it.verifiedCoverageMs }
        val verifiedHours = verifiedMs / 3_600_000.0
        val reels = days.sumOf { it.reelCount }
        val viewingMs = days.sumOf { it.actualViewingMs ?: 0L }
        return WeekdayWeekendGroup(
            dayCount = days.size,
            adequateDays = days.count { it.verifiedCoverageMs >= ADEQUATE_DAY_THRESHOLD_MS },
            verifiedCoverageMs = verifiedMs,
            reelCount = reels,
            actualViewingMs = viewingMs,
            reelsPerMonitoredHour = if (verifiedHours > 0.0) reels / verifiedHours else null,
            viewingMinutesPerMonitoredHour = if (verifiedHours > 0.0) {
                (viewingMs / 60_000.0) / verifiedHours
            } else {
                null
            }
        )
    }
}
