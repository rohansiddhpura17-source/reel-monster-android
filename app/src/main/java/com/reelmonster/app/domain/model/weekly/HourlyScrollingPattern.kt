package com.reelmonster.app.domain.model.weekly

import com.reelmonster.app.domain.model.ReelLogItem
import com.reelmonster.app.domain.model.ViewingSession
import com.reelmonster.app.domain.model.monitoring.CoverageCalculator
import com.reelmonster.app.domain.model.monitoring.MonitoringInterval
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Phase 15 — Hourly Scrolling Pattern (proposal §2).
 *
 * 24 local clock-hour buckets. For each hour `h`:
 * - `C(h)`: eligible verified monitoring coverage overlapping that hour.
 * - `R(h)`: distinct deduplicated Reel events attributed to that hour.
 * - `T(h)`: actual observed viewing-session duration allocated to that hour.
 *
 * Coverage-normalized metrics (only when `C(h) > 0`):
 * - `reels_per_monitored_hour(h) = R(h) / (C(h) / 3,600,000)`
 * - `viewing_minutes_per_monitored_hour(h) = (T(h) / 60,000) / (C(h) / 3,600,000)`
 *
 * Raw totals and normalized rates are always shown separately.
 * Nothing is extrapolated into unmonitored time, and missing
 * monitoring is never treated as zero scrolling.
 *
 * Coverage is derived only from persisted intervals eligible under
 * Phase 14 rules ([MonitoringInterval.isEligibleForVerifiedCoverage]).
 * Intervals are clipped to local-hour boundaries and overlapping
 * wall-clock time is merged so it is counted once (the same anchoring
 * Phase 14 daily coverage uses: wall start + monotonic duration;
 * monotonic clocks are never compared across boots). Ineligible
 * intervals contribute zero coverage. Heartbeats alone never prove
 * coverage. Sessions are split at every hour and local-midnight
 * boundary; an entire cross-boundary session is never assigned to
 * its start hour.
 */
enum class HourlyCoverageStatus(val displayLabel: String) {
    /** `C(h) = 0`: no eligible monitoring at all in this hour. */
    UNMONITORED("Unmonitored"),

    /** `0 < coverage_ratio(h) < 0.25`. */
    INSUFFICIENT("Insufficient data"),

    /** `0.25 <= coverage_ratio(h) < 0.80`. */
    PARTIAL("Partially monitored"),

    /** `coverage_ratio(h) >= 0.80`. Proposal label: "Well monitored". */
    WELL_MONITORED("Well monitored")
}

data class HourlyBucket(
    val hour: Int,
    /** Distinct deduplicated Reel events in this hour. */
    val reelCount: Int,
    /** Actual observed viewing duration allocated to this hour, in ms. */
    val actualViewingMs: Long,
    /** Eligible verified monitoring coverage in this hour, in ms. */
    val coverageMs: Long,
    /** Real elapsed duration belonging to this local hour within the window, in ms. */
    val availableMs: Long,
    /** `coverageMs / availableMs`, or 0.0 when `availableMs == 0`. */
    val coverageRatio: Double,
    val status: HourlyCoverageStatus,
    /** Null when `coverageMs == 0`: no rate can be computed. */
    val reelsPerMonitoredHour: Double?,
    /** Null when `coverageMs == 0`: no rate can be computed. */
    val viewingMinutesPerMonitoredHour: Double?
)

object HourlyScrollingPatternCalculator {

    /**
     * Computes the 24 hourly buckets for [windowStartMs] (inclusive) to
     * [windowEndMs] (exclusive), in [zoneId].
     *
     * The window is normally the current seven complete days
     * `[D-7, D-1]`; the calculator itself accepts any window.
     */
    fun computeHourlyPattern(
        events: List<ReelLogItem>,
        sessions: List<ViewingSession>,
        intervals: List<MonitoringInterval>,
        windowStartMs: Long,
        windowEndMs: Long,
        zoneId: ZoneId
    ): List<HourlyBucket> {
        require(windowEndMs >= windowStartMs) { "windowEndMs must be >= windowStartMs" }

        // Instant ranges (wall-clock ms) for each local hour, DST-aware:
        // a skipped hour has no ranges, a repeated hour has two.
        val hourRanges = hourInstantRanges(windowStartMs, windowEndMs, zoneId)
        val availableLongByHour = LongArray(24)
        for (hour in 0..23) {
            var total = 0L
            for ((rangeStart, rangeEnd) in hourRanges[hour]) {
                total += overlapMs(rangeStart, rangeEnd, windowStartMs, windowEndMs)
            }
            availableLongByHour[hour] = total
        }

        // R(h): distinct events by id, attributed by local hour, in-window only.
        val reelCountByHour = IntArray(24)
        for (event in events.distinctBy { it.id }) {
            if (event.timestamp < windowStartMs || event.timestamp >= windowEndMs) continue
            val hour = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(event.timestamp), zoneId
            ).hour
            reelCountByHour[hour] += event.countIncrement.coerceAtLeast(0)
        }

        // T(h): sessions split at hour boundaries, capped at durationMs.
        val viewingMsByHour = LongArray(24)
        for (session in sessions.distinctBy { it.id }) {
            val durationMs = session.durationMs.coerceAtLeast(0L)
            if (durationMs == 0L) continue
            val start = session.startTimestamp
            val end = session.endTimestamp
            if (end <= start) continue
            val perHour = LongArray(24)
            var allocatedTotal = 0L
            for (hour in 0..23) {
                var allocated = 0L
                for ((rangeStart, rangeEnd) in hourRanges[hour]) {
                    allocated += overlapMs(start, end, rangeStart, rangeEnd)
                }
                perHour[hour] = allocated
                allocatedTotal += allocated
            }
            if (allocatedTotal <= 0L) continue
            // Wall-clock overlap can exceed the monotonic duration when the
            // clock moved; scale proportionally so the session never
            // contributes more than its measured duration.
            val scale = if (allocatedTotal > durationMs) {
                durationMs.toDouble() / allocatedTotal.toDouble()
            } else {
                1.0
            }
            for (hour in 0..23) {
                viewingMsByHour[hour] += (perHour[hour] * scale).toLong()
            }
        }

        // C(h): eligible intervals only, clipped per hour, then merged
        // so overlapping wall-clock time is counted once. Effective wall
        // range per interval is startTimestamp + monotonic duration
        // (the same anchoring Phase 14 daily coverage uses); monotonic
        // clocks are never compared across boots — only the resulting
        // wall ranges are merged, exactly as
        // CoverageCalculator.calculateDailyCoverage does.
        val coverageMsByHour = LongArray(24)
        val eligibleRanges = intervals
            .filter { it.isEligibleForVerifiedCoverage }
            .mapNotNull { interval ->
                val duration = interval.monotonicDurationMs
                if (duration <= 0L) return@mapNotNull null
                val rangeStart = interval.startTimestamp
                val rangeEnd = rangeStart + duration
                if (rangeEnd <= windowStartMs || rangeStart >= windowEndMs) return@mapNotNull null
                rangeStart to rangeEnd
            }
        for (hour in 0..23) {
            val clipped = mutableListOf<Pair<Long, Long>>()
            for ((rangeStart, rangeEnd) in eligibleRanges) {
                for ((hourStart, hourEnd) in hourRanges[hour]) {
                    val clipStart = maxOf(rangeStart, hourStart, windowStartMs)
                    val clipEnd = minOf(rangeEnd, hourEnd, windowEndMs)
                    if (clipEnd > clipStart) clipped.add(clipStart to clipEnd)
                }
            }
            if (clipped.isNotEmpty()) {
                coverageMsByHour[hour] =
                    CoverageCalculator.mergeNonOverlappingDurationMs(clipped)
            }
        }

        return (0..23).map { hour ->
            val coverageMs = coverageMsByHour[hour]
            val availableMs = availableLongByHour[hour]
            val coverageRatio = if (availableMs > 0L) {
                coverageMs.toDouble() / availableMs.toDouble()
            } else {
                0.0
            }
            val status = when {
                coverageMs == 0L -> HourlyCoverageStatus.UNMONITORED
                coverageRatio < 0.25 -> HourlyCoverageStatus.INSUFFICIENT
                coverageRatio < 0.80 -> HourlyCoverageStatus.PARTIAL
                else -> HourlyCoverageStatus.WELL_MONITORED
            }
            val coverageHours = coverageMs.toDouble() / 3_600_000.0
            HourlyBucket(
                hour = hour,
                reelCount = reelCountByHour[hour],
                actualViewingMs = viewingMsByHour[hour],
                coverageMs = coverageMs,
                availableMs = availableMs,
                coverageRatio = coverageRatio,
                status = status,
                reelsPerMonitoredHour = if (coverageMs > 0L) {
                    reelCountByHour[hour] / coverageHours
                } else {
                    null
                },
                viewingMinutesPerMonitoredHour = if (coverageMs > 0L) {
                    (viewingMsByHour[hour] / 60_000.0) / coverageHours
                } else {
                    null
                }
            )
        }
    }

    /**
     * For each local hour 0..23, the list of [start, end) instant ranges
     * (epoch ms) in which the local time has that hour, restricted to
     * local dates touched by the window. A DST-skipped hour yields no
     * ranges; a DST-repeated hour yields two.
     */
    internal fun hourInstantRanges(
        windowStartMs: Long,
        windowEndMs: Long,
        zoneId: ZoneId
    ): List<List<Pair<Long, Long>>> {
        val ranges = List(24) { mutableListOf<Pair<Long, Long>>() }
        if (windowEndMs <= windowStartMs) return ranges
        val startDate = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(windowStartMs), zoneId
        ).toLocalDate()
        // Include the end date too: a window ending exactly at midnight
        // still touches the previous date only, but an end inside a day
        // needs that day's hours for overlap clipping.
        val endDate = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli((windowEndMs - 1).coerceAtLeast(windowStartMs)), zoneId
        ).toLocalDate()
        var date = startDate
        while (!date.isAfter(endDate)) {
            for (hour in 0..23) {
                val localTime = LocalDateTime.of(date, LocalTime.of(hour, 0))
                val offsets = zoneId.rules.getValidOffsets(localTime)
                for (offset in offsets) {
                    val startInstant = ZonedDateTime.ofLocal(localTime, zoneId, offset)
                        .toInstant().toEpochMilli()
                    val endInstant = startInstant + 3_600_000L
                    // A repeated hour's second occurrence is +1h of wall
                    // time after the first; ofLocal with each offset gives
                    // the correct distinct instants, and +1h from each is
                    // the end of that occurrence.
                    ranges[hour].add(startInstant to endInstant)
                }
            }
            date = date.plusDays(1)
        }
        return ranges
    }

    private fun overlapMs(
        start: Long,
        end: Long,
        rangeStart: Long,
        rangeEnd: Long
    ): Long {
        return (minOf(end, rangeEnd) - maxOf(start, rangeStart)).coerceAtLeast(0L)
    }
}
