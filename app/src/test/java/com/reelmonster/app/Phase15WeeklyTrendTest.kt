package com.reelmonster.app

import com.reelmonster.app.domain.model.weekly.WeeklyConfidence
import com.reelmonster.app.domain.model.weekly.WeeklyDayInput
import com.reelmonster.app.domain.model.weekly.WeeklyTrendCalculator
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 15 — Weekly intelligence tests (proposal §13, criteria 11–19).
 */
class Phase15WeeklyTrendTest {

    private val today = LocalDate.of(2026, 11, 8) // a Sunday
    private val threeHours = 3 * 3_600_000L
    private val twoHours = 2 * 3_600_000L

    private fun day(
        date: LocalDate,
        reels: Int,
        viewingMs: Long?,
        verifiedMs: Long,
        focusMs: Long = 0L
    ) = WeeklyDayInput(
        date = date,
        reelCount = reels,
        instagramCount = reels / 2,
        youtubeCount = reels - reels / 2,
        actualViewingMs = viewingMs,
        verifiedCoverageMs = verifiedMs,
        focusMs = focusMs
    )

    /** 7 adequate days: 10 reels, 10 min viewing, 3h coverage each day. */
    private fun fullWeek(dates: List<LocalDate>, reelsPerDay: Int, viewingMinPerDay: Long) =
        dates.associateWith { date ->
            day(date, reelsPerDay, viewingMinPerDay * 60_000L, threeHours)
        }

    @Test
    fun testExactWeeklyWindows() {
        val (current, previous) = WeeklyTrendCalculator.windowsFor(today)
        assertEquals(
            listOf(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4),
                LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6),
                LocalDate.of(2026, 11, 7)
            ),
            current
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 26),
                LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28),
                LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 30),
                LocalDate.of(2026, 10, 31)
            ),
            previous
        )
    }

    @Test
    fun testTodayExcludedFromBothWindows() {
        val (current, previous) = WeeklyTrendCalculator.windowsFor(today)
        assertTrue(today !in current)
        assertTrue(today !in previous)
        assertEquals(7, current.size)
        assertEquals(7, previous.size)
    }

    @Test
    fun testZeroToThreeAdequateDaysIsInsufficientAndSuppressesPercentages() {
        val (currentDates, previousDates) = WeeklyTrendCalculator.windowsFor(today)
        val inputs = fullWeek(currentDates, 10, 10) +
            fullWeek(previousDates, 10, 10).mapValues { (_, input) ->
                // Only 3 adequate days in the previous window (Oct 29–31).
                if (input.date.dayOfMonth <= 28) input.copy(verifiedCoverageMs = 0L) else input
            }
        val current = WeeklyTrendCalculator.buildWindow(currentDates, inputs)
        val previous = WeeklyTrendCalculator.buildWindow(previousDates, inputs)
        assertEquals(WeeklyConfidence.HIGH, current.confidence)
        assertEquals(3, previous.adequateDays)
        assertEquals(WeeklyConfidence.INSUFFICIENT, previous.confidence)
        val comparison = WeeklyTrendCalculator.compare(current, previous)
        assertTrue(comparison.percentagesSuppressed)
        assertNull(comparison.reelRateDeltaPercent)
        assertNull(comparison.viewRateDeltaPercent)
        assertEquals(
            "There is not enough monitoring data to compare these periods reliably.",
            comparison.summary
        )
    }

    @Test
    fun testFourToFiveAdequateDaysIsLimited() {
        val (currentDates, _) = WeeklyTrendCalculator.windowsFor(today)
        val inputs = currentDates.associateWith { date ->
            // 5 adequate days, 2 with no coverage.
            val adequate = date.dayOfMonth <= 5
            day(date, 10, 600_000L, if (adequate) threeHours else 0L)
        }
        val window = WeeklyTrendCalculator.buildWindow(currentDates, inputs)
        assertEquals(5, window.adequateDays)
        assertEquals(WeeklyConfidence.LIMITED, window.confidence)
    }

    @Test
    fun testSixToSevenAdequateDaysIsHigh() {
        val (currentDates, _) = WeeklyTrendCalculator.windowsFor(today)
        val window = WeeklyTrendCalculator.buildWindow(
            currentDates, fullWeek(currentDates, 10, 10)
        )
        assertEquals(7, window.adequateDays)
        assertEquals(WeeklyConfidence.HIGH, window.confidence)
    }

    @Test
    fun testDifferentConfidenceUsesLower() {
        val (currentDates, previousDates) = WeeklyTrendCalculator.windowsFor(today)
        val inputs = fullWeek(currentDates, 10, 10) +
            fullWeek(previousDates, 10, 10).mapValues { (_, input) ->
                // 4 adequate days in the previous window (Oct 28–31).
                if (input.date.dayOfMonth <= 27) input.copy(verifiedCoverageMs = 0L) else input
            }
        val current = WeeklyTrendCalculator.buildWindow(currentDates, inputs)
        val previous = WeeklyTrendCalculator.buildWindow(previousDates, inputs)
        assertEquals(WeeklyConfidence.HIGH, current.confidence)
        assertEquals(4, previous.adequateDays)
        assertEquals(WeeklyConfidence.LIMITED, previous.confidence)
        val comparison = WeeklyTrendCalculator.compare(current, previous)
        assertEquals(WeeklyConfidence.LIMITED, comparison.comparisonConfidence)
    }

    @Test
    fun testZeroBaselineBothZeroHasNoPercentage() {
        val (currentDates, previousDates) = WeeklyTrendCalculator.windowsFor(today)
        val inputs = fullWeek(currentDates, 0, 0) + fullWeek(previousDates, 0, 0)
        val current = WeeklyTrendCalculator.buildWindow(currentDates, inputs)
        val previous = WeeklyTrendCalculator.buildWindow(previousDates, inputs)
        val comparison = WeeklyTrendCalculator.compare(current, previous)
        assertNull(comparison.reelRateDeltaPercent)
        assertNull(comparison.viewRateDeltaPercent)
        assertEquals("No observed activity in either period.", comparison.summary)
    }

    @Test
    fun testZeroBaselinePreviousZeroCurrentPositiveHasNoPercentage() {
        val (currentDates, previousDates) = WeeklyTrendCalculator.windowsFor(today)
        val inputs = fullWeek(currentDates, 10, 10) + fullWeek(previousDates, 0, 0)
        val current = WeeklyTrendCalculator.buildWindow(currentDates, inputs)
        val previous = WeeklyTrendCalculator.buildWindow(previousDates, inputs)
        val comparison = WeeklyTrendCalculator.compare(current, previous)
        assertNull(comparison.reelRateDeltaPercent)
        assertTrue(comparison.reelRateDifference!! > 0.0)
        assertEquals("New observed activity in the current period.", comparison.summary)
    }

    @Test
    fun testPreviousPositiveCurrentZeroIsMinusOneHundredPercent() {
        val (currentDates, previousDates) = WeeklyTrendCalculator.windowsFor(today)
        val inputs = fullWeek(currentDates, 0, 0) + fullWeek(previousDates, 10, 10)
        val current = WeeklyTrendCalculator.buildWindow(currentDates, inputs)
        val previous = WeeklyTrendCalculator.buildWindow(previousDates, inputs)
        val comparison = WeeklyTrendCalculator.compare(current, previous)
        assertEquals(-100.0, comparison.reelRateDeltaPercent!!, 0.001)
        assertEquals(-100.0, comparison.viewRateDeltaPercent!!, 0.001)
    }

    @Test
    fun testMissingDaysAreNotZeroUse() {
        val (currentDates, _) = WeeklyTrendCalculator.windowsFor(today)
        // No inputs at all: every day is missing, not zero-use.
        val window = WeeklyTrendCalculator.buildWindow(currentDates, emptyMap())
        assertEquals(0, window.adequateDays)
        assertEquals(WeeklyConfidence.INSUFFICIENT, window.confidence)
        assertEquals(0, window.reelCount)
        assertEquals(7, window.missingViewingDays)
        assertNull(window.reelsPerVerifiedHour)
    }

    @Test
    fun testDeltaMathIsOneHundredPercentIncrease() {
        val (currentDates, previousDates) = WeeklyTrendCalculator.windowsFor(today)
        val inputs = fullWeek(currentDates, 20, 20) + fullWeek(previousDates, 10, 10)
        val current = WeeklyTrendCalculator.buildWindow(currentDates, inputs)
        val previous = WeeklyTrendCalculator.buildWindow(previousDates, inputs)
        val comparison = WeeklyTrendCalculator.compare(current, previous)
        assertEquals(100.0, comparison.reelRateDeltaPercent!!, 0.001)
        assertEquals(100.0, comparison.viewRateDeltaPercent!!, 0.001)
    }

    @Test
    fun testWeekdayWeekendRatesAccountForCoverageAndDayCounts() {
        // Current window Nov 1 (Sun) – Nov 7 (Sat): 5 weekdays, 2 weekend days.
        val (currentDates, _) = WeeklyTrendCalculator.windowsFor(today)
        val inputs = currentDates.associateWith { date ->
            val weekend = date.dayOfWeek == java.time.DayOfWeek.SATURDAY ||
                date.dayOfWeek == java.time.DayOfWeek.SUNDAY
            if (weekend) day(date, 20, 3_600_000L, twoHours)
            else day(date, 10, 600_000L, twoHours)
        }
        val window = WeeklyTrendCalculator.buildWindow(currentDates, inputs)
        val comparison = WeeklyTrendCalculator.weekdayWeekend(window.days)
        assertEquals(5, comparison.weekday.dayCount)
        assertEquals(2, comparison.weekend.dayCount)
        assertEquals(50, comparison.weekday.reelCount)
        assertEquals(40, comparison.weekend.reelCount)
        // Weekday: 50 reels / 10 verified hours = 5.0/h.
        assertEquals(5.0, comparison.weekday.reelsPerMonitoredHour!!, 0.001)
        // Weekend: 40 reels / 4 verified hours = 10.0/h.
        assertEquals(10.0, comparison.weekend.reelsPerMonitoredHour!!, 0.001)
    }

    @Test
    fun testWeekdayWeekendRatesNullWithoutCoverage() {
        val (currentDates, _) = WeeklyTrendCalculator.windowsFor(today)
        val inputs = currentDates.associateWith { date -> day(date, 10, 600_000L, 0L) }
        val window = WeeklyTrendCalculator.buildWindow(currentDates, inputs)
        val comparison = WeeklyTrendCalculator.weekdayWeekend(window.days)
        assertNull(comparison.weekday.reelsPerMonitoredHour)
        assertNull(comparison.weekend.viewingMinutesPerMonitoredHour)
    }

    @Test
    fun testAdequateThresholdBoundary() {
        val (currentDates, _) = WeeklyTrendCalculator.windowsFor(today)
        val exactly = WeeklyTrendCalculator.buildWindow(
            currentDates,
            currentDates.associateWith { date ->
                day(date, 0, null, WeeklyTrendCalculator.ADEQUATE_DAY_THRESHOLD_MS)
            }
        )
        assertEquals(7, exactly.adequateDays)
        val justBelow = WeeklyTrendCalculator.buildWindow(
            currentDates,
            currentDates.associateWith { date ->
                day(date, 0, null, WeeklyTrendCalculator.ADEQUATE_DAY_THRESHOLD_MS - 1)
            }
        )
        assertEquals(0, justBelow.adequateDays)
    }

    @Test
    fun testAttributionConsistencyEnforced() {
        try {
            WeeklyDayInput(
                date = today, reelCount = 10, instagramCount = 6, youtubeCount = 3,
                actualViewingMs = null, verifiedCoverageMs = 0L, focusMs = 0L
            )
            throw AssertionError("Expected IllegalArgumentException for 6 + 3 != 10")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("must equal Instagram"))
        }
    }
}
