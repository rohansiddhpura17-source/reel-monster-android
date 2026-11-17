package com.reelmonster.app.service.tracking

import android.content.Context
import android.os.SystemClock
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.domain.model.monitoring.ContinuityStatus
import com.reelmonster.app.domain.model.monitoring.CoverageCalculator
import com.reelmonster.app.domain.model.monitoring.MonitoringHeartbeat
import com.reelmonster.app.domain.model.monitoring.MonitoringInterval
import com.reelmonster.app.domain.model.monitoring.SupportedAppDetectionEvent
import java.util.Date

/**
 * MonitoringCoverageTracker
 *
 * Tracks independent monitoring states (AccessibilityService connected, global tracking enabled,
 * screen-on state), continuity heartbeats, and supported-app detection activity.
 *
 * Fix 2 (Reboot-Safe Monitoring):
 * - Persists active monitoring intervals in SQLite rather than holding unclosed state in RAM.
 * - Records a reliable `boot_session_id` for intervals and heartbeats.
 * - Distinguishes between ordinary process restarts and actual device reboots.
 * - Closes previous unclosed intervals as "DEVICE_REBOOT" upon device reboot.
 * - Never compares monotonic clocks across different boot sessions.
 */
class MonitoringCoverageTracker(
    var clockElapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
    var clockWallTime: () -> Long = { System.currentTimeMillis() },
    var bootSessionProvider: () -> String = { BootSessionProvider.getBootSessionId() }
) {
    companion object {
        val instance: MonitoringCoverageTracker by lazy { MonitoringCoverageTracker() }
    }

    private var serviceConnected: Boolean = false
    private var trackingEnabled: Boolean = true
    private var screenOn: Boolean = true

    private var currentSegmentWallStartMs: Long = 0L
    private var currentSegmentMonoStartMs: Long = 0L
    private var currentSegmentDateKey: String? = null
    private var isSegmentActive: Boolean = false
    private var activeIntervalId: Long = 0L

    /**
     * Initializes the tracker on process startup.
     * Recovers or interrupts any unclosed intervals stored in SQLite from a prior process or boot session.
     */
    @Synchronized
    fun initialize(context: Context? = null, dbHelper: ReelDbHelper) {
        val currentBootId = bootSessionProvider()
        val nowMono = clockElapsedRealtime()
        val nowWall = clockWallTime()

        val unclosed = dbHelper.getUnclosedMonitoringIntervals()
        for (interval in unclosed) {
            if (interval.bootSessionId.isNotEmpty() && interval.bootSessionId != currentBootId) {
                // Device reboot detected: close interval as interrupted
                dbHelper.closeInterruptedMonitoringInterval(
                    intervalId = interval.id,
                    interruptionReason = "DEVICE_REBOOT",
                    continuityStatus = ContinuityStatus.INTERRUPTED,
                    endMonoMs = interval.monotonicEndMs,
                    endWallTs = interval.endTimestamp
                )
            } else {
                // Same-boot process restart: check monotonic gap
                val gapMono = (nowMono - interval.monotonicEndMs).coerceAtLeast(0L)
                if (gapMono <= CoverageCalculator.MAX_CONTINUITY_GAP_MS) {
                    dbHelper.closeInterruptedMonitoringInterval(
                        intervalId = interval.id,
                        interruptionReason = "PROCESS_RESTART_RECOVERED",
                        continuityStatus = ContinuityStatus.VERIFIED,
                        endMonoMs = interval.monotonicEndMs,
                        endWallTs = interval.endTimestamp
                    )
                } else {
                    dbHelper.closeInterruptedMonitoringInterval(
                        intervalId = interval.id,
                        interruptionReason = "PROCESS_INTERRUPTED",
                        continuityStatus = ContinuityStatus.INTERRUPTED,
                        endMonoMs = interval.monotonicEndMs,
                        endWallTs = interval.endTimestamp
                    )
                }
            }
        }

        activeIntervalId = 0L
        isSegmentActive = false

        // If monitoring conditions are currently met, start fresh interval
        if (serviceConnected && trackingEnabled && screenOn) {
            startNewActiveInterval(dbHelper, nowWall, nowMono, currentBootId)
        }
    }

    @Synchronized
    fun onStateChanged(
        dbHelper: ReelDbHelper,
        newServiceConnected: Boolean = this.serviceConnected,
        newTrackingEnabled: Boolean = this.trackingEnabled,
        newScreenOn: Boolean = this.screenOn,
        interruptionReason: String? = null
    ) {
        val nowWall = clockWallTime()
        val nowMono = clockElapsedRealtime()
        val currentBootId = bootSessionProvider()
        val willBeActive = newServiceConnected && newTrackingEnabled && newScreenOn && interruptionReason == null

        if (isSegmentActive) {
            finalizeActiveIntervalInternal(
                dbHelper = dbHelper,
                nowWall = nowWall,
                nowMono = nowMono,
                closingInterruptionReason = interruptionReason,
                bootSessionId = currentBootId
            )
        }

        this.serviceConnected = newServiceConnected
        this.trackingEnabled = newTrackingEnabled
        this.screenOn = newScreenOn

        if (willBeActive) {
            startNewActiveInterval(dbHelper, nowWall, nowMono, currentBootId)
        } else {
            isSegmentActive = false
            activeIntervalId = 0L

            if (interruptionReason != null) {
                // Record transition interruption marker
                val todayKey = DateTimeUtils.getTodayDateString(Date(nowWall))
                dbHelper.insertMonitoringInterval(
                    MonitoringInterval(
                        dateKey = todayKey,
                        startTimestamp = nowWall,
                        endTimestamp = nowWall,
                        monotonicStartMs = nowMono,
                        monotonicEndMs = nowMono,
                        serviceConnected = newServiceConnected,
                        trackingEnabled = newTrackingEnabled,
                        screenOn = newScreenOn,
                        interruptionReason = interruptionReason,
                        continuityStatus = ContinuityStatus.INTERRUPTED,
                        bootSessionId = currentBootId,
                        isActive = false,
                        estimatedVerifiedDurationMs = 0L
                    )
                )
            }
        }
    }

    private fun startNewActiveInterval(
        dbHelper: ReelDbHelper,
        startWall: Long,
        startMono: Long,
        bootSessionId: String
    ) {
        val dateKey = DateTimeUtils.getTodayDateString(Date(startWall))
        val interval = MonitoringInterval(
            dateKey = dateKey,
            startTimestamp = startWall,
            endTimestamp = startWall,
            monotonicStartMs = startMono,
            monotonicEndMs = startMono,
            serviceConnected = true,
            trackingEnabled = true,
            screenOn = true,
            interruptionReason = null,
            continuityStatus = ContinuityStatus.VERIFIED,
            bootSessionId = bootSessionId,
            isActive = true,
            estimatedVerifiedDurationMs = 0L
        )
        activeIntervalId = dbHelper.insertMonitoringInterval(interval)
        currentSegmentWallStartMs = startWall
        currentSegmentMonoStartMs = startMono
        currentSegmentDateKey = dateKey
        isSegmentActive = true
    }

    @Synchronized
    fun flushActiveInterval(dbHelper: ReelDbHelper) {
        if (!isSegmentActive) return
        val nowWall = clockWallTime()
        val nowMono = clockElapsedRealtime()
        val currentBootId = bootSessionProvider()
        finalizeActiveIntervalInternal(
            dbHelper = dbHelper,
            nowWall = nowWall,
            nowMono = nowMono,
            closingInterruptionReason = null,
            bootSessionId = currentBootId
        )
        startNewActiveInterval(dbHelper, nowWall, nowMono, currentBootId)
    }

    private fun finalizeActiveIntervalInternal(
        dbHelper: ReelDbHelper,
        nowWall: Long,
        nowMono: Long,
        closingInterruptionReason: String?,
        bootSessionId: String
    ) {
        val monoDelta = (nowMono - currentSegmentMonoStartMs).coerceAtLeast(0L)
        val dateKey = currentSegmentDateKey ?: DateTimeUtils.getTodayDateString(Date(currentSegmentWallStartMs))
        if (monoDelta <= 0L && closingInterruptionReason == null) {
            isSegmentActive = false
            activeIntervalId = 0L
            return
        }

        val heartbeats = dbHelper.getMonitoringHeartbeatsForDate(dateKey)
            .filter { it.bootSessionId.isEmpty() || it.bootSessionId == bootSessionId }

        val segments = if (closingInterruptionReason == null && serviceConnected && trackingEnabled && screenOn) {
            CoverageCalculator.splitByContinuityHeartbeats(
                dateKey = dateKey,
                startTimestamp = currentSegmentWallStartMs,
                endTimestamp = nowWall,
                monotonicStartMs = currentSegmentMonoStartMs,
                monotonicEndMs = nowMono,
                serviceConnected = serviceConnected,
                trackingEnabled = trackingEnabled,
                screenOn = screenOn,
                heartbeatsInWindow = heartbeats,
                bootSessionId = bootSessionId
            )
        } else {
            val status = if (closingInterruptionReason == null) ContinuityStatus.VERIFIED else ContinuityStatus.INTERRUPTED
            listOf(
                MonitoringInterval(
                    id = activeIntervalId,
                    dateKey = dateKey,
                    startTimestamp = currentSegmentWallStartMs,
                    endTimestamp = nowWall,
                    monotonicStartMs = currentSegmentMonoStartMs,
                    monotonicEndMs = nowMono,
                    serviceConnected = serviceConnected,
                    trackingEnabled = trackingEnabled,
                    screenOn = screenOn,
                    interruptionReason = closingInterruptionReason,
                    continuityStatus = status,
                    bootSessionId = bootSessionId,
                    isActive = false
                )
            )
        }

        if (segments.isNotEmpty()) {
            val firstSeg = segments.first().copy(id = activeIntervalId, isActive = false)
            if (activeIntervalId > 0L) {
                dbHelper.updateMonitoringInterval(firstSeg)
            } else {
                dbHelper.insertMonitoringInterval(firstSeg)
            }

            for (i in 1 until segments.size) {
                val seg = segments[i].copy(isActive = false)
                if (seg.monotonicDurationMs > 0L) {
                    dbHelper.insertMonitoringInterval(seg)
                }
            }
        }

        isSegmentActive = false
        activeIntervalId = 0L
    }

    @Synchronized
    fun recordHeartbeat(
        dbHelper: ReelDbHelper,
        continuityStatus: ContinuityStatus = ContinuityStatus.VERIFIED
    ): Long {
        val nowWall = clockWallTime()
        val nowMono = clockElapsedRealtime()
        val currentBootId = bootSessionProvider()
        val dateKey = DateTimeUtils.getTodayDateString(Date(nowWall))

        if (isSegmentActive && activeIntervalId > 0L) {
            dbHelper.updateActiveMonitoringIntervalProgress(activeIntervalId, nowMono, nowWall)
        }

        return dbHelper.insertMonitoringHeartbeat(
            MonitoringHeartbeat(
                timestamp = nowWall,
                monotonicMs = nowMono,
                dateKey = dateKey,
                serviceConnected = serviceConnected,
                trackingEnabled = trackingEnabled,
                screenOn = screenOn,
                continuityStatus = continuityStatus,
                bootSessionId = currentBootId
            )
        )
    }

    @Synchronized
    fun recordSupportedAppDetection(
        dbHelper: ReelDbHelper,
        platform: String,
        eventType: String,
        detectionStatus: String = "CONFIRMED",
        contentFingerprint: String? = null
    ): Long {
        val nowWall = clockWallTime()
        val dateKey = DateTimeUtils.getTodayDateString(Date(nowWall))
        return dbHelper.insertSupportedAppDetectionEvent(
            SupportedAppDetectionEvent(
                timestamp = nowWall,
                dateKey = dateKey,
                platform = platform,
                eventType = eventType,
                detectionStatus = detectionStatus,
                contentFingerprint = contentFingerprint
            )
        )
    }

    @Synchronized
    fun reset() {
        serviceConnected = false
        trackingEnabled = true
        screenOn = true
        currentSegmentWallStartMs = 0L
        currentSegmentMonoStartMs = 0L
        currentSegmentDateKey = null
        isSegmentActive = false
        activeIntervalId = 0L
        clockElapsedRealtime = { SystemClock.elapsedRealtime() }
        clockWallTime = { System.currentTimeMillis() }
        bootSessionProvider = { BootSessionProvider.getBootSessionId() }
    }
}
