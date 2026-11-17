package com.reelmonster.app.service.tracking

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.reelmonster.app.ReelMonsterApp
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.DetectionCandidate
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import com.reelmonster.app.service.focus.FocusSessionTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ReelAccessibilityService
 *
 * Dedicated Android AccessibilityService for Level 2 Automatic Reel and Shorts detection.
 *
 * Guarantees:
 * 1. 100% Offline: Zero internet permission, zero network activity.
 * 2. Strict Package Scoping: Only processes Instagram (com.instagram.android) and YouTube (com.google.android.youtube).
 * 3. Privacy Preservation: Never inspects keystrokes, personal messages, or passwords.
 * 4. Multi-Stage Deduplication: Dwell floor, progressive metadata merging, bounce-back prevention,
 *    and child overlay (comments/share) suppression.
 */
class ReelAccessibilityService : AccessibilityService() {

    companion object {
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        val stateMachine = DetectionStateMachine()
        val dedupEngine = DeduplicationEngine()

        val instagramDetector = InstagramReelDetector()
        val youtubeDetector = YouTubeShortsDetector()

        /**
         * Checks whether the user has enabled ReelAccessibilityService in Android Settings.
         * Supports both flattened full component strings and shortened relative component strings.
         */
        fun isAccessibilityPermissionGranted(context: Context): Boolean {
            if (_isServiceRunning.value) return true
            val enabledServices = try {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            } catch (_: Exception) {
                null
            } ?: return false

            return isComponentInEnabledServices(
                enabledServices = enabledServices,
                packageName = context.packageName,
                className = ReelAccessibilityService::class.java.name
            )
        }

        internal fun isComponentInEnabledServices(
            enabledServices: String?,
            packageName: String,
            className: String
        ): Boolean {
            if (enabledServices.isNullOrBlank()) return false
            val expectedComponent = ComponentName(packageName, className)
            val expectedFull = "$packageName/$className"
            val relativeSuffix = className.removePrefix(packageName)
            val expectedShort = "$packageName/$relativeSuffix"

            return enabledServices.split(":").any { rawEntry ->
                val entry = rawEntry.trim()
                if (entry.isEmpty()) return@any false
                val parsed = ComponentName.unflattenFromString(entry)
                if (parsed != null && parsed == expectedComponent) {
                    true
                } else {
                    entry.equals(expectedFull, ignoreCase = true) ||
                        entry.equals(expectedShort, ignoreCase = true)
                }
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var dwellJob: Job? = null
    private var heartbeatJob: Job? = null
    private var consecutiveNonViewerFrames: Int = 0

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val repo = try { ReelMonsterApp.getOrCreateRepository(this@ReelAccessibilityService) } catch (_: Exception) { null }
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    dwellJob?.cancel()
                    stateMachine.onAppBackgrounded()
                    dedupEngine.reset(clearRecentHistory = false)
                    if (repo != null) {
                        ViewingSessionTracker.instance.onScreenOff(repo)
                        repo.onScreenStateChanged(false)
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    repo?.onScreenStateChanged(true)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isServiceRunning.value = true

        // When the user enables ReelAccessibilityService in Android Settings,
        // automatically activate Level 2 Automatic mode so scrolling works immediately.
        try {
            val repo = ReelMonsterApp.getOrCreateRepository(this)
            repo.monitoringCoverageTracker.initialize(this, repo.dbHelper)
            repo.preferencesManager.setTrackingMode(TrackingMode.LEVEL_2_AUTOMATIC)
            repo.onAccessibilityServiceConnectionChanged(true)
        } catch (_: Exception) {}

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        try {
            ContextCompat.registerReceiver(
                this,
                screenOffReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {}

        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (_isServiceRunning.value) {
                delay(900_000L) // 15-minute continuity heartbeat
                try {
                    val repo = ReelMonsterApp.getOrCreateRepository(this@ReelAccessibilityService)
                    repo.recordMonitoringHeartbeat()
                } catch (_: Exception) {}
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val repo = try {
            ReelMonsterApp.getOrCreateRepository(this)
        } catch (_: Exception) {
            null
        } ?: return
        val prefs = repo.preferencesManager

        // 0. Check Global Tracking ON/OFF switch
        if (!prefs.isGlobalTrackingEnabled.value) {
            ViewingSessionTracker.instance.onGlobalTrackingDisabled(repo)
            return
        }

        // 1. Only track if Level 2 Automatic mode is selected
        if (prefs.trackingMode.value != TrackingMode.LEVEL_2_AUTOMATIC) {
            return
        }

        val pkg = event.packageName?.toString() ?: return
        val eventTypeName = eventTypeToString(event.eventType)
        DetectorLogger.logEventReceived(pkg, eventTypeName)

        // 2. Respect pause setting
        if (prefs.isTrackingPaused.value) {
            stateMachine.onPauseStateChanged(true)
            dwellJob?.cancel()
            ViewingSessionTracker.instance.onTrackingPaused(repo)
            val target = TargetApp.fromPackage(pkg)
            if (target != null) {
                DetectorLogger.logTransitionRejected(target, "Tracking is paused by user")
            }
            return
        } else {
            stateMachine.onPauseStateChanged(false)
        }

        val targetApp = TargetApp.fromPackage(pkg)

        if (targetApp == null) {
            // Ignore events from our own overlay or Android SystemUI
            if (pkg.startsWith("com.reelmonster.app") || pkg == "com.android.systemui") {
                return
            }
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                dwellJob?.cancel()
                stateMachine.onAppBackgrounded()
                dedupEngine.reset(clearRecentHistory = false)
                ViewingSessionTracker.instance.onAppBackgrounded(repo)
            }
            return
        }

        // 3. Respect per-app monitoring toggles
        if (targetApp == TargetApp.INSTAGRAM && !prefs.monitorInstagram.value) {
            DetectorLogger.logTransitionRejected(targetApp, "Instagram monitoring toggle disabled")
            return
        }
        if (targetApp == TargetApp.YOUTUBE && !prefs.monitorYouTube.value) {
            DetectorLogger.logTransitionRejected(targetApp, "YouTube monitoring toggle disabled")
            return
        }

        DetectorLogger.logTargetAppDetected(targetApp)

        // If user switched between Instagram and YouTube, reset active candidate
        val currentActiveApp = stateMachine.currentApp.value
        if (currentActiveApp != null && currentActiveApp != targetApp) {
            dwellJob?.cancel()
            dedupEngine.reset(clearRecentHistory = false)
            ViewingSessionTracker.instance.onAppBackgrounded(repo)
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                stateMachine.onTargetAppForeground(targetApp)
                FocusSessionTracker.instance.checkIntervention(
                    targetApp = targetApp,
                    cooldownMins = prefs.focusReminderCooldownMins.value,
                    repository = repo
                )
                inspectHierarchy(targetApp, repo, event)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                stateMachine.onScrollTransitionStarted()
                inspectHierarchy(targetApp, repo, event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                inspectHierarchy(targetApp, repo, event)
            }
        }
    }

    /**
     * Resolves the root AccessibilityNodeInfo for the target application, even when
     * FloatingMonsterOverlayService or system bars are layered above it.
     */
    private fun resolveTargetRootNode(
        targetApp: TargetApp,
        event: AccessibilityEvent
    ): AccessibilityNodeInfo? {
        val activeRoot = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        }
        if (activeRoot != null && activeRoot.packageName?.toString() == targetApp.packageName) {
            return activeRoot
        }

        // Fallback 1: Inspect interactive windows (enabled by flagRetrieveInteractiveWindows)
        val windowList = try {
            windows
        } catch (_: Exception) {
            null
        }
        if (windowList != null) {
            for (window in windowList) {
                val root = try {
                    window.root
                } catch (_: Exception) {
                    null
                }
                if (root != null && root.packageName?.toString() == targetApp.packageName) {
                    return root
                }
            }
        }

        // Fallback 2: Walk up from event.source
        var sourceNode = try {
            event.source
        } catch (_: Exception) {
            null
        }
        if (sourceNode != null && sourceNode.packageName?.toString() == targetApp.packageName) {
            var guard = 0
            while (sourceNode?.parent != null && guard < 50) {
                sourceNode = sourceNode.parent
                guard++
            }
            return sourceNode
        }

        return activeRoot
    }

    private fun inspectHierarchy(
        targetApp: TargetApp,
        repo: ReelRepository,
        event: AccessibilityEvent
    ) {
        val rootNode = resolveTargetRootNode(targetApp, event) ?: return
        val nodeSource = AccessibilityNodeSource(rootNode)
        val snapshot = nodeSource.captureSnapshot(maxDepth = 45)

        when (targetApp) {
            TargetApp.INSTAGRAM -> {
                // Check child overlay (comments / share sheet) BEFORE checking viewer exit!
                if (instagramDetector.isChildOverlayActive(snapshot)) {
                    dedupEngine.touchActiveTimestamp()
                    DetectorLogger.logTransitionRejected(
                        targetApp,
                        "Comments or share bottom sheet active"
                    )
                    return
                }

                if (!instagramDetector.isViewerActive(snapshot)) {
                    handlePotentialViewerExit(targetApp, event.eventType)
                    return
                }

                consecutiveNonViewerFrames = 0
                DetectorLogger.logReelViewerDetected(targetApp, true, "ClipsViewer")
                stateMachine.onViewerEntered(targetApp)

                val candidate = instagramDetector.extractCandidate(snapshot)
                if (candidate != null) {
                    DetectorLogger.logContentIdentityExtracted(candidate)
                    evaluateCandidate(candidate, repo)
                } else {
                    DetectorLogger.logTransitionRejected(
                        targetApp,
                        "Viewer active; awaiting creator/caption metadata binding"
                    )
                }
            }
            TargetApp.YOUTUBE -> {
                // Check child overlay (comments engagement panel) BEFORE checking viewer exit!
                // When YouTube Shorts comments open, YouTube hides reel_watch_fragment_root
                // behind app_engagement_panel_wrapper.
                if (youtubeDetector.isChildOverlayActive(snapshot)) {
                    dedupEngine.touchActiveTimestamp()
                    DetectorLogger.logTransitionRejected(
                        targetApp,
                        "Shorts comments/engagement panel active"
                    )
                    return
                }

                if (!youtubeDetector.isViewerActive(snapshot)) {
                    handlePotentialViewerExit(targetApp, event.eventType)
                    return
                }

                consecutiveNonViewerFrames = 0
                DetectorLogger.logReelViewerDetected(targetApp, true, "ShortsViewer")
                stateMachine.onViewerEntered(targetApp)

                val candidate = youtubeDetector.extractCandidate(snapshot)
                if (candidate != null) {
                    DetectorLogger.logContentIdentityExtracted(candidate)
                    evaluateCandidate(candidate, repo)
                } else {
                    DetectorLogger.logTransitionRejected(
                        targetApp,
                        "Shorts viewer active; awaiting channel/title metadata binding"
                    )
                }
            }
        }
    }

    private fun handlePotentialViewerExit(targetApp: TargetApp, eventType: Int) {
        consecutiveNonViewerFrames++
        // Avoid resetting deduplication state on a single transient mid-scroll frame
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || consecutiveNonViewerFrames >= 3) {
            DetectorLogger.logReelViewerDetected(targetApp, false, "Exited Reel/Short viewer")
            stateMachine.onViewerExited()
            dwellJob?.cancel()
            dedupEngine.reset(clearRecentHistory = false)
            val repo = try { ReelMonsterApp.getOrCreateRepository(this@ReelAccessibilityService) } catch (_: Exception) { null }
            if (repo != null) {
                ViewingSessionTracker.instance.onViewerExited(targetApp, repo)
            }
        }
    }

    private fun evaluateCandidate(
        candidate: DetectionCandidate,
        repo: ReelRepository
    ) {
        val previousFp = dedupEngine.activeCandidate?.fingerprint
        if (previousFp != candidate.fingerprint) {
            DetectorLogger.logContentTransitionDetected(candidate.targetApp, previousFp, candidate)
        }

        val decision = dedupEngine.processCandidate(candidate)

        // If the previous candidate had just satisfied minDwellMs right as this swipe arrived,
        // commit it so fast consecutive swipes never drop a valid viewed Reel!
        val autoConfirmedPrev = dedupEngine.consumeAutoConfirmedPrevious()
        if (autoConfirmedPrev != null) {
            stateMachine.onContentConfirmed(autoConfirmedPrev)
            val prevSuccess = repo.logReel(autoConfirmedPrev.targetApp, TrackingMode.LEVEL_2_AUTOMATIC)
            DetectorLogger.logRepositoryIncrement(
                app = autoConfirmedPrev.targetApp,
                fingerprint = autoConfirmedPrev.fingerprint,
                success = prevSuccess,
                newTotalToday = repo.todayCount.value
            )
            ViewingSessionTracker.instance.onReelConfirmed(autoConfirmedPrev, repo)
        }

        val elapsedMs = dedupEngine.currentDwellElapsedMs()
        DetectorLogger.logDeduplicationResult(candidate, decision, elapsedMs)

        when (decision) {
            DeduplicationEngine.Decision.CONFIRMED -> {
                dwellJob?.cancel()
                commitConfirmedCandidate(candidate, repo)
            }
            DeduplicationEngine.Decision.PENDING_DWELL,
            DeduplicationEngine.Decision.IGNORED_FLING -> {
                if (decision == DeduplicationEngine.Decision.IGNORED_FLING) {
                    DetectorLogger.logTransitionRejected(
                        candidate.targetApp,
                        "Outgoing item dwelled < ${dedupEngine.minDwellMs}ms (fling); scheduling dwell for incoming item",
                        candidate.fingerprint
                    )
                }
                stateMachine.onScrollTransitionStarted()
                scheduleDwellConfirmation(candidate, repo)
            }
            DeduplicationEngine.Decision.IGNORED_SAME_CONTENT -> {
                DetectorLogger.logTransitionRejected(
                    candidate.targetApp,
                    "Same content re-rendered (+0)",
                    candidate.fingerprint
                )
            }
            DeduplicationEngine.Decision.IGNORED_RECENT_BOUNCE_BACK -> {
                DetectorLogger.logTransitionRejected(
                    candidate.targetApp,
                    "Recent bounce-back within ${dedupEngine.bounceBackWindowMs}ms (+0)",
                    candidate.fingerprint
                )
            }
            DeduplicationEngine.Decision.IGNORED_INVALID -> {
                DetectorLogger.logTransitionRejected(
                    candidate.targetApp,
                    "Invalid candidate",
                    candidate.fingerprint
                )
            }
        }
    }

    private fun scheduleDwellConfirmation(
        candidate: DetectionCandidate,
        repo: ReelRepository
    ) {
        dwellJob?.cancel()
        dwellJob = serviceScope.launch {
            delay(dedupEngine.minDwellMs + 30L)
            val dwellDecision = dedupEngine.checkDwell()
            val active = dedupEngine.activeCandidate ?: candidate
            DetectorLogger.logDeduplicationResult(
                active,
                dwellDecision,
                dedupEngine.currentDwellElapsedMs()
            )
            if (dwellDecision == DeduplicationEngine.Decision.CONFIRMED) {
                commitConfirmedCandidate(active, repo)
            }
        }
    }

    private fun commitConfirmedCandidate(
        candidate: DetectionCandidate,
        repo: ReelRepository
    ) {
        stateMachine.onContentConfirmed(candidate)
        val success = repo.logReel(candidate.targetApp, TrackingMode.LEVEL_2_AUTOMATIC)
        if (success) {
            repo.recordSupportedAppDetection(
                app = candidate.targetApp,
                eventType = if (candidate.targetApp == TargetApp.INSTAGRAM) "REEL_DETECTED" else "SHORT_DETECTED",
                fingerprint = candidate.fingerprint
            )
        }
        DetectorLogger.logRepositoryIncrement(
            app = candidate.targetApp,
            fingerprint = candidate.fingerprint,
            success = success,
            newTotalToday = repo.todayCount.value
        )
        ViewingSessionTracker.instance.onReelConfirmed(candidate, repo)
        stateMachine.onContentStabilized()
    }

    private fun eventTypeToString(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "TYPE_WINDOW_CONTENT_CHANGED"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "TYPE_VIEW_SCROLLED"
            AccessibilityEvent.TYPE_VIEW_SELECTED -> "TYPE_VIEW_SELECTED"
            else -> "TYPE_$eventType"
        }
    }

    override fun onInterrupt() {
        dwellJob?.cancel()
        val repo = try { ReelMonsterApp.getOrCreateRepository(this) } catch (_: Exception) { null }
        if (repo != null) {
            ViewingSessionTracker.instance.endActiveSession(repo, "INTERRUPTED_SERVICE_STOP")
        }
        stateMachine.reset()
        dedupEngine.reset(clearRecentHistory = false)
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        dwellJob?.cancel()
        heartbeatJob?.cancel()
        serviceScope.cancel()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {}
        val repo = try { ReelMonsterApp.getOrCreateRepository(this) } catch (_: Exception) { null }
        if (repo != null) {
            ViewingSessionTracker.instance.endActiveSession(repo, "INTERRUPTED_SERVICE_STOP")
            repo.onAccessibilityServiceConnectionChanged(false)
        }
        stateMachine.reset()
        dedupEngine.reset(clearRecentHistory = true)
    }
}
