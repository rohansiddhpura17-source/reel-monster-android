package com.reelmonster.app.service.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.reelmonster.app.MainActivity
import com.reelmonster.app.R
import com.reelmonster.app.ReelMonsterApp
import com.reelmonster.app.core.design.ReelMonsterTheme
import com.reelmonster.app.core.util.OverlayPermissionHelper
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.OverlayState
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import com.reelmonster.app.service.tracking.ViewingSessionTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

/**
 * FloatingMonsterOverlayService
 *
 * Real Android WindowManager Foreground Service hosting Munchie as a floating companion
 * above Instagram Reels and YouTube Shorts.
 * Supports compact draggable bubble, edge-snapping, expanded mini-HUD panel,
 * TAP ME +1 logging, pause/resume, and real-time synchronization with ReelRepository.
 */
class FloatingMonsterOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "reel_monster_overlay_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.reelmonster.app.action.START_OVERLAY"
        const val ACTION_STOP = "com.reelmonster.app.action.STOP_OVERLAY"
        const val ACTION_PAUSE = "com.reelmonster.app.action.PAUSE_TRACKING"
        const val ACTION_RESUME = "com.reelmonster.app.action.RESUME_TRACKING"
        const val ACTION_EXPAND = "com.reelmonster.app.action.EXPAND_OVERLAY"
        const val ACTION_COLLAPSE = "com.reelmonster.app.action.COLLAPSE_OVERLAY"
        const val ACTION_LOG_REEL = "com.reelmonster.app.action.LOG_REEL"
        const val ACTION_RESET_TODAY_FOR_TEST = "com.reelmonster.app.action.RESET_TODAY_FOR_TEST"

        private val _overlayState = MutableStateFlow(OverlayState.HIDDEN)
        val overlayState = _overlayState.asStateFlow()

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow = _isRunningFlow.asStateFlow()

        var isRunning: Boolean = false
            private set(value) {
                field = value
                _isRunningFlow.value = value
            }

        fun startService(context: Context) {
            if (!OverlayPermissionHelper.canDrawOverlays(context)) {
                _overlayState.value = OverlayState.PERMISSION_MISSING
                isRunning = false
                return
            }
            val intent = Intent(context, FloatingMonsterOverlayService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                isRunning = false
                _overlayState.value = OverlayState.TRACKING_UNAVAILABLE
            }
        }

        fun stopService(context: Context) {
            isRunning = false
            _overlayState.value = OverlayState.HIDDEN
            try {
                context.stopService(Intent(context, FloatingMonsterOverlayService::class.java))
            } catch (_: Exception) {}
        }

        fun pauseTracking(context: Context) {
            if (!isRunning) return
            val intent = Intent(context, FloatingMonsterOverlayService::class.java).apply {
                action = ACTION_PAUSE
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun resumeTracking(context: Context) {
            if (!isRunning) return
            val intent = Intent(context, FloatingMonsterOverlayService::class.java).apply {
                action = ACTION_RESUME
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val overlayLifecycleOwner = OverlayLifecycleOwner()

    private lateinit var windowManager: WindowManager
    private lateinit var repository: ReelRepository

    private var rootContainer: FrameLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var isViewAttached = false
    private var isReceiverRegistered = false

    private var savedCompactX = 16
    private var savedCompactY = 200

    private var springJob: Job? = null
    private var lastLayoutUpdateTime = 0L

    internal fun getRootContainerForTesting(): FrameLayout? = rootContainer
    internal fun isViewAttachedForTesting(): Boolean = isViewAttached

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    springJob?.cancel()
                    overlayLifecycleOwner.onStop()
                }
                Intent.ACTION_SCREEN_ON -> {
                    overlayLifecycleOwner.onStart()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        overlayLifecycleOwner.onCreate()
        overlayLifecycleOwner.onStart()

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        try {
            ContextCompat.registerReceiver(
                this,
                screenReceiver,
                screenFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        } catch (_: Exception) {}

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        repository = ReelMonsterApp.getOrCreateRepository(this)

        observeRepositoryState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                if (::repository.isInitialized) {
                    repository.preferencesManager.setOverlayEnabled(false)
                }
                handleStop()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                if (::repository.isInitialized) {
                    repository.pauseTracking()
                }
                updateNotification()
                return START_STICKY
            }
            ACTION_RESUME -> {
                if (::repository.isInitialized) {
                    repository.resumeTracking()
                }
                updateNotification()
                return START_STICKY
            }
            ACTION_EXPAND -> {
                expandOverlay()
                return START_STICKY
            }
            ACTION_COLLAPSE -> {
                collapseOverlay()
                return START_STICKY
            }
            ACTION_LOG_REEL -> {
                if (::repository.isInitialized) {
                    repository.logReel(TargetApp.INSTAGRAM)
                }
                updateNotification()
                return START_STICKY
            }
            ACTION_RESET_TODAY_FOR_TEST -> {
                if (::repository.isInitialized) {
                    repository.resetTodayCountForTesting()
                    com.reelmonster.app.service.tracking.ReelAccessibilityService.dedupEngine.reset(clearRecentHistory = true)
                }
                updateNotification()
                return START_STICKY
            }
            ACTION_START -> {
                startForegroundWithNotification()
                if (!OverlayPermissionHelper.canDrawOverlays(this)) {
                    _overlayState.value = OverlayState.PERMISSION_MISSING
                    handleStop(keepState = true)
                    return START_NOT_STICKY
                }
                attachOverlayIfNeeded()
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        try {
            val notification = buildForegroundNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {}
    }

    private fun attachOverlayIfNeeded() {
        if (!OverlayPermissionHelper.canDrawOverlays(this)) {
            _overlayState.value = OverlayState.PERMISSION_MISSING
            handleStop(keepState = true)
            return
        }

        if (isViewAttached && rootContainer != null) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedCompactX
            y = savedCompactY
        }
        windowParams = params

        // CRITICAL: WindowRecomposer searches the window root view (container) for
        // ViewTreeLifecycleOwner, ViewTreeSavedStateRegistryOwner, and ViewTreeViewModelStoreOwner.
        // Setting these on BOTH container and composeView prevents IllegalStateException on attach.
        val container = FrameLayout(this).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
        }
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)

            setContent {
                ReelMonsterTheme {
                    val overlayState by _overlayState.collectAsState()
                    val count by repository.todayCount.collectAsState()
                    val stage by repository.munchieStage.collectAsState()
                    val isPaused by repository.isTrackingPaused.collectAsState()
                    val isGlobalTracking by repository.isGlobalTrackingEnabled.collectAsState()
                    val todayViewingTimeMs by repository.todayViewingTimeMs.collectAsState()
                    val liveTimerElapsedMs by ViewingSessionTracker.instance.liveElapsedTimerMs.collectAsState()
                    val trackingMode by repository.preferencesManager.trackingMode.collectAsState()
                    val theme by repository.preferencesManager.monsterTheme.collectAsState()
                    val reactionStyle by repository.preferencesManager.reactionStyle.collectAsState()
                    val isAnimEnabled by repository.preferencesManager.isMascotAnimationEnabled.collectAsState()
                    val isHaptic by repository.preferencesManager.isHapticEnabled.collectAsState()
                    val isSound by repository.preferencesManager.isSoundFxEnabled.collectAsState()
                    val personalityState by repository.munchiePersonalityState.collectAsState()
                    val personalityMessage by repository.munchiePersonalityMessage.collectAsState()
                    val mascotReactionTrigger by repository.mascotReactionTrigger.collectAsState()
                    val isFocusOverlayEnabled by repository.preferencesManager.isFocusOverlayEnabled.collectAsState()
                    val focusProgress by repository.activeFocusProgress.collectAsState()

                    FloatingMonsterOverlayContent(
                        overlayState = overlayState,
                        todayCount = count,
                        munchieStage = stage,
                        isPaused = isPaused,
                        isGlobalTrackingEnabled = isGlobalTracking,
                        todayViewingTimeMs = todayViewingTimeMs,
                        liveTimerElapsedMs = liveTimerElapsedMs,
                        trackingMode = trackingMode,
                        theme = theme,
                        reactionStyle = reactionStyle,
                        isMascotAnimationEnabled = isAnimEnabled,
                        isHapticEnabled = isHaptic,
                        isSoundFxEnabled = isSound,
                        personalityState = personalityState,
                        personalityMessage = personalityMessage,
                        personalityReactionTrigger = mascotReactionTrigger,
                        isFocusOverlayEnabled = isFocusOverlayEnabled,
                        focusProgress = focusProgress,
                        onMascotTap = {
                            repository.onMascotTapped()
                        },
                        onMascotLongPress = {
                            repository.onMascotLongPressed()
                        },
                        onExpand = { expandOverlay() },
                        onCollapse = { collapseOverlay() },
                        onLogReel = { app ->
                            repository.logReel(app, TrackingMode.LEVEL_1_MANUAL)
                            updateNotification()
                        },
                        onTogglePause = {
                            if (repository.isTrackingPaused.value) {
                                repository.resumeTracking()
                            } else {
                                repository.pauseTracking()
                            }
                            updateNotification()
                        },
                        onToggleGlobalTracking = {
                            val newSetting = !repository.preferencesManager.isGlobalTrackingEnabled.value
                            repository.setGlobalTrackingEnabled(newSetting)
                        },
                        onClose = {
                            repository.preferencesManager.setOverlayEnabled(false)
                            handleStop()
                        }
                    )
                }
            }
        }

        container.addView(composeView)
        setupDragTouchListener(container, params)

        try {
            windowManager.addView(container, params)
            rootContainer = container
            isViewAttached = true
            _overlayState.value = if (repository.isTrackingPaused.value) {
                OverlayState.PAUSED
            } else {
                OverlayState.COMPACT
            }
            updateSystemGestureExclusion(container)
        } catch (_: Exception) {
            isViewAttached = false
            rootContainer = null
            _overlayState.value = OverlayState.TRACKING_UNAVAILABLE
        }
    }

    private fun setupDragTouchListener(view: View, params: WindowManager.LayoutParams) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var isDragging = false
        var touchStartTime = 0L

        view.setOnTouchListener { _, event ->
            // In expanded mode, delegate touches directly to Compose UI controls
            if (_overlayState.value == OverlayState.EXPANDED) {
                return@setOnTouchListener false
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    springJob?.cancel()
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchStartX
                    val dy = event.rawY - touchStartY
                    val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                    if (!isDragging && dist > touchSlop) {
                        isDragging = true
                    }

                    if (isDragging) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        throttleUpdateLayout(view, params, null, null)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsedTime = System.currentTimeMillis() - touchStartTime
                    if (!isDragging && elapsedTime < 350) {
                        // Clean tap detected on compact bubble -> Expand!
                        expandOverlay()
                    } else if (isDragging) {
                        snapToEdgeWithSpring(view, params)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdgeWithSpring(view: View, params: WindowManager.LayoutParams) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val viewWidth = view.width.takeIf { it > 0 } ?: (72 * displayMetrics.density).toInt()
        val margin = (16 * displayMetrics.density).toInt()

        // Clamp to safe horizontal screen edge
        val targetX = if (params.x + viewWidth / 2 < screenWidth / 2) {
            margin
        } else {
            screenWidth - viewWidth - margin
        }

        // Clamp vertically between status bar and navigation bar
        val minY = (48 * displayMetrics.density).toInt()
        val maxY = screenHeight - view.height - (72 * displayMetrics.density).toInt()
        val clampedY = params.y.coerceIn(minY, maxY.coerceAtLeast(minY))

        // Step 1A: Spring animation with dampingRatio = 0.55f, stiffness = Spring.StiffnessLow
        springSnapTo(view, params, targetX, clampedY)
    }

    private fun springSnapTo(view: View, params: WindowManager.LayoutParams, targetX: Int, targetY: Int) {
        springJob?.cancel()
        springJob = serviceScope.launch {
            val animX = Animatable(params.x.toFloat())
            val animY = Animatable(params.y.toFloat())

            val springSpec = spring<Float>(
                dampingRatio = 0.55f,
                stiffness = Spring.StiffnessLow
            )

            val jobX = launch {
                animX.animateTo(targetValue = targetX.toFloat(), animationSpec = springSpec) {
                    throttleUpdateLayout(view, params, newX = value.toInt(), newY = null)
                }
            }

            val jobY = launch {
                animY.animateTo(targetValue = targetY.toFloat(), animationSpec = springSpec) {
                    throttleUpdateLayout(view, params, newX = null, newY = value.toInt())
                }
            }

            jobX.join()
            jobY.join()

            // Final settlement at target edge
            params.x = targetX
            params.y = targetY
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Exception) {}

            savedCompactX = targetX
            savedCompactY = targetY
            updateSystemGestureExclusion(view)
        }
    }

    private fun throttleUpdateLayout(
        view: View,
        params: WindowManager.LayoutParams,
        newX: Int?,
        newY: Int?
    ) {
        if (newX != null) params.x = newX
        if (newY != null) params.y = newY

        val now = System.currentTimeMillis()
        // Max 30fps = minimum ~33ms between WindowManager layout updates
        if (now - lastLayoutUpdateTime >= 33L) {
            lastLayoutUpdateTime = now
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Exception) {}
        }
    }

    private fun updateSystemGestureExclusion(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.post {
                val w = view.width
                val h = view.height
                if (w > 0 && h > 0) {
                    try {
                        view.systemGestureExclusionRects = listOf(android.graphics.Rect(0, 0, w, h))
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun expandOverlay() {
        val container = rootContainer ?: return
        val params = windowParams ?: return

        savedCompactX = params.x
        savedCompactY = params.y

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Center expanded card on screen
        val panelWidth = (300 * displayMetrics.density).toInt()
        params.x = ((screenWidth - panelWidth) / 2).coerceAtLeast(16)
        params.y = ((screenHeight - (360 * displayMetrics.density).toInt()) / 2).coerceAtLeast(64)

        try {
            windowManager.updateViewLayout(container, params)
        } catch (_: Exception) {}

        _overlayState.value = OverlayState.EXPANDED
    }

    private fun collapseOverlay() {
        val container = rootContainer ?: return
        val params = windowParams ?: return

        params.x = savedCompactX
        params.y = savedCompactY

        try {
            windowManager.updateViewLayout(container, params)
        } catch (_: Exception) {}

        _overlayState.value = OverlayState.COMPACT
    }

    private fun observeRepositoryState() {
        serviceScope.launch {
            repository.todayCount.collect {
                updateNotification()
            }
        }

        serviceScope.launch {
            repository.isTrackingPaused.collect { isPaused ->
                if (_overlayState.value != OverlayState.HIDDEN) {
                    if (isPaused && _overlayState.value == OverlayState.COMPACT) {
                        _overlayState.value = OverlayState.PAUSED
                    } else if (!isPaused && _overlayState.value == OverlayState.PAUSED) {
                        _overlayState.value = OverlayState.COMPACT
                    }
                }
                updateNotification()
            }
        }
    }

    private fun handleStop(keepState: Boolean = false) {
        isRunning = false
        if (!keepState) {
            _overlayState.value = OverlayState.HIDDEN
        }
        removeOverlayView()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
        stopSelf()
    }

    private fun removeOverlayView() {
        val container = rootContainer
        rootContainer = null
        isViewAttached = false
        if (container != null && ::windowManager.isInitialized) {
            try {
                windowManager.removeViewImmediate(container)
            } catch (_: Exception) {
                try {
                    windowManager.removeView(container)
                } catch (_: Exception) {}
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reel Monster Overlay Active",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Munchie floating over Instagram Reels and YouTube Shorts"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val count = if (::repository.isInitialized) repository.todayCount.value else 0
        val isPaused = if (::repository.isInitialized) repository.isTrackingPaused.value else false

        val title = if (isPaused) "Munchie is resting 💤 (Paused)" else "Munchie is on watch 👀"
        val contentText = "$count Reels Today • Tap to open Reel Monster"

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = Intent(this, FloatingMonsterOverlayService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this,
            1,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingMonsterOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeActionTitle = if (isPaused) "Resume" else "Pause"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_munchie_monster)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, pauseResumeActionTitle, pauseResumePendingIntent)
            .addAction(0, "Close", stopPendingIntent)
            .build()
    }

    private fun updateNotification() {
        if (!isRunning) return
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildForegroundNotification())
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        if (_overlayState.value != OverlayState.PERMISSION_MISSING &&
            _overlayState.value != OverlayState.TRACKING_UNAVAILABLE
        ) {
            _overlayState.value = OverlayState.HIDDEN
        }
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        springJob?.cancel()
        serviceScope.cancel()
        removeOverlayView()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
        overlayLifecycleOwner.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
