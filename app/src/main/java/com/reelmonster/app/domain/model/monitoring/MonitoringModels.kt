package com.reelmonster.app.domain.model.monitoring

/**
 * Continuity status of a recorded monitoring interval or heartbeat.
 */
enum class ContinuityStatus {
    VERIFIED,
    UNCERTAIN,
    INTERRUPTED;

    companion object {
        fun fromName(name: String?): ContinuityStatus {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: UNCERTAIN
        }
    }
}

/**
 * Classification of a calendar day's Estimated Monitoring Coverage.
 *
 * Documented Android Limitations:
 * - Android may interrupt or restrict background AccessibilityService execution.
 * - Service connection does not guarantee that every supported-app screen transition was detected.
 * - App updates and UI changes may affect detection.
 * - Screen state and lifecycle events may be delayed or unavailable.
 * - Heartbeats demonstrate service continuity, not perfect detection accuracy.
 * - Monitoring coverage is an estimate of eligible observation time, not proof of complete activity capture.
 */
enum class EstimatedCoverageStatus(val displayName: String, val badgeLabel: String) {
    ESTIMATED_VERIFIED("Estimated Monitoring Coverage: Verified (≥ 2h)", "Estimated Verified"),
    ESTIMATED_INCOMPLETE("Estimated Monitoring Coverage: Incomplete (< 2h)", "Estimated Incomplete"),
    UNVERIFIED("Estimated Monitoring Coverage: Unverified", "Unverified");

    companion object {
        fun fromName(name: String?): EstimatedCoverageStatus {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: UNVERIFIED
        }
    }
}

/**
 * Represents a recorded monitoring interval within a local calendar day.
 */
data class MonitoringInterval(
    val id: Long = 0L,
    val dateKey: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val monotonicStartMs: Long = startTimestamp,
    val monotonicEndMs: Long = endTimestamp,
    val serviceConnected: Boolean,
    val trackingEnabled: Boolean,
    val screenOn: Boolean,
    val interruptionReason: String? = null,
    val continuityStatus: ContinuityStatus = ContinuityStatus.VERIFIED,
    val bootSessionId: String = "",
    val isActive: Boolean = false,
    val estimatedVerifiedDurationMs: Long = calculateEligibleDuration(
        monotonicStartMs = monotonicStartMs,
        monotonicEndMs = monotonicEndMs,
        serviceConnected = serviceConnected,
        trackingEnabled = trackingEnabled,
        screenOn = screenOn,
        interruptionReason = interruptionReason,
        continuityStatus = continuityStatus
    )
) {
    val monotonicDurationMs: Long
        get() = (monotonicEndMs - monotonicStartMs).coerceAtLeast(0L)

    val isEligibleForVerifiedCoverage: Boolean
        get() = serviceConnected &&
            trackingEnabled &&
            screenOn &&
            interruptionReason == null &&
            continuityStatus == ContinuityStatus.VERIFIED

    companion object {
        fun calculateEligibleDuration(
            monotonicStartMs: Long,
            monotonicEndMs: Long,
            serviceConnected: Boolean,
            trackingEnabled: Boolean,
            screenOn: Boolean,
            interruptionReason: String?,
            continuityStatus: ContinuityStatus
        ): Long {
            if (!serviceConnected || !trackingEnabled || !screenOn) return 0L
            if (interruptionReason != null || continuityStatus != ContinuityStatus.VERIFIED) return 0L
            return (monotonicEndMs - monotonicStartMs).coerceAtLeast(0L)
        }
    }
}

/**
 * Continuity checkpoint record.
 * Validates continuity within an interval; never independently qualifies a day.
 */
data class MonitoringHeartbeat(
    val id: Long = 0L,
    val timestamp: Long,
    val monotonicMs: Long = timestamp,
    val dateKey: String,
    val serviceConnected: Boolean,
    val trackingEnabled: Boolean,
    val screenOn: Boolean,
    val continuityStatus: ContinuityStatus = ContinuityStatus.VERIFIED,
    val bootSessionId: String = ""
)

/**
 * Separate detection event record for supported short-form video platforms.
 * Demonstrates that the service observed supported-app activity; stored separately from coverage.
 */
data class SupportedAppDetectionEvent(
    val id: Long = 0L,
    val timestamp: Long,
    val dateKey: String,
    val platform: String,
    val eventType: String,
    val detectionStatus: String = "CONFIRMED",
    val contentFingerprint: String? = null
)

/**
 * Daily aggregate of separated monitoring coverage measurements for a local calendar day.
 */
data class DailyMonitoringCoverage(
    val dateKey: String,
    val serviceConnectedMs: Long = 0L,
    val trackingEnabledMs: Long = 0L,
    val screenOnMs: Long = 0L,
    val estimatedVerifiedMonitoringMs: Long = 0L,
    val heartbeatCount: Int = 0,
    val detectionEventCount: Int = 0,
    val interruptionCount: Int = 0,
    val recoveryCount: Int = 0,
    val coverageStatus: EstimatedCoverageStatus = EstimatedCoverageStatus.UNVERIFIED,
    val calculatedAt: Long = System.currentTimeMillis()
) {
    val isQualifiedForProgression: Boolean
        get() = estimatedVerifiedMonitoringMs >= CoverageCalculator.QUALIFICATION_THRESHOLD_MS

    val estimatedVerifiedMinutes: Int
        get() = (estimatedVerifiedMonitoringMs / 60_000L).toInt()

    val qualificationProgressFraction: Float
        get() = (estimatedVerifiedMonitoringMs.toFloat() / CoverageCalculator.QUALIFICATION_THRESHOLD_MS.toFloat())
            .coerceIn(0f, 1f)
}

/**
 * Deterministic calculator for Estimated Monitoring Coverage.
 *
 * Rules:
 * 1. Primary qualification criterion: estimated_verified_monitoring_ms >= 7,200,000 ms (120 minutes).
 * 2. Heartbeats are continuity evidence only and never independently satisfy the 120-minute requirement.
 * 3. Component durations (service_connected_ms, tracking_enabled_ms, screen_on_ms) are kept strictly separate.
 * 4. Overlapping intervals are merged so time is never double-counted.
 * 5. Uncertain or interrupted intervals are excluded from estimated_verified_monitoring_ms.
 */
object CoverageCalculator {
    /** 2 hours (120 minutes) in milliseconds. */
    const val QUALIFICATION_THRESHOLD_MS = 7_200_000L

    /** Standard continuity heartbeat interval (15 minutes). */
    const val HEARTBEAT_INTERVAL_MS = 900_000L

    /** Maximum uncheckpointed gap (20 minutes) before continuity is marked UNCERTAIN. */
    const val MAX_CONTINUITY_GAP_MS = 1_200_000L

    /**
     * Merges a list of [start, end] intervals and returns the total non-overlapping duration.
     */
    fun mergeNonOverlappingDurationMs(ranges: List<Pair<Long, Long>>): Long {
        val valid = ranges.filter { it.second > it.first }.sortedBy { it.first }
        if (valid.isEmpty()) return 0L

        var totalMs = 0L
        var currentStart = valid.first().first
        var currentEnd = valid.first().second

        for (i in 1 until valid.size) {
            val (nextStart, nextEnd) = valid[i]
            if (nextStart <= currentEnd) {
                if (nextEnd > currentEnd) {
                    currentEnd = nextEnd
                }
            } else {
                totalMs += (currentEnd - currentStart)
                currentStart = nextStart
                currentEnd = nextEnd
            }
        }
        totalMs += (currentEnd - currentStart)
        return totalMs
    }

    /**
     * Normalizes an interval's start/end pair using monotonic duration anchored at startTimestamp,
     * ensuring monotonic timing accuracy while enabling overlap deduplication across intervals.
     */
    private fun toEffectiveRange(interval: MonitoringInterval): Pair<Long, Long> {
        val duration = interval.monotonicDurationMs
        return Pair(interval.startTimestamp, interval.startTimestamp + duration)
    }

    /**
     * Computes the separated DailyMonitoringCoverage metrics for a given dateKey.
     * Groups intervals by bootSessionId to ensure monotonic clocks across boots are never compared or merged.
     */
    fun calculateDailyCoverage(
        dateKey: String,
        intervals: List<MonitoringInterval>,
        heartbeats: List<MonitoringHeartbeat>,
        detectionEventCount: Int,
        calculatedAt: Long = System.currentTimeMillis()
    ): DailyMonitoringCoverage {
        val dayIntervals = intervals.filter { it.dateKey == dateKey }.sortedBy { it.startTimestamp }
        val dayHeartbeats = heartbeats.filter { it.dateKey == dateKey }

        val intervalsByBoot = dayIntervals.groupBy { it.bootSessionId }
        val allServiceRanges = mutableListOf<Pair<Long, Long>>()
        val allTrackingRanges = mutableListOf<Pair<Long, Long>>()
        val allScreenOnRanges = mutableListOf<Pair<Long, Long>>()
        val allEligibleRanges = mutableListOf<Pair<Long, Long>>()

        for ((_, bootIntervals) in intervalsByBoot) {
            for (interval in bootIntervals) {
                if (interval.monotonicDurationMs <= 0L) continue
                val effectiveRange = toEffectiveRange(interval)

                if (interval.serviceConnected && interval.interruptionReason == null) {
                    allServiceRanges.add(effectiveRange)
                }
                if (interval.trackingEnabled && interval.interruptionReason == null) {
                    allTrackingRanges.add(effectiveRange)
                }
                if (interval.screenOn && interval.interruptionReason == null) {
                    allScreenOnRanges.add(effectiveRange)
                }
                if (interval.isEligibleForVerifiedCoverage) {
                    allEligibleRanges.add(effectiveRange)
                }
            }
        }

        val serviceConnectedMs = mergeNonOverlappingDurationMs(allServiceRanges)
        val trackingEnabledMs = mergeNonOverlappingDurationMs(allTrackingRanges)
        val screenOnMs = mergeNonOverlappingDurationMs(allScreenOnRanges)
        val estimatedVerifiedMonitoringMs = mergeNonOverlappingDurationMs(allEligibleRanges)

        // 5. Interruptions and recoveries
        var interruptionCount = 0
        var recoveryCount = 0
        var hadPriorEligible = false
        var inInterruptedState = false

        for (interval in dayIntervals) {
            if (interval.isEligibleForVerifiedCoverage && interval.monotonicDurationMs > 0L) {
                if (inInterruptedState && hadPriorEligible) {
                    recoveryCount++
                    inInterruptedState = false
                }
                hadPriorEligible = true
            } else if (
                interval.interruptionReason != null ||
                interval.continuityStatus == ContinuityStatus.INTERRUPTED ||
                interval.continuityStatus == ContinuityStatus.UNCERTAIN ||
                !interval.serviceConnected ||
                !interval.trackingEnabled ||
                !interval.screenOn
            ) {
                if (interval.interruptionReason != null || interval.continuityStatus != ContinuityStatus.VERIFIED) {
                    interruptionCount++
                }
                if (hadPriorEligible) {
                    inInterruptedState = true
                }
            }
        }

        val status = when {
            estimatedVerifiedMonitoringMs >= QUALIFICATION_THRESHOLD_MS ->
                EstimatedCoverageStatus.ESTIMATED_VERIFIED
            estimatedVerifiedMonitoringMs > 0L || serviceConnectedMs > 0L || dayHeartbeats.isNotEmpty() ->
                EstimatedCoverageStatus.ESTIMATED_INCOMPLETE
            else ->
                EstimatedCoverageStatus.UNVERIFIED
        }

        return DailyMonitoringCoverage(
            dateKey = dateKey,
            serviceConnectedMs = serviceConnectedMs,
            trackingEnabledMs = trackingEnabledMs,
            screenOnMs = screenOnMs,
            estimatedVerifiedMonitoringMs = estimatedVerifiedMonitoringMs,
            heartbeatCount = dayHeartbeats.size,
            detectionEventCount = detectionEventCount,
            interruptionCount = interruptionCount,
            recoveryCount = recoveryCount,
            coverageStatus = status,
            calculatedAt = calculatedAt
        )
    }

    /**
     * Splits a monitoring segment if its tail lacks continuity heartbeats beyond MAX_CONTINUITY_GAP_MS.
     * The confirmed prefix is marked VERIFIED and the unconfirmed tail is marked UNCERTAIN.
     * Evaluates heartbeats strictly within the matching bootSessionId.
     */
    fun splitByContinuityHeartbeats(
        dateKey: String,
        startTimestamp: Long,
        endTimestamp: Long,
        monotonicStartMs: Long,
        monotonicEndMs: Long,
        serviceConnected: Boolean,
        trackingEnabled: Boolean,
        screenOn: Boolean,
        heartbeatsInWindow: List<MonitoringHeartbeat>,
        maxConfirmedGapMs: Long = MAX_CONTINUITY_GAP_MS,
        bootSessionId: String = ""
    ): List<MonitoringInterval> {
        val totalDuration = (monotonicEndMs - monotonicStartMs).coerceAtLeast(0L)
        if (totalDuration <= maxConfirmedGapMs) {
            return listOf(
                MonitoringInterval(
                    dateKey = dateKey,
                    startTimestamp = startTimestamp,
                    endTimestamp = endTimestamp,
                    monotonicStartMs = monotonicStartMs,
                    monotonicEndMs = monotonicEndMs,
                    serviceConnected = serviceConnected,
                    trackingEnabled = trackingEnabled,
                    screenOn = screenOn,
                    continuityStatus = ContinuityStatus.VERIFIED,
                    bootSessionId = bootSessionId
                )
            )
        }

        val validHeartbeats = heartbeatsInWindow
            .filter {
                (bootSessionId.isEmpty() || it.bootSessionId.isEmpty() || it.bootSessionId == bootSessionId) &&
                    it.continuityStatus == ContinuityStatus.VERIFIED &&
                    it.serviceConnected &&
                    it.trackingEnabled &&
                    it.screenOn &&
                    it.monotonicMs in monotonicStartMs..monotonicEndMs
            }
            .sortedBy { it.monotonicMs }

        var lastConfirmedMonotonic = monotonicStartMs
        for (hb in validHeartbeats) {
            if (hb.monotonicMs - lastConfirmedMonotonic <= maxConfirmedGapMs) {
                lastConfirmedMonotonic = hb.monotonicMs
            } else {
                break
            }
        }

        // Check if end is within maxConfirmedGapMs of the last confirmed checkpoint
        if (monotonicEndMs - lastConfirmedMonotonic <= maxConfirmedGapMs) {
            return listOf(
                MonitoringInterval(
                    dateKey = dateKey,
                    startTimestamp = startTimestamp,
                    endTimestamp = endTimestamp,
                    monotonicStartMs = monotonicStartMs,
                    monotonicEndMs = monotonicEndMs,
                    serviceConnected = serviceConnected,
                    trackingEnabled = trackingEnabled,
                    screenOn = screenOn,
                    continuityStatus = ContinuityStatus.VERIFIED,
                    bootSessionId = bootSessionId
                )
            )
        }

        // Otherwise, confirm up to lastConfirmedMonotonic and mark the remaining tail as UNCERTAIN
        val confirmedDuration = (lastConfirmedMonotonic - monotonicStartMs).coerceAtLeast(0L)
        val splitWallTime = startTimestamp + confirmedDuration
        val result = mutableListOf<MonitoringInterval>()

        if (confirmedDuration > 0L) {
            result.add(
                MonitoringInterval(
                    dateKey = dateKey,
                    startTimestamp = startTimestamp,
                    endTimestamp = splitWallTime,
                    monotonicStartMs = monotonicStartMs,
                    monotonicEndMs = lastConfirmedMonotonic,
                    serviceConnected = serviceConnected,
                    trackingEnabled = trackingEnabled,
                    screenOn = screenOn,
                    continuityStatus = ContinuityStatus.VERIFIED,
                    bootSessionId = bootSessionId
                )
            )
        }

        result.add(
            MonitoringInterval(
                dateKey = dateKey,
                startTimestamp = splitWallTime,
                endTimestamp = endTimestamp,
                monotonicStartMs = lastConfirmedMonotonic,
                monotonicEndMs = monotonicEndMs,
                serviceConnected = serviceConnected,
                trackingEnabled = trackingEnabled,
                screenOn = screenOn,
                interruptionReason = "UNCERTAIN_CONTINUITY_GAP",
                continuityStatus = ContinuityStatus.UNCERTAIN,
                bootSessionId = bootSessionId
            )
        )

        return result
    }
}
