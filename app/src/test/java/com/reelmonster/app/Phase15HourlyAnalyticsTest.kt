package com.reelmonster.app

import com.reelmonster.app.domain.model.ReelLogItem
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import com.reelmonster.app.domain.model.ViewingSession
import com.reelmonster.app.domain.model.monitoring.ContinuityStatus
import com.reelmonster.app.domain.model.monitoring.MonitoringInterval
import com.reelmonster.app.domain.model.weekly.HourlyCoverageStatus
import com.reelmonster.app.domain.model.weekly.HourlyScrollingPatternCalculator
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 15 — Hourly analytics tests (proposal §13, criteria 1–10).
 *
 * Pure-Kotlin calculators: no Android framework, deterministic zones.
 */
class Phase15HourlyAnalyticsTest {

    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val newYork = ZoneId.of("America/New_York")

    private fun ms(zone: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    private fun interval(
        startMs: Long,
        endMs: Long,
        boot: String = "boot-1",
        eligible: Boolean = true,
        dateKey: String = "2026-11-01"
    ) = MonitoringInterval(
        dateKey = dateKey,
        startTimestamp = startMs,
        endTimestamp = endMs,
        monotonicStartMs = startMs,
        monotonicEndMs = endMs,
        serviceConnected = eligible,
        trackingEnabled = eligible,
        screenOn = eligible,
        continuityStatus = if (eligible) ContinuityStatus.VERIFIED else ContinuityStatus.UNCERTAIN,
        bootSessionId = boot
    )

    private fun event(id: Long, ts: Long) = ReelLogItem(
        id = id,
        timestamp = ts,
        targetApp = TargetApp.INSTAGRAM,
        trackingMode = TrackingMode.LEVEL_1_MANUAL,
        sessionDate = "2026-11-01"
    )

    private fun session(id: Long, startMs: Long, endMs: Long, durationMs: Long) =
        ViewingSession(
            id = id,
            targetApp = TargetApp.INSTAGRAM,
            startTimestamp = startMs,
            endTimestamp = endMs,
            durationMs = durationMs,
            sessionDate = "2026-11-01"
        )

    @Test
    fun testAll24HourBucketsReturned() {
        val start = ms(kolkata, 2026, 11, 1, 0)
        val end = ms(kolkata, 2026, 11, 2, 0)
        val buckets = HourlyScrollingPatternCalculator.computeHourlyPattern(
            emptyList(), emptyList(), emptyList(), start, end, kolkata
        )
        assertEquals(24, buckets.size)
        assertEquals((0..23).toList(), buckets.map { it.hour })
    }

    @Test
    fun testUnmonitoredHourIsNotZeroUsage() {
        val start = ms(kolkata, 2026, 11, 1, 0)
        val end = ms(kolkata, 2026, 11, 2, 0)
        // A reel was logged at 10:05, but there is no monitoring coverage.
        val buckets = HourlyScrollingPatternCalculator.computeHourlyPattern(
            listOf(event(1, ms(kolkata, 2026, 11, 1, 10, 5))),
            emptyList(), emptyList(), start, end, kolkata
        )
        val bucket = buckets[10]
        assertEquals(1, bucket.reelCount)
        assertEquals(0L, bucket.coverageMs)
        assertEquals(HourlyCoverageStatus.UNMONITORED, bucket.status)
        assertNull(bucket.reelsPerMonitoredHour)
        assertNull(bucket.viewingMinutesPerMonitoredHour)
    }

    @Test
    fun testWellMonitoredHourAndNormalization() {
        val start = ms(kolkata, 2026, 11, 1, 0)
        val end = ms(kolkata, 2026, 11, 2, 0)
        val buckets = HourlyScrollingPatternCalculator.computeHourlyPattern(
            events = listOf(
                event(1, ms(kolkata, 2026, 11, 1, 10, 5)),
                event(2, ms(kolkata, 2026, 11, 1, 10, 20))
            ),
            sessions = listOf(
                session(1, ms(kolkata, 2026, 11, 1, 10, 0), ms(kolkata, 2026, 11, 1, 10, 10), 600_000L)
            ),
            intervals = listOf(
                interval(ms(kolkata, 2026, 11, 1, 10, 0), ms(kolkata, 2026, 11, 1, 10, 30))
            ),
            windowStartMs = start, windowEndMs = end, zoneId = kolkata
        )
        val bucket = buckets[10]
        assertEquals(2, bucket.reelCount)
        assertEquals(1_800_000L, bucket.coverageMs)
        assertEquals(3_600_000L, bucket.availableMs)
        assertEquals(0.5, bucket.coverageRatio, 0.001)
        assertEquals(HourlyCoverageStatus.PARTIAL, bucket.status)
        assertEquals(4.0, bucket.reelsPerMonitoredHour!!, 0.001)
        assertEquals(20.0, bucket.viewingMinutesPerMonitoredHour!!, 0.001)
    }

    @Test
    fun testInsufficientCoverageBelowQuarter() {
        val start = ms(kolkata, 2026, 11, 1, 0)
        val end = ms(kolkata, 2026, 11, 2, 0)
        val buckets = HourlyScrollingPatternCalculator.computeHourlyPattern(
            emptyList(), emptyList(),
            listOf(interval(ms(kolkata, 2026, 11, 1, 10, 0), ms(kolkata, 2026, 11, 1, 10, 10))),
            start, end, kolkata
        )
        assertEquals(HourlyCoverageStatus.INSUFFICIENT, buckets[10].status)
    }

    @Test
    fun testWellMonitoredAtEightyPercent() {
        val start = ms(kolkata, 2026, 11, 1, 0)
        val end = ms(kolkata, 2026, 11, 2, 0)
        val buckets = HourlyScrollingPatternCalculator.computeHourlyPattern(
            emptyList(), emptyList(),
            listOf(interval(ms(kolkata, 2026, 11, 1, 10, 0), ms(kolkata, 2026, 11, 1, 10, 48))),
            start, end, kolkata
        )
        assertEquals(HourlyCoverageStatus.WELL_MONITORED, buckets[10].status)
    }

    @Test
    fun testOverlappingIntervalsMergedNotDoubleCounted() {
        val start = ms(kolkata, 2026, 11, 1, 0)
        val end = ms(kolkata, 2026, 11, 2, 0)
        val buckets = HourlyScrollingPatternCalculator.computeHourlyPattern(
            emptyList(), emptyList(),
            listOf(
                interval(ms(kolkata, 2026, 11, 1, 10, 0), ms(kolkata, 2026, 11, 1, 10, 30)),
                interval(ms(kolkata, 2026, 11, 1, 10, 15), ms(kolkata, 2026, 11, 1, 10, 45))
            ),
            start, end, kolkata
        )
        // 10:00–10:45 merged = 45 min, not 30 + 30.
        assertEquals(2_700_000L, buckets[10].coverageMs)
    }

    @Test
    fun testIneligibleIntervalsContributeZeroCoverage() {
        val start = ms(kolkata, 2026, 11, 1, 0)
        val end = ms(kolkata, 2026, 11, 2, 0)
        val buckets = HourlyScrollingPatternCalculator.computeHourlyPattern(
            emptyList(), emptyList(),
            listOf(
                interval(ms(kolkata, 2026, 11, 1, 10, 0), ms(kolkata, 2026, 11, 1, 10, 30), eligible = false)
            ),
            start, end, kolkata
        )
        assertEquals(0L, buckets[10].coverageMs)
        assertEquals(HourlyCoverageStatus.UNMONITORED, buckets[10].status)
    }

    @Test
    fun testDuplicateReelEventsDoNotInflateCounts() {
        val start = ms(kolkata, 2026, 11, 1, 0)
        val end = ms(kolkata, 2026, 11, 2, 0)
        val buckets = HourlyScrollingPatternCalculator.computeHourlyPattern(
            listOf(
                event(1, ms(kolkata, 2026, 11, 1, 10, 5)),
                event(1, ms(kolkata, 2026, 11, 1, 10, 5))
            ),
            emptyList(), emptyList(), start, end, kolkata
        )
        assertEquals(1, buckets[10].reelCount)
    }

    @Test
    fun testSessionSplitAtHourBoundaries() {
        val start = ms(kolkata, 2026, 11, 1, 0)
        val end = ms(kolkata, 2026, 11, 2, 0)
        val buckets = HourlyScrollingPatternCalculator.computeHourlyPattern(
            emptyList(),
            listOf(
                session(
                    1,
                    ms(kolkata, 2026, 11, 1, 10, 30),
                    ms(kolkata, 2026, 11, 1, 12, 0),
                    5_400_000L
                )
            ),
            emptyList(), start, end, kolkata
        )
        assertEquals(1_800_000L, buckets[10].actualViewingMs)
        assertEquals(3_600_000L, buckets[11].actualViewingMs)
        assertEquals(0L, buckets[12].actualViewingMs)
    }

    @Test
    fun testMidnightCrossingSessionSplitAcrossDates() {
        val start = ms(kolkata, 2026, 11, 1, 0)
        val end = ms(kolkata, 2026, 11, 3, 0)
        val buckets = HourlyScrollingPatternCalculator.computeHourlyPattern(
            emptyList(),
            listOf(
                session(
                    1,
                    ms(kolkata, 2026, 11, 1, 23, 30),
                    ms(kolkata, 2026, 11, 2, 0, 30),
                    3_600_000L
                )
            ),
            emptyList(), start, end, kolkata
        )
        assertEquals(1_800_000L, buckets[23].actualViewingMs)
        assertEquals(1_800_000L, buckets[0].actualViewingMs)
    }

    @Test
    fun testDstSpringForwardDayHas23Hours() {
        // 2026-03-08: clocks jump 02:00 -> 03:00 in America/New_York.
        val start = ms(newYork, 2026, 3, 8, 0)
        val end = ms(newYork, 2026, 3, 9, 0)
        val buckets = HourlyScrollingPatternCalculator.computeHourlyPattern(
            emptyList(), emptyList(), emptyList(), start, end, newYork
        )
        val totalAvailable = buckets.sumOf { it.availableMs }
        assertEquals(23 * 3_600_000L, totalAvailable)
        assertEquals(0L, buckets[2].availableMs)
        assertEquals(3_600_000L, buckets[3].availableMs)
    }

    @Test
    fun testDstFallBackDayHas25Hours() {
        // 2026-11-01: clocks fall back 02:00 -> 01:00 in America/New_York.
        val start = ms(newYork, 2026, 11, 1, 0)
        val end = ms(newYork, 2026, 11, 2, 0)
        val buckets = HourlyScrollingPatternCalculator.computeHourlyPattern(
            emptyList(), emptyList(), emptyList(), start, end, newYork
        )
        val totalAvailable = buckets.sumOf { it.availableMs }
        assertEquals(25 * 3_600_000L, totalAvailable)
        // The repeated hour is 01:00 (clocks fall back 02:00 -> 01:00);
        // 02:00 itself occurs only once.
        assertEquals(2 * 3_600_000L, buckets[1].availableMs)
        assertEquals(3_600_000L, buckets[2].availableMs)
    }

    @Test
    fun testOverlappingWallTimeCountedOnceAcrossBoots() {
        val start = ms(kolkata, 2026, 11, 1, 0)
        val end = ms(kolkata, 2026, 11, 2, 0)
        // Same wall-clock range in two different boot sessions: the
        // overlapping wall time is counted once (30 min), never summed
        // to 60 min, so coverage can never exceed available time.
        val buckets = HourlyScrollingPatternCalculator.computeHourlyPattern(
            emptyList(), emptyList(),
            listOf(
                interval(ms(kolkata, 2026, 11, 1, 10, 0), ms(kolkata, 2026, 11, 1, 10, 30), boot = "boot-1"),
                interval(ms(kolkata, 2026, 11, 1, 10, 0), ms(kolkata, 2026, 11, 1, 10, 30), boot = "boot-2")
            ),
            start, end, kolkata
        )
        assertEquals(1_800_000L, buckets[10].coverageMs)
        assertTrue(buckets[10].coverageRatio <= 1.0)
    }

    @Test
    fun testCoverageRatioUsesAvailableNotWallClock() {
        val start = ms(kolkata, 2026, 11, 1, 0)
        val end = ms(kolkata, 2026, 11, 2, 0)
        val buckets = HourlyScrollingPatternCalculator.computeHourlyPattern(
            emptyList(), emptyList(),
            listOf(interval(ms(kolkata, 2026, 11, 1, 10, 0), ms(kolkata, 2026, 11, 1, 10, 30))),
            start, end, kolkata
        )
        assertEquals(0.5, buckets[10].coverageRatio, 0.001)
        assertTrue(buckets[10].coverageRatio <= 1.0)
    }
}
