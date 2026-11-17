package com.reelmonster.app.service.focus

import android.os.SystemClock
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.focus.FocusInterventionReminder
import com.reelmonster.app.domain.model.focus.FocusPeriodStats
import com.reelmonster.app.domain.model.focus.FocusSession
import com.reelmonster.app.domain.model.focus.FocusSessionProgress
import com.reelmonster.app.domain.model.focus.FocusStatus
import com.reelmonster.app.domain.model.focus.FocusType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * FocusSessionTracker
 *
 * Dedicated monotonic session tracker for Phase 13: Focus Mode & Gentle Interventions.
 *
 * Key Guarantees:
 * 1. Strictly separate from short-form video viewing time (zero bleed into viewing_sessions).
 * 2. Uses SystemClock.elapsedRealtime() for monotonic elapsed time measurement immune to wall-clock changes.
 * 3. Supports start, pause, resume, complete, and cancel lifecycle controls.
 * 4. Safe session persistence and state restoration across app restarts and reboots.
 * 5. Gentle, non-blocking reminders with configurable cooldown periods when entering video feeds.
 * 6. Zero continuous rendering / battery consumption when idle.
 */
class FocusSessionTracker(
    var clockElapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    var clockWallTime: () -> Long = System::currentTimeMillis
) {
    companion object {
        val instance = FocusSessionTracker()
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickerJob: Job? = null

    private val _progress = MutableStateFlow(FocusSessionProgress.idle())
    val progress: StateFlow<FocusSessionProgress> = _progress.asStateFlow()

    private val _interventionReminder = MutableStateFlow<FocusInterventionReminder?>(null)
    val interventionReminder: StateFlow<FocusInterventionReminder?> = _interventionReminder.asStateFlow()

    private var activeStartMonoTime: Long = 0L
    private var activeStartWallTime: Long = 0L
    private var activeTargetDurationMs: Long = 25 * 60_000L
    private var accumulatedPausedMs: Long = 0L
    private var pauseStartMonoTime: Long = 0L
    private var activeType: FocusType = FocusType.DEEP_FOCUS

    var lastInterventionWallTime: Long = 0L

    val isRunning: Boolean get() = _progress.value.status == FocusStatus.RUNNING
    val isPaused: Boolean get() = _progress.value.status == FocusStatus.PAUSED
    val isActive: Boolean get() = _progress.value.status.isActive

    /**
     * Start a new focus session.
     */
    fun start(
        type: FocusType,
        durationMinutes: Int,
        repository: ReelRepository? = null
    ) {
        stopTicker()

        activeType = type
        activeTargetDurationMs = durationMinutes.coerceAtLeast(1) * 60_000L
        activeStartMonoTime = clockElapsedRealtime()
        activeStartWallTime = clockWallTime()
        accumulatedPausedMs = 0L
        pauseStartMonoTime = 0L
        lastInterventionWallTime = 0L

        _progress.value = FocusSessionProgress(
            status = FocusStatus.RUNNING,
            type = activeType,
            targetDurationMs = activeTargetDurationMs,
            elapsedMs = 0L,
            startWallTime = activeStartWallTime
        )

        repository?.personalityEngine?.onFocusStarted(type)
        persistActiveState(repository)
        startTicker(repository)
    }

    /**
     * Pause the running focus session.
     */
    fun pause(repository: ReelRepository? = null) {
        if (_progress.value.status != FocusStatus.RUNNING) return

        stopTicker()
        pauseStartMonoTime = clockElapsedRealtime()
        val currentElapsed = computeElapsedMs()

        _progress.value = _progress.value.copy(
            status = FocusStatus.PAUSED,
            elapsedMs = currentElapsed
        )

        repository?.personalityEngine?.onFocusPaused()
        persistActiveState(repository)
    }

    /**
     * Resume a paused focus session.
     */
    fun resume(repository: ReelRepository? = null) {
        if (_progress.value.status != FocusStatus.PAUSED) return

        val nowMono = clockElapsedRealtime()
        if (pauseStartMonoTime > 0L) {
            accumulatedPausedMs += (nowMono - pauseStartMonoTime).coerceAtLeast(0L)
            pauseStartMonoTime = 0L
        }

        _progress.value = _progress.value.copy(
            status = FocusStatus.RUNNING,
            elapsedMs = computeElapsedMs()
        )

        repository?.personalityEngine?.onFocusResumed()
        persistActiveState(repository)
        startTicker(repository)
    }

    /**
     * Complete and log the focus session to the database.
     */
    fun complete(repository: ReelRepository? = null, status: String = FocusSession.STATUS_COMPLETED) {
        stopTicker()
        val actualElapsed = computeElapsedMs()
        val finalElapsed = actualElapsed.coerceAtMost(activeTargetDurationMs)

        val endWall = clockWallTime()
        val dateStr = DateTimeUtils.getTodayDateString()

        val session = FocusSession(
            sessionType = activeType,
            targetDurationMs = activeTargetDurationMs,
            actualDurationMs = finalElapsed,
            startTimestamp = activeStartWallTime,
            endTimestamp = endWall,
            completionStatus = status,
            dateString = dateStr
        )

        _progress.value = _progress.value.copy(
            status = FocusStatus.COMPLETED,
            elapsedMs = finalElapsed
        )

        if (repository != null) {
            repository.insertFocusSession(session)
            repository.refreshFocusStats()
            if (status == FocusSession.STATUS_COMPLETED) {
                repository.personalityEngine.onFocusCompleted(finalElapsed)
            }
            clearActiveState(repository)
        }
    }

    /**
     * Cancel an active session. If it was active for at least 10 seconds,
     * it is recorded as CANCELLED in history for accuracy.
     */
    fun cancel(repository: ReelRepository? = null) {
        stopTicker()
        val elapsed = computeElapsedMs()
        val endWall = clockWallTime()

        if (elapsed >= 10_000L && repository != null) {
            val session = FocusSession(
                sessionType = activeType,
                targetDurationMs = activeTargetDurationMs,
                actualDurationMs = elapsed,
                startTimestamp = activeStartWallTime,
                endTimestamp = endWall,
                completionStatus = FocusSession.STATUS_CANCELLED,
                dateString = DateTimeUtils.getTodayDateString()
            )
            repository.insertFocusSession(session)
            repository.refreshFocusStats()
        }

        _progress.value = FocusSessionProgress(
            status = FocusStatus.CANCELLED,
            type = activeType,
            targetDurationMs = activeTargetDurationMs,
            elapsedMs = elapsed,
            startWallTime = activeStartWallTime
        )

        if (repository != null) {
            clearActiveState(repository)
        }
    }

    /**
     * Compute current elapsed duration accurately using the monotonic clock.
     */
    fun computeElapsedMs(): Long {
        return when (_progress.value.status) {
            FocusStatus.RUNNING -> {
                val nowMono = clockElapsedRealtime()
                (nowMono - activeStartMonoTime - accumulatedPausedMs).coerceAtLeast(0L)
            }
            FocusStatus.PAUSED -> {
                if (pauseStartMonoTime > 0L) {
                    (pauseStartMonoTime - activeStartMonoTime - accumulatedPausedMs).coerceAtLeast(0L)
                } else {
                    _progress.value.elapsedMs
                }
            }
            else -> _progress.value.elapsedMs
        }
    }

    fun tick() {
        if (_progress.value.status == FocusStatus.RUNNING) {
            val elapsed = computeElapsedMs()
            _progress.value = _progress.value.copy(elapsedMs = elapsed)
        }
    }

    /**
     * Gentle intervention check when entering a video app.
     * Respects the configurable cooldown period so users are never spammed.
     */
    fun checkIntervention(
        targetApp: TargetApp,
        cooldownMins: Int = 3,
        repository: ReelRepository? = null
    ): FocusInterventionReminder? {
        if (_progress.value.status != FocusStatus.RUNNING) return null

        val nowWall = clockWallTime()
        val cooldownMs = cooldownMins.coerceAtLeast(1) * 60_000L

        if (nowWall - lastInterventionWallTime < cooldownMs) {
            return null
        }

        lastInterventionWallTime = nowWall
        val rem = _progress.value.formattedRemaining
        val reminder = FocusInterventionReminder(
            title = "Mindful Focus Check-in",
            message = "You're currently in a ${activeType.label} ($rem left). Munchie suggests staying in your flow! 🎯",
            remainingFormatted = rem,
            targetApp = targetApp,
            timestamp = nowWall,
            targetDurationMs = activeTargetDurationMs
        )

        _interventionReminder.value = reminder
        repository?.personalityEngine?.onFocusIntervention()
        return reminder
    }

    fun checkIntervention(
        packageName: String,
        cooldownMins: Int = 3,
        repository: ReelRepository? = null
    ): FocusInterventionReminder? {
        val app = TargetApp.fromPackageName(packageName) ?: return null
        return checkIntervention(app, cooldownMins, repository)
    }

    fun checkIntervention(
        packageName: String,
        repository: ReelRepository
    ): FocusInterventionReminder? {
        val app = TargetApp.fromPackageName(packageName) ?: return null
        val cooldown = repository.preferencesManager.focusReminderCooldownMins.value
        return checkIntervention(app, cooldown, repository)
    }

    fun dismissIntervention() {
        _interventionReminder.value = null
    }

    private fun startTicker(repository: ReelRepository?) {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && _progress.value.status == FocusStatus.RUNNING) {
                delay(1000L)
                val elapsed = computeElapsedMs()
                if (elapsed >= activeTargetDurationMs) {
                    complete(repository, FocusSession.STATUS_COMPLETED)
                    break
                } else {
                    _progress.value = _progress.value.copy(elapsedMs = elapsed)
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun persistActiveState(repository: ReelRepository?) {
        if (repository == null) return
        val currentElapsed = computeElapsedMs()
        scope.launch {
            repository.preferencesManager.saveActiveFocusSessionState(
                type = activeType.name,
                targetMs = activeTargetDurationMs,
                elapsedMs = currentElapsed,
                status = _progress.value.status.name,
                startWallTime = activeStartWallTime
            )
        }
    }

    private fun clearActiveState(repository: ReelRepository?) {
        if (repository == null) return
        scope.launch {
            repository.preferencesManager.clearActiveFocusSessionState()
        }
    }

    /**
     * Restore saved focus state across application restart / device reboot.
     */
    fun restoreActiveSessionState(repository: ReelRepository): Boolean {
        val savedTypeStr = repository.preferencesManager.savedFocusType.value
        val targetMs = repository.preferencesManager.savedFocusTargetMs.value
        val startWall = repository.preferencesManager.savedFocusStartWallTime.value
        val statusStr = repository.preferencesManager.savedFocusStatus.value

        if (savedTypeStr == null || targetMs <= 0L || startWall <= 0L) return false

        val type = FocusType.fromName(savedTypeStr)
        val nowWall = clockWallTime()
        val elapsedWall = (nowWall - startWall).coerceAtLeast(0L)

        if (elapsedWall >= targetMs) {
            // Expired while process was dead: complete it
            val session = FocusSession(
                sessionType = type,
                targetDurationMs = targetMs,
                actualDurationMs = targetMs,
                startTimestamp = startWall,
                endTimestamp = startWall + targetMs,
                completionStatus = FocusSession.STATUS_COMPLETED,
                dateString = DateTimeUtils.getTodayDateString()
            )
            repository.insertFocusSession(session)
            repository.refreshFocusStats()
            clearActiveState(repository)
            _progress.value = FocusSessionProgress(
                status = FocusStatus.COMPLETED,
                type = type,
                targetDurationMs = targetMs,
                elapsedMs = targetMs,
                startWallTime = startWall
            )
            return true
        }

        // Still active: restore
        activeType = type
        activeTargetDurationMs = targetMs
        activeStartWallTime = startWall
        activeStartMonoTime = clockElapsedRealtime() - elapsedWall
        accumulatedPausedMs = 0L

        val status = if (statusStr == FocusStatus.PAUSED.name) FocusStatus.PAUSED else FocusStatus.RUNNING
        _progress.value = FocusSessionProgress(
            status = status,
            type = type,
            targetDurationMs = targetMs,
            elapsedMs = elapsedWall,
            startWallTime = startWall
        )
        if (status == FocusStatus.RUNNING) {
            startTicker(repository)
        }
        return true
    }

    /**
     * Reset tracker for testing or app restart.
     */
    fun reset() {
        stopTicker()
        activeStartMonoTime = 0L
        activeStartWallTime = 0L
        accumulatedPausedMs = 0L
        pauseStartMonoTime = 0L
        lastInterventionWallTime = 0L
        _progress.value = FocusSessionProgress.idle()
        _interventionReminder.value = null
        clockElapsedRealtime = { SystemClock.elapsedRealtime() }
        clockWallTime = { System.currentTimeMillis() }
    }
}
