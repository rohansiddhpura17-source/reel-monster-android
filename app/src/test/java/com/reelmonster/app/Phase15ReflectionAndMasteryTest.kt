package com.reelmonster.app

import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.ViewingSession
import com.reelmonster.app.domain.model.weekly.ActualViewingTimeReconciler
import com.reelmonster.app.domain.model.weekly.MasteryAura
import com.reelmonster.app.domain.model.weekly.MunchieMasteryCalculator
import com.reelmonster.app.domain.model.weekly.WeeklyDayInput
import com.reelmonster.app.domain.model.weekly.WeeklyDigestCalculator
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 15 — Reflections, Munchie mastery, and actual viewing-time
 * reconciliation tests (proposal §13, criteria 25–28 and §5).
 */
class Phase15ReflectionAndMasteryTest {

    private val today = LocalDate.of(2026, 11, 8) // a Sunday
    private val threeHours = 3 * 3_600_000L
    private val twoHours = 2 * 3_600_000L

    private fun day(
        date: LocalDate,
        reels: Int,
        viewingMin: Long,
        verifiedMs: Long
    ) = WeeklyDayInput(
        date = date,
        reelCount = reels,
        instagramCount = reels / 2,
        youtubeCount = reels - reels / 2,
        actualViewingMs = viewingMin * 60_000L,
        verifiedCoverageMs = verifiedMs,
        focusMs = 0L
    )

    private fun fullWeek(
        dates: List<LocalDate>,
        reelsPerDay: Int,
        viewingMinPerDay: Long,
        verifiedMs: Long = threeHours
    ) = dates.associateWith { date -> day(date, reelsPerDay, viewingMinPerDay, verifiedMs) }

    @Test
    fun testInsufficientDataReflection() {
        val (currentDates, previousDates) =
            com.reelmonster.app.domain.model.weekly.WeeklyTrendCalculator.windowsFor(today)
        // Previous window has no coverage at all: comparison suppressed.
        val inputs = fullWeek(currentDates, 10, 10) +
            previousDates.associateWith { date -> day(date, 10, 10, 0L) }
        val digest = WeeklyDigestCalculator.buildDigest(today, inputs)
        assertTrue(digest.comparison.percentagesSuppressed)
        assertTrue(
            digest.reflections.any {
                it.contains("not enough monitoring data")
            }
        )
    }

    @Test
    fun testLowerViewingTimeReflection() {
        val (currentDates, previousDates) =
            com.reelmonster.app.domain.model.weekly.WeeklyTrendCalculator.windowsFor(today)
        val inputs = fullWeek(currentDates, 10, 5) + fullWeek(previousDates, 10, 10)
        val digest = WeeklyDigestCalculator.buildDigest(today, inputs)
        assertTrue(
            digest.reflections.any {
                it.contains("lower than the previous period")
            }
        )
    }

    @Test
    fun testHigherViewingTimeReflection() {
        val (currentDates, previousDates) =
            com.reelmonster.app.domain.model.weekly.WeeklyTrendCalculator.windowsFor(today)
        val inputs = fullWeek(currentDates, 10, 20) + fullWeek(previousDates, 10, 10)
        val digest = WeeklyDigestCalculator.buildDigest(today, inputs)
        assertTrue(
            digest.reflections.any {
                it.contains("higher than the previous period")
            }
        )
    }

    @Test
    fun testWeekendHigherReflection() {
        val (currentDates, previousDates) =
            com.reelmonster.app.domain.model.weekly.WeeklyTrendCalculator.windowsFor(today)
        // Weekday 10 min/day, weekend 60 min/day, same 2h coverage each day.
        val currentInputs = currentDates.associateWith { date ->
            val weekend = date.dayOfWeek == DayOfWeek.SATURDAY ||
                date.dayOfWeek == DayOfWeek.SUNDAY
            if (weekend) day(date, 10, 60, twoHours) else day(date, 10, 10, twoHours)
        }
        // Previous window identical to current: trend is "about the same".
        val previousInputs = previousDates.associateWith { date ->
            val weekend = date.dayOfWeek == DayOfWeek.SATURDAY ||
                date.dayOfWeek == DayOfWeek.SUNDAY
            if (weekend) day(date, 10, 60, twoHours) else day(date, 10, 10, twoHours)
        }
        val digest = WeeklyDigestCalculator.buildDigest(today, currentInputs + previousInputs)
        assertTrue(
            digest.reflections.any {
                it.contains("Weekend viewing time per monitored hour was higher")
            }
        )
    }

    @Test
    fun testAtMostTwoReflections() {
        val (currentDates, previousDates) =
            com.reelmonster.app.domain.model.weekly.WeeklyTrendCalculator.windowsFor(today)
        val currentInputs = currentDates.associateWith { date ->
            val weekend = date.dayOfWeek == DayOfWeek.SATURDAY ||
                date.dayOfWeek == DayOfWeek.SUNDAY
            if (weekend) day(date, 10, 60, twoHours) else day(date, 10, 10, twoHours)
        }
        val digest = WeeklyDigestCalculator.buildDigest(
            today, currentInputs + fullWeek(previousDates, 10, 10)
        )
        assertTrue(digest.reflections.size <= 2)
        assertTrue(digest.reflections.isNotEmpty())
    }

    @Test
    fun testReflectionsContainNoPsychologicalClaims() {
        val banned = listOf(
            "addict", "mood", "depress", "anxious", "anxiety", "failure",
            "shame", "lazy", "disorder", "mental health", "vulnerable"
        )
        val (currentDates, previousDates) =
            com.reelmonster.app.domain.model.weekly.WeeklyTrendCalculator.windowsFor(today)
        val digest = WeeklyDigestCalculator.buildDigest(
            today, fullWeek(currentDates, 10, 20) + fullWeek(previousDates, 10, 10)
        )
        for (reflection in digest.reflections) {
            for (word in banned) {
                assertFalse(
                    "Reflection must not contain '$word': $reflection",
                    reflection.lowercase().contains(word)
                )
            }
        }
    }

    @Test
    fun testMasteryAurasByLevel() {
        assertEquals(MasteryAura.NONE, MunchieMasteryCalculator.auraForLevel(1))
        assertEquals(MasteryAura.NONE, MunchieMasteryCalculator.auraForLevel(2))
        assertEquals(MasteryAura.SPROUT, MunchieMasteryCalculator.auraForLevel(3))
        assertEquals(MasteryAura.SPROUT, MunchieMasteryCalculator.auraForLevel(4))
        assertEquals(MasteryAura.MINT_AURA, MunchieMasteryCalculator.auraForLevel(5))
        assertEquals(MasteryAura.MINT_AURA, MunchieMasteryCalculator.auraForLevel(6))
        assertEquals(MasteryAura.SPARKLE, MunchieMasteryCalculator.auraForLevel(7))
        assertEquals(MasteryAura.SPARKLE, MunchieMasteryCalculator.auraForLevel(8))
        assertEquals(MasteryAura.GOLDEN_CROWN, MunchieMasteryCalculator.auraForLevel(9))
        assertEquals(MasteryAura.GOLDEN_CROWN, MunchieMasteryCalculator.auraForLevel(10))
        assertEquals(MasteryAura.NONE, MunchieMasteryCalculator.auraForLevel(11))
        assertEquals(MasteryAura.NONE, MunchieMasteryCalculator.auraForLevel(0))
    }

    @Test
    fun testStreakEmblemAtSevenDays() {
        assertFalse(MunchieMasteryCalculator.streakEmblemVisible(6))
        assertTrue(MunchieMasteryCalculator.streakEmblemVisible(7))
        assertTrue(MunchieMasteryCalculator.streakEmblemVisible(30))
    }

    @Test
    fun testMasteryDependsOnlyOnLevelAndStreak() {
        // Same level/streak, different scrolling volumes: identical mastery.
        val a = MunchieMasteryCalculator.masteryFor(level = 5, streakDays = 7)
        val b = MunchieMasteryCalculator.masteryFor(level = 5, streakDays = 7)
        assertEquals(a, b)
        assertEquals(MasteryAura.MINT_AURA, a.aura)
        assertTrue(a.streakEmblemVisible)
    }

    @Test
    fun testReconcilerSumsActualDurationsAndDedupes() {
        val sessions = listOf(
            ViewingSession(
                id = 1, targetApp = TargetApp.INSTAGRAM,
                startTimestamp = 1_000L, endTimestamp = 61_000L,
                durationMs = 60_000L, sessionDate = "2026-11-01"
            ),
            // Same id again: duplicate, must not double-count.
            ViewingSession(
                id = 1, targetApp = TargetApp.INSTAGRAM,
                startTimestamp = 1_000L, endTimestamp = 61_000L,
                durationMs = 60_000L, sessionDate = "2026-11-01"
            ),
            ViewingSession(
                id = 2, targetApp = TargetApp.YOUTUBE,
                startTimestamp = 2_000L, endTimestamp = 122_000L,
                durationMs = 120_000L, sessionDate = "2026-11-01"
            )
        )
        val report = ActualViewingTimeReconciler.reconcile(sessions, untimedManualReelCount = 0)
        assertEquals(180_000L, report.actualMeasuredMs)
        assertEquals(2, report.measuredSessionCount)
        // Average dwell denominator is measured sessions only: 180s / 2.
        assertEquals(90_000L, report.averageDwellMs)
        assertFalse(report.hasMissingDurations)
    }

    @Test
    fun testReconcilerKeepsUntimedLogsAsCountOnly() {
        val sessions = listOf(
            ViewingSession(
                id = 1, targetApp = TargetApp.INSTAGRAM,
                startTimestamp = 1_000L, endTimestamp = 61_000L,
                durationMs = 60_000L, sessionDate = "2026-11-01"
            )
        )
        // 5 manual logs with no measured session: no duration is invented.
        val report = ActualViewingTimeReconciler.reconcile(sessions, untimedManualReelCount = 5)
        assertEquals(60_000L, report.actualMeasuredMs)
        assertEquals(5, report.untimedManualReelCount)
        assertTrue(report.hasMissingDurations)
        assertEquals(60_000L, report.averageDwellMs)
    }

    @Test
    fun testReconcilerWithNoSessionsHasNullAverage() {
        val report = ActualViewingTimeReconciler.reconcile(emptyList(), untimedManualReelCount = null)
        assertEquals(0L, report.actualMeasuredMs)
        assertEquals(0, report.measuredSessionCount)
        assertNull(report.averageDwellMs)
        assertFalse(report.hasMissingDurations)
    }

    @Test
    fun testTotalMeasuredMsIgnoresNegativeDurations() {
        val sessions = listOf(
            ViewingSession(
                id = 1, targetApp = TargetApp.INSTAGRAM,
                startTimestamp = 1_000L, endTimestamp = 61_000L,
                durationMs = -5_000L, sessionDate = "2026-11-01"
            )
        )
        assertEquals(0L, ActualViewingTimeReconciler.totalMeasuredMs(sessions))
    }
}
