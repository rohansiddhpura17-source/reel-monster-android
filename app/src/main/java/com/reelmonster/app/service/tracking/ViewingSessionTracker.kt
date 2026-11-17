package com.reelmonster.app.service.tracking

import android.os.SystemClock
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.DetectionCandidate
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.ViewingSession
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
 * ViewingSessionTracker
 *
 * Dedicated viewing-session tracker for Level 2 Automatic Tracking.
 * Replaces the static 1.5-minute estimation with actual on-screen viewing durations.
 *
 * Guarantees:
 * 1. Uses SystemClock.elapsedRealtime() (monotonic clock) so duration measurement is immune
 *    to wall-clock drift, NTP sync adjustments, and timezone shifts.
 * 2. Starts a session when a confirmed Reel or Short is detected.
 * 3. Closes and persists the session when swiping to a different Reel or Short.
 * 4. Never counts screen-off time or time spent in other apps.
 * 5. Safely terminates active sessions on screen-off, app switch, pause, global tracking OFF,
 *    and daily reset.
 * 6. Zero continuous animation or rendering while idle: timer coroutines only run while
 *    a viewing session is active.
 */
class ViewingSessionTracker(
    var clockElapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    var clockWallTime: () -> Long = System::currentTimeMillis
) {
    companion object {
        val instance = ViewingSessionTracker()

        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_INTERRUPTED_APP_SWITCH = "INTERRUPTED_APP_SWITCH"
        const val STATUS_INTERRUPTED_SCREEN_OFF = "INTERRUPTED_SCREEN_OFF"
        const val STATUS_INTERRUPTED_PAUSED = "INTERRUPTED_PAUSED"
        const val STATUS_INTERRUPTED_GLOBAL_OFF = "INTERRUPTED_GLOBAL_OFF"
        const val STATUS_INTERRUPTED_RESET = "INTERRUPTED_RESET"
        const val STATUS_INTERRUPTED_VIEWER_EXIT = "INTERRUPTED_VIEWER_EXIT"

        /** Minimum duration (100ms) required to persist a session to avoid sub-frame noise */
        const val MIN_RECORDABLE_DURATION_MS = 100L
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickerJob: Job? = null

    private var activeCandidate: DetectionCandidate? = null
    private var startMonotonicTime: Long = 0L
    private var startWallTime: Long = 0L
    private var isSessionActive: Boolean = false

    private val _liveElapsedTimerMs = MutableStateFlow(0L)
    val liveElapsedTimerMs: StateFlow<Long> = _liveElapsedTimerMs.asStateFlow()

    private val _isSessionActiveFlow = MutableStateFlow(false)
    val isSessionActiveFlow: StateFlow<Boolean> = _isSessionActiveFlow.asStateFlow()

    val currentCandidate: DetectionCandidate? get() = activeCandidate
    val isActive: Boolean get() = isSessionActive

    /**
     * Called when a genuine new Reel or Short is confirmed by the deduplication engine.
     * Closes the previous session (if any) and starts a new session for [candidate].
     */
    @Synchronized
    fun onReelConfirmed(
        candidate: DetectionCandidate,
        repo: ReelRepository
    ) {
        val current = activeCandidate

        // If candidate is identical to the current active session, continue without restart
        if (isSessionActive && current != null &&
            current.targetApp == candidate.targetApp &&
            current.fingerprint == candidate.fingerprint
        ) {
            return
        }

        // Close previous session if running
        if (isSessionActive && current != null) {
            endActiveSessionInternal(repo, STATUS_COMPLETED)
        }

        // Start new session
        val nowMono = clockElapsedRealtime()
        val nowWall = clockWallTime()

        activeCandidate = candidate
        startMonotonicTime = nowMono
        startWallTime = nowWall
        isSessionActive = true
        _isSessionActiveFlow.value = true
        _liveElapsedTimerMs.value = 0L

        startTicker()
    }

    /**
     * Ends the currently active session with [status] and persists it to [repo].
     */
    @Synchronized
    fun endActiveSession(
        repo: ReelRepository,
        status: String = STATUS_COMPLETED
    ): ViewingSession? {
        return endActiveSessionInternal(repo, status)
    }

    private fun endActiveSessionInternal(
        repo: ReelRepository,
        status: String
    ): ViewingSession? {
        if (!isSessionActive || activeCandidate == null || startMonotonicTime == 0L) {
            resetInternal()
            return null
        }

        val candidate = activeCandidate!!
        val endMono = clockElapsedRealtime()
        val endWall = clockWallTime()
        val durationMs = (endMono - startMonotonicTime).coerceAtLeast(0L)

        val dateStr = DateTimeUtils.formatDate(startWallTime)
        val session = ViewingSession(
            targetApp = candidate.targetApp,
            fingerprint = candidate.fingerprint,
            startTimestamp = startWallTime,
            endTimestamp = endWall,
            durationMs = durationMs,
            completionStatus = status,
            sessionDate = dateStr
        )

        // Only persist non-trivial viewing durations
        if (durationMs >= MIN_RECORDABLE_DURATION_MS) {
            try {
                repo.recordViewingSession(session)
            } catch (_: Exception) {}
        }

        resetInternal()
        return session
    }

    /**
     * Handles target app or Reel viewer exit.
     */
    @Synchronized
    fun onViewerExited(targetApp: TargetApp, repo: ReelRepository) {
        if (isSessionActive && activeCandidate?.targetApp == targetApp) {
            endActiveSession(repo, STATUS_INTERRUPTED_VIEWER_EXIT)
        }
    }

    /**
     * Handles switching away from Instagram/YouTube to another app or home screen.
     */
    @Synchronized
    fun onAppBackgrounded(repo: ReelRepository) {
        if (isSessionActive) {
            endActiveSession(repo, STATUS_INTERRUPTED_APP_SWITCH)
        }
    }

    /**
     * Handles screen turning off. Never counts screen-off duration as Reel viewing time.
     */
    @Synchronized
    fun onScreenOff(repo: ReelRepository) {
        if (isSessionActive) {
            endActiveSession(repo, STATUS_INTERRUPTED_SCREEN_OFF)
        }
    }

    /**
     * Handles tracking paused by user.
     */
    @Synchronized
    fun onTrackingPaused(repo: ReelRepository) {
        if (isSessionActive) {
            endActiveSession(repo, STATUS_INTERRUPTED_PAUSED)
        }
    }

    /**
     * Handles global tracking switch toggled to OFF.
     */
    @Synchronized
    fun onGlobalTrackingDisabled(repo: ReelRepository) {
        if (isSessionActive) {
            endActiveSession(repo, STATUS_INTERRUPTED_GLOBAL_OFF)
        }
    }

    /**
     * Returns elapsed milliseconds for the current active Reel session.
     */
    @Synchronized
    fun currentSessionElapsedMs(): Long {
        if (!isSessionActive || startMonotonicTime == 0L) return 0L
        return (clockElapsedRealtime() - startMonotonicTime).coerceAtLeast(0L)
    }

    @Synchronized
    fun reset(clearHistory: Boolean = false) {
        resetInternal()
    }

    private fun resetInternal() {
        stopTicker()
        activeCandidate = null
        startMonotonicTime = 0L
        startWallTime = 0L
        isSessionActive = false
        _isSessionActiveFlow.value = false
        _liveElapsedTimerMs.value = 0L
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && isSessionActive) {
                _liveElapsedTimerMs.value = currentSessionElapsedMs()
                delay(500L)
            }
            _liveElapsedTimerMs.value = 0L
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }
}
