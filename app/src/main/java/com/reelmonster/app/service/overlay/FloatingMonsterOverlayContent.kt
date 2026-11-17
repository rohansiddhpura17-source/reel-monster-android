package com.reelmonster.app.service.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelmonster.app.core.design.MintCardBorder
import com.reelmonster.app.core.design.MintPrimary
import com.reelmonster.app.core.design.MintPrimaryBright
import com.reelmonster.app.core.design.MintPrimaryDark
import com.reelmonster.app.domain.model.focus.FocusSessionProgress
import com.reelmonster.app.core.design.MintSurface
import com.reelmonster.app.core.design.PurpleAccent
import com.reelmonster.app.core.design.PurpleDark
import com.reelmonster.app.core.design.PurpleLight
import com.reelmonster.app.core.design.SurfaceWhite
import com.reelmonster.app.core.design.TextPrimary
import com.reelmonster.app.core.design.TextSecondary
import com.reelmonster.app.core.design.WarningAmber
import com.reelmonster.app.core.design.YouTubeRed
import com.reelmonster.app.core.design.YouTubeRedLight
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.core.util.HapticHelper
import com.reelmonster.app.domain.model.MunchieMascotEngine
import com.reelmonster.app.domain.model.MunchiePersonalityState
import com.reelmonster.app.domain.model.MunchieStage
import com.reelmonster.app.domain.model.MunchieTheme
import com.reelmonster.app.domain.model.OverlayState
import com.reelmonster.app.domain.model.ReactionStyle
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import com.reelmonster.app.presentation.components.MunchieMascot
import com.reelmonster.app.presentation.components.OdometerCounter
import com.reelmonster.app.presentation.components.ParticleBurst
import com.reelmonster.app.presentation.components.PrimaryButton
import com.reelmonster.app.presentation.components.RadialFlashEffect
import com.reelmonster.app.presentation.components.SecondaryButton
import com.reelmonster.app.presentation.components.TrackingStatusBadge

/**
 * FloatingMonsterOverlayContent
 *
 * Renders either the compact floating Munchie bubble or the expanded mini-HUD
 * panel using approved Stitch designs and real application state.
 */
@Composable
fun FloatingMonsterOverlayContent(
    overlayState: OverlayState,
    todayCount: Int,
    munchieStage: MunchieStage,
    isPaused: Boolean,
    isGlobalTrackingEnabled: Boolean = true,
    todayViewingTimeMs: Long = 0L,
    liveTimerElapsedMs: Long = 0L,
    trackingMode: TrackingMode,
    theme: MunchieTheme = MunchieTheme.MINT_CLASSIC,
    reactionStyle: ReactionStyle = ReactionStyle.BOUNCE,
    isMascotAnimationEnabled: Boolean = true,
    isHapticEnabled: Boolean = false,
    isSoundFxEnabled: Boolean = false,
    personalityState: MunchiePersonalityState = MunchiePersonalityState.IDLE,
    personalityMessage: String? = null,
    personalityReactionTrigger: Int = 0,
    isFocusOverlayEnabled: Boolean = true,
    focusProgress: FocusSessionProgress? = null,
    onMascotTap: (() -> Unit)? = null,
    onMascotLongPress: (() -> Unit)? = null,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onLogReel: (TargetApp) -> Unit,
    onTogglePause: () -> Unit,
    onToggleGlobalTracking: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    AnimatedContent(
        targetState = overlayState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "OverlayStateAnimation"
    ) { state ->
        when (state) {
            OverlayState.EXPANDED -> {
                ExpandedOverlayPanel(
                    todayCount = todayCount,
                    munchieStage = munchieStage,
                    isPaused = isPaused,
                    isGlobalTrackingEnabled = isGlobalTrackingEnabled,
                    todayViewingTimeMs = todayViewingTimeMs,
                    liveTimerElapsedMs = liveTimerElapsedMs,
                    trackingMode = trackingMode,
                    theme = theme,
                    reactionStyle = reactionStyle,
                    isMascotAnimationEnabled = isMascotAnimationEnabled,
                    isHapticEnabled = isHapticEnabled,
                    isSoundFxEnabled = isSoundFxEnabled,
                    personalityState = personalityState,
                    personalityMessage = personalityMessage,
                    personalityReactionTrigger = personalityReactionTrigger,
                    isFocusOverlayEnabled = isFocusOverlayEnabled,
                    focusProgress = focusProgress,
                    onMascotTap = onMascotTap,
                    onMascotLongPress = onMascotLongPress,
                    onCollapse = onCollapse,
                    onLogReel = onLogReel,
                    onTogglePause = onTogglePause,
                    onToggleGlobalTracking = onToggleGlobalTracking,
                    onClose = onClose
                )
            }
            else -> {
                CompactOverlayBubble(
                    todayCount = todayCount,
                    munchieStage = munchieStage,
                    isPaused = isPaused,
                    isGlobalTrackingEnabled = isGlobalTrackingEnabled,
                    liveTimerElapsedMs = liveTimerElapsedMs,
                    theme = theme,
                    reactionStyle = reactionStyle,
                    isMascotAnimationEnabled = isMascotAnimationEnabled,
                    isHapticEnabled = isHapticEnabled,
                    isSoundFxEnabled = isSoundFxEnabled,
                    personalityState = personalityState,
                    personalityReactionTrigger = personalityReactionTrigger,
                    isFocusOverlayEnabled = isFocusOverlayEnabled,
                    focusProgress = focusProgress,
                    onMascotTap = onMascotTap,
                    onMascotLongPress = onMascotLongPress,
                    onExpand = onExpand
                )
            }
        }
    }
}

/**
 * Compact draggable bubble matching Stitch Screen 06.
 */
@Composable
fun CompactOverlayBubble(
    todayCount: Int,
    munchieStage: MunchieStage,
    isPaused: Boolean,
    isGlobalTrackingEnabled: Boolean = true,
    liveTimerElapsedMs: Long = 0L,
    theme: MunchieTheme = MunchieTheme.MINT_CLASSIC,
    reactionStyle: ReactionStyle = ReactionStyle.BOUNCE,
    isMascotAnimationEnabled: Boolean = true,
    isHapticEnabled: Boolean = false,
    isSoundFxEnabled: Boolean = false,
    personalityState: MunchiePersonalityState = MunchiePersonalityState.IDLE,
    personalityReactionTrigger: Int = 0,
    isFocusOverlayEnabled: Boolean = true,
    focusProgress: FocusSessionProgress? = null,
    onMascotTap: (() -> Unit)? = null,
    onMascotLongPress: (() -> Unit)? = null,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onExpand
            ),
        contentAlignment = Alignment.Center
    ) {
        // Main circular container with elevation and border
        val surfaceColor = if (!isGlobalTrackingEnabled) Color(0xFFF3F4F6) else if (isPaused) Color(0xFFFEF3C7) else MintSurface
        val borderColor = if (!isGlobalTrackingEnabled) Color(0xFF9CA3AF) else if (isPaused) WarningAmber else MintCardBorder

        Surface(
            shape = CircleShape,
            color = surfaceColor,
            border = BorderStroke(2.dp, borderColor),
            shadowElevation = 6.dp,
            modifier = Modifier.size(62.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                MunchieMascot(
                    stage = munchieStage,
                    theme = theme,
                    isPaused = isPaused || !isGlobalTrackingEnabled,
                    reactionTrigger = todayCount + personalityReactionTrigger,
                    reactionStyle = reactionStyle,
                    isAnimated = isMascotAnimationEnabled && isGlobalTrackingEnabled,
                    personality = personalityState,
                    onTap = onMascotTap,
                    onLongPress = onMascotLongPress,
                    modifier = Modifier.size(46.dp)
                )

                // Step 1E: Particle burst on increment
                ParticleBurst(trigger = todayCount + personalityReactionTrigger)

                // Step 1G: Expanding radial-gradient flash on stage evolution
                RadialFlashEffect(
                    stage = munchieStage,
                    isHapticEnabled = isHapticEnabled,
                    isSoundEnabled = isSoundFxEnabled
                )
            }
        }

        // Top-right status / counter pill badge
        val badgeColor = if (!isGlobalTrackingEnabled) Color(0xFF6B7280) else if (isPaused) WarningAmber else MintPrimaryDark
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(
                    color = badgeColor,
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            if (!isGlobalTrackingEnabled) {
                Text(
                    text = "OFF",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                )
            } else if (isPaused) {
                Text(
                    text = "⏸",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            } else {
                OdometerCounter(
                    count = todayCount,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }
        }

        // Bottom live Reel elapsed timer pill (only shown when actively watching a video)
        if (liveTimerElapsedMs > 0L && isGlobalTrackingEnabled && !isPaused) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(
                        color = PurpleDark,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = DateTimeUtils.formatTimerMmSs(liveTimerElapsedMs),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                )
            }
        } else if (isFocusOverlayEnabled && focusProgress != null && focusProgress.status.isActive) {
            // Optional gentle focus indicator
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(
                        color = PurpleDark,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "🎯 ${focusProgress.formattedRemaining}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                )
            }
        }
    }
}

/**
 * Expanded mini-HUD card matching Stitch Screen 07.
 */
@Composable
fun ExpandedOverlayPanel(
    todayCount: Int,
    munchieStage: MunchieStage,
    isPaused: Boolean,
    isGlobalTrackingEnabled: Boolean = true,
    todayViewingTimeMs: Long = 0L,
    liveTimerElapsedMs: Long = 0L,
    trackingMode: TrackingMode,
    theme: MunchieTheme = MunchieTheme.MINT_CLASSIC,
    reactionStyle: ReactionStyle = ReactionStyle.BOUNCE,
    isMascotAnimationEnabled: Boolean = true,
    isHapticEnabled: Boolean = false,
    isSoundFxEnabled: Boolean = false,
    personalityState: MunchiePersonalityState = MunchiePersonalityState.IDLE,
    personalityMessage: String? = null,
    personalityReactionTrigger: Int = 0,
    isFocusOverlayEnabled: Boolean = true,
    focusProgress: FocusSessionProgress? = null,
    onMascotTap: (() -> Unit)? = null,
    onMascotLongPress: (() -> Unit)? = null,
    onCollapse: () -> Unit,
    onLogReel: (TargetApp) -> Unit,
    onTogglePause: () -> Unit,
    onToggleGlobalTracking: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedApp by remember { mutableStateOf(TargetApp.INSTAGRAM) }
    val funnyMessage = if (!isGlobalTrackingEnabled) {
        "Munchie is sleeping. Global tracking is turned OFF."
    } else {
        personalityMessage ?: MunchieMascotEngine.getMessage(munchieStage, todayCount, isPaused)
    }

    Surface(
        modifier = modifier
            .width(300.dp)
            .wrapContentHeight()
            .shadow(12.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.5.dp, MintCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Row: Status badge and Close (collapse) button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackingStatusBadge(
                    isTrackingActive = !isPaused,
                    mode = trackingMode,
                    isGlobalTrackingEnabled = isGlobalTrackingEnabled
                )

                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Collapse",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hero Mascot & Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(MintSurface, CircleShape)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MunchieMascot(
                        stage = munchieStage,
                        theme = theme,
                        isPaused = isPaused || !isGlobalTrackingEnabled,
                        reactionTrigger = todayCount + personalityReactionTrigger,
                        reactionStyle = reactionStyle,
                        isAnimated = isMascotAnimationEnabled && isGlobalTrackingEnabled,
                        personality = personalityState,
                        onTap = onMascotTap,
                        onLongPress = onMascotLongPress,
                        modifier = Modifier.size(68.dp)
                    )

                    // Step 1E: Particle burst on increment
                    ParticleBurst(trigger = todayCount + personalityReactionTrigger)

                    // Step 1G: Expanding radial-gradient flash on stage evolution
                    RadialFlashEffect(
                        stage = munchieStage,
                        isHapticEnabled = isHapticEnabled,
                        isSoundEnabled = isSoundFxEnabled
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Stage ${munchieStage.level}: ${munchieStage.stageName}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MintPrimaryDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OdometerCounter(
                            count = todayCount,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reels",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                    Text(
                        text = "⏱️ ${DateTimeUtils.formatDurationMs(todayViewingTimeMs)} Actual Viewing Time",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = PurpleDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    if (liveTimerElapsedMs > 0L && isGlobalTrackingEnabled && !isPaused) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color(0xFFEDE9FE), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(PurpleAccent, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Current: ${DateTimeUtils.formatTimerMmSs(liveTimerElapsedMs)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = PurpleDark,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }

            if (isFocusOverlayEnabled && focusProgress != null && focusProgress.status.isActive) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEDE9FE), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎯 Focus: ${focusProgress.type.displayName}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = PurpleDark,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "${focusProgress.formattedRemaining} left",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = PurpleDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Funny Speech Bubble from Mascot Engine
            Surface(
                color = MintSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MintCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "\"$funnyMessage\"",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MintPrimaryDark,
                        lineHeight = 16.sp
                    ),
                    modifier = Modifier.padding(10.dp),
                    textAlign = TextAlign.Start
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // App Selector Chips: Instagram Reels vs YouTube Shorts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Instagram Chip
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedApp == TargetApp.INSTAGRAM) PurpleLight else Color(0xFFF9FAFB),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selectedApp == TargetApp.INSTAGRAM) PurpleAccent else Color(0xFFE5E7EB)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedApp = TargetApp.INSTAGRAM }
                ) {
                    Text(
                        text = "Instagram",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (selectedApp == TargetApp.INSTAGRAM) PurpleDark else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                // YouTube Chip
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedApp == TargetApp.YOUTUBE) YouTubeRedLight else Color(0xFFF9FAFB),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selectedApp == TargetApp.YOUTUBE) YouTubeRed else Color(0xFFE5E7EB)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedApp = TargetApp.YOUTUBE }
                ) {
                    Text(
                        text = "YouTube",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (selectedApp == TargetApp.YOUTUBE) YouTubeRed else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // High-Emphasis "TAP ME +1" Action Button
            val buttonText = when {
                !isGlobalTrackingEnabled -> "Tracking Disabled (Global Switch OFF)"
                isPaused -> "Tracking Paused"
                else -> "TAP ME +1 ${selectedApp.mediaType.uppercase()}"
            }
            PrimaryButton(
                text = buttonText,
                onClick = {
                    HapticHelper.performCountTick(context, isHapticEnabled)
                    onLogReel(selectedApp)
                },
                enabled = isGlobalTrackingEnabled && !isPaused,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Controls: Pause/Resume and Hide Overlay
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pause / Resume Toggle
                SecondaryButton(
                    text = if (isPaused) "Resume" else "Pause",
                    leadingIcon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    onClick = onTogglePause,
                    enabled = isGlobalTrackingEnabled,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Hide / Stop Overlay Button
                SecondaryButton(
                    text = "Hide",
                    leadingIcon = Icons.Default.VisibilityOff,
                    onClick = onClose,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
