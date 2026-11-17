package com.reelmonster.app.presentation.dashboard

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.reelmonster.app.core.util.OverlayPermissionHelper
import com.reelmonster.app.presentation.components.OverlayPermissionDialog
import com.reelmonster.app.service.overlay.FloatingMonsterOverlayService
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelmonster.app.core.design.MintBackground
import com.reelmonster.app.core.design.MintCardBorder
import com.reelmonster.app.core.design.MintPrimary
import com.reelmonster.app.core.design.MintPrimaryDark
import com.reelmonster.app.core.design.MintSurface
import com.reelmonster.app.core.design.PurpleAccent
import com.reelmonster.app.core.design.PurpleDark
import com.reelmonster.app.core.design.PurpleLight
import com.reelmonster.app.core.design.TextMuted
import com.reelmonster.app.core.design.TextPrimary
import com.reelmonster.app.core.design.TextSecondary
import com.reelmonster.app.core.design.WarningAmber
import com.reelmonster.app.core.design.YouTubeRed
import com.reelmonster.app.core.design.YouTubeRedLight
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.core.util.HapticHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.MunchieMascotEngine
import com.reelmonster.app.domain.model.MunchiePersonalityState
import com.reelmonster.app.domain.model.MunchieStage
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import com.reelmonster.app.domain.model.focus.FocusStatus
import com.reelmonster.app.presentation.components.AccessibilityDisclosureDialog
import com.reelmonster.app.presentation.components.AppCard
import com.reelmonster.app.presentation.components.ConfirmationDialog
import com.reelmonster.app.presentation.components.EmptyState
import com.reelmonster.app.presentation.components.MunchieMascot
import com.reelmonster.app.presentation.components.MunchieSpeechBubble
import com.reelmonster.app.presentation.components.OverlayPermissionDialog
import com.reelmonster.app.presentation.components.ParticleBurst
import com.reelmonster.app.presentation.components.PrimaryButton
import com.reelmonster.app.presentation.components.RadialFlashEffect
import com.reelmonster.app.presentation.components.ReelMonsterTopBar
import com.reelmonster.app.presentation.components.SecondaryButton
import com.reelmonster.app.presentation.components.SectionHeader
import com.reelmonster.app.presentation.components.SevenDayActivityChart
import com.reelmonster.app.presentation.components.StageUpDialog
import com.reelmonster.app.presentation.components.StatCard
import com.reelmonster.app.presentation.components.TrackingStatusBadge
import com.reelmonster.app.presentation.components.openAccessibilitySettings
import com.reelmonster.app.presentation.components.tilt3D
import com.reelmonster.app.presentation.goals.GoalStateBadge
import com.reelmonster.app.service.tracking.ReelAccessibilityService

@Composable
fun DashboardScreen(
    repository: ReelRepository,
    onNavigateToStats: () -> Unit = {},
    onNavigateToGoals: () -> Unit = {},
    onNavigateToFocus: () -> Unit = {},
    onNavigateToProgression: () -> Unit = {},
    onNavigateToWeeklyIntelligence: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val count by repository.todayCount.collectAsState()
    val stage by repository.munchieStage.collectAsState()
    val platformBreakdown by repository.platformBreakdown.collectAsState()
    val todayViewingTimeMs by repository.todayViewingTimeMs.collectAsState()
    val todayPlatformViewingTimeMs by repository.todayPlatformViewingTimeMs.collectAsState()
    val todayAverageDurationMs by repository.todayAverageDurationMs.collectAsState()
    val remainingDailyResets by repository.remainingDailyResets.collectAsState()
    val last7Days by repository.last7Days.collectAsState()
    val sevenDayActivity by repository.sevenDayActivity.collectAsState()
    val personalityState by repository.munchiePersonalityState.collectAsState()
    val personalityMessage by repository.munchiePersonalityMessage.collectAsState()
    val mascotReactionTrigger by repository.mascotReactionTrigger.collectAsState()
    val combinedCountProg by repository.combinedCountProgress.collectAsState()
    val combinedTimeProg by repository.combinedTimeProgress.collectAsState()
    val focusProgress by repository.activeFocusProgress.collectAsState()
    val todayFocusMs by repository.todayFocusTimeMs.collectAsState()
    val todayFocusCount by repository.todayFocusCompletedCount.collectAsState()
    val userProgress by repository.userProgress.collectAsState()
    val achievements by repository.achievements.collectAsState()
    val todayCoverage by repository.todayMonitoringCoverage.collectAsState()

    val preferences = repository.preferencesManager
    val isGlobalTrackingEnabled by repository.isGlobalTrackingEnabled.collectAsState()
    val isOverlayEnabled by preferences.isOverlayEnabled.collectAsState()
    val isTrackingPaused by preferences.isTrackingPaused.collectAsState()
    val trackingMode by preferences.trackingMode.collectAsState()
    val monitorInstagram by preferences.monitorInstagram.collectAsState()
    val monitorYouTube by preferences.monitorYouTube.collectAsState()
    val monsterTheme by preferences.monsterTheme.collectAsState()
    val reactionStyle by preferences.reactionStyle.collectAsState()
    val isMascotAnimationEnabled by preferences.isMascotAnimationEnabled.collectAsState()
    val isSoundFxEnabled by preferences.isSoundFxEnabled.collectAsState()
    val isHapticEnabled by preferences.isHapticEnabled.collectAsState()

    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var pendingOverlayStart by remember { mutableStateOf(false) }
    var showStageUpDialog by remember { mutableStateOf(false) }
    var showDailyResetDialog by remember { mutableStateOf(false) }
    var previousStage by remember { mutableStateOf(stage) }

    androidx.compose.runtime.LaunchedEffect(stage) {
        if (stage.level > previousStage.level) {
            showStageUpDialog = true
        }
        previousStage = stage
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        repository.onDashboardOpened()
    }

    var hasOverlayPermission by remember { mutableStateOf(OverlayPermissionHelper.canDrawOverlays(context)) }
    var hasAccessibilityPermission by remember {
        mutableStateOf(ReelAccessibilityService.isAccessibilityPermissionGranted(context))
    }
    val isAccessibilityRunning by ReelAccessibilityService.isServiceRunning.collectAsState()
    val isOverlayServiceRunning by FloatingMonsterOverlayService.isRunningFlow.collectAsState()

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowHasOverlay = OverlayPermissionHelper.canDrawOverlays(context)
                hasOverlayPermission = nowHasOverlay
                hasAccessibilityPermission = ReelAccessibilityService.isAccessibilityPermissionGranted(context)
                if (pendingOverlayStart) {
                    pendingOverlayStart = false
                    if (nowHasOverlay) {
                        preferences.setOverlayEnabled(true)
                        if (!FloatingMonsterOverlayService.isRunning) {
                            FloatingMonsterOverlayService.startService(context)
                        }
                    } else {
                        preferences.setOverlayEnabled(false)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        ReelMonsterTopBar(title = "Home")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Header Greeting with Help Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hey there, scroller! 👋",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when (stage) {
                            MunchieStage.BABY -> "Your monster is resting calmly."
                            MunchieStage.CURIOUS -> "Munchie is getting curious about your feed."
                            MunchieStage.HUNGRY -> "Today's Reel appetite is looking heavy."
                            MunchieStage.STUFFED -> "Munchie is stuffed and slowing down!"
                            MunchieStage.MEGACHONK -> "Megachonk danger zone! Put the phone down!"
                        },
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MintSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "Help",
                        tint = MintPrimaryDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // GLOBAL TRACKING MASTER CARD
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isGlobalTrackingEnabled) MintSurface else Color(0xFFF3F4F6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = if (isGlobalTrackingEnabled) MintPrimaryDark else Color(0xFF6B7280),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Global Tracking Master Switch",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isGlobalTrackingEnabled) {
                                    "Active • Monitoring enabled per mode"
                                } else {
                                    "Disabled • All tracking and timers stopped"
                                },
                                fontSize = 11.sp,
                                color = if (isGlobalTrackingEnabled) MintPrimaryDark else WarningAmber
                            )
                        }
                    }

                    Switch(
                        checked = isGlobalTrackingEnabled,
                        onCheckedChange = { enabled ->
                            repository.setGlobalTrackingEnabled(enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MintPrimary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFD1D5DB)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // STATUS BANNER: Active Tracking Mode & Toggle
            AppCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isTrackingActive = !isTrackingPaused
                        TrackingStatusBadge(
                            isTrackingActive = isTrackingActive,
                            mode = trackingMode,
                            isGlobalTrackingEnabled = isGlobalTrackingEnabled
                        )

                        Button(
                            onClick = {
                                if (isTrackingPaused) {
                                    preferences.resumeTracking()
                                } else {
                                    preferences.pauseTracking()
                                }
                            },
                            enabled = isGlobalTrackingEnabled,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isGlobalTrackingEnabled) MintSurface else Color(0xFFF3F4F6),
                                contentColor = if (isGlobalTrackingEnabled) MintPrimaryDark else Color(0xFF9CA3AF)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                imageVector = if (!isTrackingPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (!isTrackingPaused) "Pause" else "Resume",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (trackingMode == TrackingMode.LEVEL_2_AUTOMATIC && !hasAccessibilityPermission) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(WarningAmber.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️ Auto-tracking pending accessibility permission.",
                                fontSize = 11.sp,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { showAccessibilityDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MintPrimary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text(text = "Setup", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (trackingMode == TrackingMode.LEVEL_1_MANUAL) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MintSurface)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (hasAccessibilityPermission) {
                                    "✨ Auto-detection service is ready! Switch to hands-free mode."
                                } else {
                                    "🤖 Want hands-free Reel counting? Enable Auto Mode."
                                },
                                fontSize = 11.sp,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    preferences.setTrackingMode(TrackingMode.LEVEL_2_AUTOMATIC)
                                    if (!hasAccessibilityPermission) {
                                        showAccessibilityDialog = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MintPrimary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text(text = "Use Auto", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // HERO MONSTER CARD (Screen 02 Focal Point)
            AppCard(
                elevation = 2.dp,
                modifier = Modifier.tilt3D()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Stage Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(MintSurface)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Stage ${stage.level} of 5: ${stage.stageName} ${stage.emoji}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MintPrimaryDark
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Speech Bubble with Context-Aware Personality Message
                    MunchieSpeechBubble(
                        text = if (!isGlobalTrackingEnabled) {
                            "Munchie is resting calmly. Global tracking is turned OFF."
                        } else {
                            personalityMessage
                        },
                        modifier = Modifier.fillMaxWidth(0.92f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Munchie Mascot with Phase 10 Personality, 1C Squash-and-Stretch & Tap Interactions
                    Box(contentAlignment = Alignment.Center) {
                        MunchieMascot(
                            stage = stage,
                            size = 140.dp,
                            showHalo = true,
                            isAnimated = isMascotAnimationEnabled && isGlobalTrackingEnabled,
                            theme = monsterTheme,
                            isPaused = isTrackingPaused || !isGlobalTrackingEnabled,
                            reactionTrigger = count + mascotReactionTrigger,
                            reactionStyle = reactionStyle,
                            personality = personalityState,
                            onTap = {
                                HapticHelper.performCountTick(context, isHapticEnabled, view)
                                repository.onMascotTapped()
                            },
                            onLongPress = {
                                HapticHelper.performStageEvolution(context, isHapticEnabled, view)
                                repository.onMascotLongPressed()
                            }
                        )

                        // Step 1E: Particle burst on increment
                        ParticleBurst(trigger = count + mascotReactionTrigger)

                        // Step 1G: Expanding radial-gradient flash on stage evolution
                        RadialFlashEffect(
                            stage = stage,
                            isHapticEnabled = isHapticEnabled,
                            isSoundEnabled = isSoundFxEnabled
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Evolution Progress
                    val nextTarget = stage.maxReels + 1
                    val progress = MunchieMascotEngine.getProgressToNextStage(count)
                    val reelsLeft = MunchieMascotEngine.getReelsUntilNextStage(count)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (stage == MunchieStage.MEGACHONK) "Maximum Capacity Reached" else "Progress to Next Evolution",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (stage == MunchieStage.MEGACHONK) "$count reels (Max)" else "$reelsLeft reels left",
                            fontSize = 12.sp,
                            color = MintPrimaryDark,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MintPrimary,
                        trackColor = MintSurface,
                        strokeCap = StrokeCap.Round
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Level 1 Quick Log Action (+1 Reel Watched)
                    val quickLogText = when {
                        !isGlobalTrackingEnabled -> "Tracking Disabled (Turn ON Master Switch)"
                        isTrackingPaused -> "Resume & Log +1 Reel"
                        trackingMode == TrackingMode.LEVEL_2_AUTOMATIC -> "+1 Reel Watched (Manual Fallback)"
                        else -> "+1 Reel Watched (Quick Log)"
                    }
                    PrimaryButton(
                        text = quickLogText,
                        onClick = {
                            HapticHelper.performCountTick(context, isHapticEnabled, view)
                            if (isSoundFxEnabled) {
                                view.playSoundEffect(SoundEffectConstants.CLICK)
                            }
                            if (isTrackingPaused) {
                                preferences.resumeTracking()
                                repository.logReel(TargetApp.INSTAGRAM, force = true)
                            } else {
                                repository.logReel(TargetApp.INSTAGRAM)
                            }
                        },
                        enabled = isGlobalTrackingEnabled,
                        leadingIcon = Icons.Default.Add,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // METRICS ROW: Today's Count & Actual Viewing Time (Using StatCards)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Today's Reels Card
                StatCard(
                    title = "Today's Reels",
                    value = count.toString(),
                    subtitle = "Reels & Shorts today",
                    icon = Icons.Default.PlayArrow,
                    iconTint = MintPrimaryDark,
                    iconBackground = MintSurface,
                    modifier = Modifier.weight(1f)
                )

                // Actual Viewing Time Card (Measured via Monotonic Clock SystemClock.elapsedRealtime)
                val formattedViewingTime = DateTimeUtils.formatDurationMs(todayViewingTimeMs)
                val avgSubtitle = if (todayAverageDurationMs > 0L) {
                    "Avg: ${DateTimeUtils.formatDurationMs(todayAverageDurationMs)}/reel"
                } else {
                    "Awaiting sessions"
                }
                StatCard(
                    title = "Actual Viewing Time",
                    value = formattedViewingTime,
                    subtitle = avgSubtitle,
                    icon = Icons.Default.Schedule,
                    iconTint = PurpleAccent,
                    iconBackground = PurpleLight,
                    modifier = Modifier.weight(1f)
                )
            }

            // Phase 12: Daily Goal Pacing Card
            if (combinedCountProg != null || combinedTimeProg != null) {
                Spacer(modifier = Modifier.height(14.dp))
                AppCard(
                    modifier = Modifier.clickable { onNavigateToGoals() }
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(MintSurface, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Flag,
                                        contentDescription = null,
                                        tint = MintPrimaryDark,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Daily Goal Pacing",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Tap to view and edit limits",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                            combinedCountProg?.let { prog ->
                                GoalStateBadge(state = prog.state)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Dual Mini Progress Rows
                        combinedCountProg?.let { prog ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Reels: ${prog.currentValue} / ${prog.goal.targetValue}",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = prog.formattedRemaining,
                                    fontSize = 11.sp,
                                    color = if (prog.isLimitBreached) YouTubeRed else TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { prog.usageRatio.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (prog.isLimitBreached) YouTubeRed else MintPrimary,
                                trackColor = Color(0xFFF3F4F6)
                            )
                        }

                        if (combinedTimeProg != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val timeProg = combinedTimeProg!!
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Viewing Time: ${timeProg.currentValue} / ${timeProg.goal.targetValue}m",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = timeProg.formattedRemaining,
                                    fontSize = 11.sp,
                                    color = if (timeProg.isLimitBreached) YouTubeRed else TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { timeProg.usageRatio.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (timeProg.isLimitBreached) YouTubeRed else MintPrimary,
                                trackColor = Color(0xFFF3F4F6)
                            )
                        }
                    }
                }
            }

            // Phase 13: Focus Mode Card
            Spacer(modifier = Modifier.height(14.dp))
            val currentFocus = focusProgress
            AppCard(
                modifier = Modifier.clickable { onNavigateToFocus() }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(PurpleLight, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SelfImprovement,
                                    contentDescription = null,
                                    tint = PurpleDark,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Focus Mode & Interventions",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (currentFocus != null && currentFocus.status.isActive) {
                                        if (currentFocus.status == FocusStatus.RUNNING) "Session in progress" else "Session paused"
                                    } else {
                                        if (todayFocusCount > 0) "$todayFocusCount completed today (${DateTimeUtils.formatDurationMs(todayFocusMs)})" else "Mindful breaks from scrolling"
                                    },
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Badge / Status
                        if (currentFocus != null && currentFocus.status.isActive) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (currentFocus.status == FocusStatus.RUNNING) MintPrimary.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (currentFocus.status == FocusStatus.RUNNING) "FOCUSING 🎯" else "PAUSED ⏸️",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentFocus.status == FocusStatus.RUNNING) MintPrimaryDark else WarningAmber,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MintSurface
                            ) {
                                Text(
                                    text = "START ⏱️",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MintPrimaryDark,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // If active session, show remaining countdown and linear progress
                    if (currentFocus != null && currentFocus.status.isActive) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = currentFocus.type.displayName,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${currentFocus.formattedRemaining} remaining",
                                fontSize = 12.sp,
                                color = PurpleDark,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { currentFocus.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = PurpleAccent,
                            trackColor = Color(0xFFF3F4F6)
                        )
                    }
                }
            }

            // Phase 14: Progression, Streaks & Estimated Monitoring Coverage Card
            Spacer(modifier = Modifier.height(14.dp))
            AppCard(
                modifier = Modifier.clickable { onNavigateToProgression() }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(MintBackground, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Whatshot,
                                    contentDescription = null,
                                    tint = MintPrimaryDark,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Level ${userProgress.currentLevel} • ${userProgress.levelTitle}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${userProgress.totalXp} XP • ${userProgress.currentStreak}d Streak • ${achievements.count { it.isUnlocked }}/${achievements.size} Badges",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MintSurface
                        ) {
                            Text(
                                text = "VIEW 🏆",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MintPrimaryDark,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { userProgress.levelProgressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MintPrimaryDark,
                        trackColor = MintSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Estimated Monitoring Coverage: ${todayCoverage.estimatedVerifiedMinutes}/120m",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                        Text(
                            text = "${userProgress.levelProgressPercent}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MintPrimaryDark
                        )
                    }
                }
            }

            // Phase 15: Weekly Intelligence Card
            Spacer(modifier = Modifier.height(14.dp))
            AppCard(
                modifier = Modifier.clickable { onNavigateToWeeklyIntelligence() }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(MintBackground, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = MintPrimaryDark,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Weekly Intelligence",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Hourly pattern • week vs week • reflections",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MintSurface
                        ) {
                            Text(
                                text = "VIEW 📊",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MintPrimaryDark,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // DAILY RESET ALLOWANCE CARD
            Spacer(modifier = Modifier.height(14.dp))
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Today's Tracking Reset",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$remainingDailyResets of ${UserPreferencesManager.MAX_DAILY_RESETS} resets remaining today",
                            fontSize = 11.sp,
                            color = if (remainingDailyResets > 0) MintPrimaryDark else WarningAmber
                        )
                    }

                    Button(
                        onClick = { showDailyResetDialog = true },
                        enabled = remainingDailyResets > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (remainingDailyResets > 0) Color(0xFFFEF3C7) else Color(0xFFF3F4F6),
                            contentColor = if (remainingDailyResets > 0) Color(0xFF92400E) else Color(0xFF9CA3AF)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = "Restart Today",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Empty state helper banner when count is 0
            if (count == 0) {
                Spacer(modifier = Modifier.height(14.dp))
                AppCard(containerColor = MintSurface, borderColor = MintPrimary.copy(alpha = 0.4f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🌱", fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Zero Reels Logged Today!",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MintPrimaryDark
                            )
                            Text(
                                text = "Your focus streak is completely clean. Munchie is content and resting.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // FLOATING MONSTER OVERLAY CARD
            AppCard {
                SectionHeader(
                    title = "Floating Monster Companion",
                    subtitle = "Munchie floats above Instagram & YouTube"
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MintSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = MintPrimaryDark,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Floating Bubble Overlay",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            val statusDescription = if (!hasOverlayPermission) {
                                "Permission Required • Tap to grant"
                            } else if (isOverlayEnabled && isOverlayServiceRunning) {
                                "Active • Draggable on screen"
                            } else if (isOverlayEnabled) {
                                "Ready • Service started"
                            } else {
                                "Disabled"
                            }
                            Text(
                                text = statusDescription,
                                fontSize = 11.sp,
                                color = if (!hasOverlayPermission) WarningAmber else TextMuted
                            )
                        }
                    }

                    Switch(
                        checked = isOverlayEnabled && hasOverlayPermission,
                        onCheckedChange = { enable ->
                            if (enable) {
                                if (hasOverlayPermission) {
                                    preferences.setOverlayEnabled(true)
                                    FloatingMonsterOverlayService.startService(context)
                                } else {
                                    pendingOverlayStart = true
                                    showPermissionDialog = true
                                }
                            } else {
                                preferences.setOverlayEnabled(false)
                                FloatingMonsterOverlayService.stopService(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MintPrimary
                        )
                    )
                }

                if (!hasOverlayPermission) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PrimaryButton(
                        text = "Grant Overlay Permission",
                        onClick = {
                            pendingOverlayStart = true
                            showPermissionDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (showPermissionDialog) {
                OverlayPermissionDialog(
                    onDismissRequest = {
                        pendingOverlayStart = false
                        showPermissionDialog = false
                    },
                    onGrantPermission = {
                        showPermissionDialog = false
                        OverlayPermissionHelper.requestOverlayPermission(context)
                    }
                )
            }

            if (showAccessibilityDialog) {
                AccessibilityDisclosureDialog(
                    onDismissRequest = { showAccessibilityDialog = false },
                    onOpenSettings = {
                        showAccessibilityDialog = false
                        openAccessibilitySettings(context)
                    },
                    onFallbackToManual = {
                        showAccessibilityDialog = false
                        preferences.setTrackingMode(TrackingMode.LEVEL_1_MANUAL)
                    }
                )
            }

            if (showStageUpDialog) {
                StageUpDialog(
                    stage = stage,
                    theme = monsterTheme,
                    onDismiss = { showStageUpDialog = false }
                )
            }

            if (showDailyResetDialog) {
                ConfirmationDialog(
                    title = "Restart today's tracking?",
                    message = "Today's count and viewing-time totals will reset. Previous days' history will remain saved.",
                    confirmText = "Restart Today",
                    dismissText = "Cancel",
                    isDestructive = false,
                    onConfirm = {
                        showDailyResetDialog = false
                        repository.performDailyReset()
                    },
                    onDismiss = { showDailyResetDialog = false }
                )
            }
            AppCard {
                SectionHeader(
                    title = "Selected Video Apps",
                    subtitle = "Choose which apps Munchie monitors"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Instagram Selection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PurpleLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = PurpleAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Instagram Reels",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (monitorInstagram) "Monitored • com.instagram.android" else "Disabled",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Switch(
                        checked = monitorInstagram,
                        onCheckedChange = { preferences.setMonitorInstagram(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MintPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // YouTube Selection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(YouTubeRedLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = YouTubeRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "YouTube Shorts",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (monitorYouTube) "Monitored • com.google.android.youtube" else "Disabled",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Switch(
                        checked = monitorYouTube,
                        onCheckedChange = { preferences.setMonitorYouTube(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MintPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // PLATFORM BREAKDOWN CARD (Section 4: Separate Instagram & YouTube Metrics)
            val (igCount, ytCount) = platformBreakdown
            val totalReels = igCount + ytCount
            // Phase 15: the platform cards below already show actual measured
            // viewing time; the old count x 1.5 min estimates are gone.

            AppCard {
                SectionHeader(
                    title = "Platform Breakdown",
                    subtitle = "Instagram Reels vs YouTube Shorts"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Side-by-side platform stat cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Instagram Reels Card
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = PurpleLight.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, PurpleAccent.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(PurpleAccent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Instagram",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PurpleDark
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "$igCount Reels",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "⏱️ ${DateTimeUtils.formatDurationMs(todayPlatformViewingTimeMs.first)} viewing",
                                fontSize = 11.sp,
                                color = PurpleDark
                            )
                        }
                    }

                    // YouTube Shorts Card
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = YouTubeRedLight.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, YouTubeRed.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(YouTubeRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "YouTube",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = YouTubeRed
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "$ytCount Shorts",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "⏱️ ${DateTimeUtils.formatDurationMs(todayPlatformViewingTimeMs.second)} viewing",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (totalReels > 0) {
                    val igRatio = igCount.toFloat() / totalReels.coerceAtLeast(1)

                    // Split visual bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(igRatio.coerceAtLeast(0.01f))
                                .height(8.dp)
                                .background(PurpleAccent)
                        )
                        Box(
                            modifier = Modifier
                                .weight((1f - igRatio).coerceAtLeast(0.01f))
                                .height(8.dp)
                                .background(MintPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "IG: ${(igRatio * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = PurpleDark
                        )
                        Text(
                            text = "Total Today: $totalReels",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "YT: ${(100 - (igRatio * 100).toInt())}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MintPrimaryDark
                        )
                    }
                } else {
                    Text(
                        text = "Zero platform activity recorded today.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // LAST SEVEN DAYS ACTIVITY CHART (Section 5: Visual Continuous 7-Calendar-Day Chart)
            AppCard {
                SectionHeader(
                    title = "Last 7 Days Activity",
                    subtitle = "Daily scroll velocity & binge spikes",
                    actionText = "Full Stats",
                    onActionClick = onNavigateToStats
                )

                Spacer(modifier = Modifier.height(12.dp))

                SevenDayActivityChart(sevenDayActivity = sevenDayActivity)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // SECONDARY QUICK LOG ACTION: YouTube Short
            SecondaryButton(
                text = if (!isTrackingPaused) "Log +1 YouTube Short (Manual)" else "Resume & Log +1 Short",
                onClick = {
                    HapticHelper.performCountTick(context, isHapticEnabled, view)
                    if (isSoundFxEnabled) {
                        view.playSoundEffect(SoundEffectConstants.CLICK)
                    }
                    if (isTrackingPaused) {
                        preferences.resumeTracking()
                        repository.logReel(TargetApp.YOUTUBE, force = true)
                    } else {
                        repository.logReel(TargetApp.YOUTUBE)
                    }
                },
                leadingIcon = Icons.Default.Add,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
