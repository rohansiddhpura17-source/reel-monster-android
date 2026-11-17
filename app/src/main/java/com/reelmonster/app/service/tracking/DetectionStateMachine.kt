package com.reelmonster.app.service.tracking

import com.reelmonster.app.domain.model.DetectionCandidate
import com.reelmonster.app.domain.model.DetectionState
import com.reelmonster.app.domain.model.TargetApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DetectionStateMachine
 *
 * Coordinates state transitions for Level 2 Automatic Tracking.
 * Ensures thread-safe tracking of foreground application, viewer presence,
 * transition lifecycle, and pause states.
 */
class DetectionStateMachine {

    private val _currentState = MutableStateFlow(DetectionState.IDLE)
    val currentState: StateFlow<DetectionState> = _currentState.asStateFlow()

    private val _currentApp = MutableStateFlow<TargetApp?>(null)
    val currentApp: StateFlow<TargetApp?> = _currentApp.asStateFlow()

    private var isPaused: Boolean = false

    @Synchronized
    fun onTargetAppForeground(app: TargetApp) {
        if (isPaused) {
            _currentApp.value = app
            _currentState.value = DetectionState.PAUSED
            return
        }
        val appChanged = _currentApp.value != app
        _currentApp.value = app

        // If app changed or was idle, transition to TARGET_APP_ACTIVE
        if (appChanged || _currentState.value == DetectionState.IDLE) {
            _currentState.value = DetectionState.TARGET_APP_ACTIVE
        }
    }

    @Synchronized
    fun onViewerEntered(app: TargetApp) {
        if (isPaused) {
            _currentApp.value = app
            _currentState.value = DetectionState.PAUSED
            return
        }
        _currentApp.value = app
        _currentState.value = DetectionState.REEL_VIEWER_ACTIVE
    }

    @Synchronized
    fun onScrollTransitionStarted() {
        if (isPaused) return
        if (_currentState.value == DetectionState.REEL_VIEWER_ACTIVE ||
            _currentState.value == DetectionState.NEW_CONTENT_CONFIRMED) {
            _currentState.value = DetectionState.CONTENT_TRANSITION_PENDING
        }
    }

    @Synchronized
    fun onContentConfirmed(candidate: DetectionCandidate) {
        if (isPaused) return
        _currentApp.value = candidate.targetApp
        _currentState.value = DetectionState.NEW_CONTENT_CONFIRMED
    }

    @Synchronized
    fun onContentStabilized() {
        if (isPaused) return
        if (_currentState.value == DetectionState.NEW_CONTENT_CONFIRMED ||
            _currentState.value == DetectionState.CONTENT_TRANSITION_PENDING) {
            _currentState.value = DetectionState.REEL_VIEWER_ACTIVE
        }
    }

    @Synchronized
    fun onViewerExited() {
        if (isPaused) {
            _currentState.value = DetectionState.PAUSED
            return
        }
        if (_currentApp.value != null) {
            _currentState.value = DetectionState.TARGET_APP_ACTIVE
        } else {
            _currentState.value = DetectionState.IDLE
        }
    }

    @Synchronized
    fun onAppBackgrounded() {
        _currentApp.value = null
        _currentState.value = if (isPaused) DetectionState.PAUSED else DetectionState.IDLE
    }

    @Synchronized
    fun onPauseStateChanged(paused: Boolean) {
        isPaused = paused
        if (paused) {
            _currentState.value = DetectionState.PAUSED
        } else {
            _currentState.value = if (_currentApp.value != null) {
                DetectionState.TARGET_APP_ACTIVE
            } else {
                DetectionState.IDLE
            }
        }
    }

    @Synchronized
    fun onUnsupportedLayout() {
        if (!isPaused) {
            _currentState.value = DetectionState.UNSUPPORTED_UI
        }
    }

    @Synchronized
    fun reset() {
        _currentApp.value = null
        _currentState.value = if (isPaused) DetectionState.PAUSED else DetectionState.IDLE
    }
}
