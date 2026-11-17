package com.reelmonster.app.domain.model.weekly

import java.time.LocalDate

/**
 * Phase 15 — Weekly Executive Digest (proposal §6).
 *
 * Calculated locally from the current seven-complete-day window:
 * observed Reel count, actual observed viewing duration, focus duration
 * (separately from viewing duration), adequately monitored days out of
 * seven, weekday/weekend comparison when eligible, current vs previous
 * period trend when eligible, and one or two factual reflections.
 *
 * Computed dynamically; there is intentionally no `weekly_digests`
 * table (proposal §12). The digest must be recomputed after resets or
 * data changes — never served from a stale cache.
 */
data class WeeklyDigest(
    val currentWindow: WeeklyWindow,
    val previousWindow: WeeklyWindow,
    val comparison: WeeklyComparison,
    val weekdayWeekend: WeekdayWeekendComparison,
    /** Focus duration in the current window, kept separate from viewing duration. */
    val focusMs: Long,
    /** One or two factual reflections (proposal §6). */
    val reflections: List<String> = emptyList()
)

object WeeklyDigestCalculator {

    /**
     * Builds the digest for today [today] from per-day inputs.
     * [inputsByDate] may omit dates; missing dates are missing data,
     * never zero-filled as observed use.
     */
    fun buildDigest(
        today: LocalDate,
        inputsByDate: Map<LocalDate, WeeklyDayInput>
    ): WeeklyDigest {
        val (currentDates, previousDates) = WeeklyTrendCalculator.windowsFor(today)
        val current = WeeklyTrendCalculator.buildWindow(currentDates, inputsByDate)
        val previous = WeeklyTrendCalculator.buildWindow(previousDates, inputsByDate)
        val comparison = WeeklyTrendCalculator.compare(current, previous)
        val weekdayWeekend = WeeklyTrendCalculator.weekdayWeekend(current.days)
        val digest = WeeklyDigest(
            currentWindow = current,
            previousWindow = previous,
            comparison = comparison,
            weekdayWeekend = weekdayWeekend,
            focusMs = current.focusMs,
            reflections = emptyList()
        )
        return digest.copy(reflections = WeeklyReflectionEngine.reflect(digest))
    }
}
