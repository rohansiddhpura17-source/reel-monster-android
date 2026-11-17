package com.reelmonster.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reelmonster.app.domain.model.MunchiePersonalityState
import com.reelmonster.app.domain.model.MunchieStage
import com.reelmonster.app.domain.model.MunchieTheme
import com.reelmonster.app.domain.model.ReactionStyle
import kotlin.math.sin

/**
 * Reusable Compose mascot component for Munchie the Reel Monster.
 * Renders consistent vector graphics across all 5 evolution stages and
 * all 10 Phase 10 personality states (IDLE, HAPPY, EXCITED, SLEEPY, CURIOUS,
 * SURPRISED, CONCERNED, CELEBRATING, FOCUSING, RESTING).
 *
 * Performance guarantee:
 * Zero continuous drawing / animation loop while idle.
 */
@Composable
fun MunchieMascot(
    stage: MunchieStage = MunchieStage.HUNGRY,
    size: Dp = 120.dp,
    showHalo: Boolean = true,
    isAnimated: Boolean = true,
    theme: MunchieTheme = MunchieTheme.MINT_CLASSIC,
    isPaused: Boolean = false,
    reactionTrigger: Int = 0,
    reactionStyle: ReactionStyle = ReactionStyle.BOUNCE,
    personality: MunchiePersonalityState? = null,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val effectivePersonality = personality ?: if (isPaused) MunchiePersonalityState.RESTING else MunchiePersonalityState.IDLE

    // Interactive squash-and-stretch reaction animation triggered on count increments or personality triggers
    val reactionProgress = remember { Animatable(0f) }
    var lastTrigger by remember { mutableIntStateOf(reactionTrigger) }

    LaunchedEffect(reactionTrigger) {
        if (reactionTrigger > 0 && reactionTrigger != lastTrigger) {
            lastTrigger = reactionTrigger
            if (isAnimated) {
                reactionProgress.snapTo(1f)
                reactionProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.55f,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
        } else {
            lastTrigger = reactionTrigger
        }
    }

    val stateLabel = if (isPaused) "Sleeping (Tracking Paused)" else effectivePersonality.displayName

    Box(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = "Munchie mascot, Stage ${stage.level}: ${stage.stageName}, ${stage.appetiteLabel}, Personality: ${effectivePersonality.displayName}, Theme: ${theme.displayName}, $stateLabel"
            }
            .then(
                if (onTap != null || onLongPress != null) {
                    Modifier.pointerInput(onTap, onLongPress) {
                        detectTapGestures(
                            onTap = { onTap?.invoke() },
                            onLongPress = { onLongPress?.invoke() }
                        )
                    }
                } else Modifier
            )
            .then(
                if (showHalo) {
                    Modifier.background(
                        color = Color.White.copy(alpha = 0.95f),
                        shape = CircleShape
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.85f)) {
            val canvasW = this.size.width
            val canvasH = this.size.height
            val centerX = canvasW / 2f

            // Calculate reaction transforms: only non-zero when reacting (zero idle redraw)
            val rVal = reactionProgress.value
            val reactionHop = if (reactionStyle == ReactionStyle.BOUNCE && rVal > 0f) -18f * rVal else 0f
            val personalityHop = when (effectivePersonality) {
                MunchiePersonalityState.HAPPY -> if (rVal > 0f) -12f * rVal else 0f
                MunchiePersonalityState.EXCITED, MunchiePersonalityState.CELEBRATING -> if (rVal > 0f) -20f * rVal else 0f
                else -> 0f
            }
            val centerY = canvasH / 2f + reactionHop + personalityHop

            // Scale transforms
            val reactionScaleX: Float
            val reactionScaleY: Float
            if (rVal > 0f) {
                reactionScaleX = 1f + (rVal * 0.15f) // peaks at 1.15
                reactionScaleY = 1f - (rVal * 0.12f) // peaks at 0.88
            } else {
                reactionScaleX = 1f
                reactionScaleY = 1f
            }

            val personalityScaleY = when (effectivePersonality) {
                MunchiePersonalityState.SURPRISED -> 1.08f
                MunchiePersonalityState.SLEEPY -> 0.96f
                else -> 1f
            }

            val finalScaleX = reactionScaleX
            val finalScaleY = reactionScaleY * personalityScaleY

            // Head tilt & rotation
            val wiggleAngle = if (reactionStyle == ReactionStyle.WINK_AND_HAPPY && rVal > 0f) {
                sin(rVal * Math.PI.toFloat() * 2f) * 12f
            } else 0f

            val personalityTilt = when (effectivePersonality) {
                MunchiePersonalityState.CURIOUS -> -7f
                MunchiePersonalityState.CONCERNED -> 6f
                else -> 0f
            }

            val totalRotation = wiggleAngle + personalityTilt
            val isWinking = (reactionStyle == ReactionStyle.WINK_AND_HAPPY && rVal > 0.15f) ||
                (effectivePersonality == MunchiePersonalityState.CELEBRATING)

            if (totalRotation != 0f) {
                rotate(degrees = totalRotation, pivot = Offset(centerX, centerY)) {
                    drawMunchieCharacter(
                        stage = stage,
                        theme = theme,
                        isPaused = isPaused,
                        personality = effectivePersonality,
                        isWinking = isWinking,
                        centerX = centerX,
                        centerY = centerY,
                        baseRadius = canvasW * 0.38f,
                        extraScaleX = finalScaleX,
                        extraScaleY = finalScaleY
                    )
                }
            } else {
                drawMunchieCharacter(
                    stage = stage,
                    theme = theme,
                    isPaused = isPaused,
                    personality = effectivePersonality,
                    isWinking = isWinking,
                    centerX = centerX,
                    centerY = centerY,
                    baseRadius = canvasW * 0.38f,
                    extraScaleX = finalScaleX,
                    extraScaleY = finalScaleY
                )
            }
        }
    }
}

private fun DrawScope.drawMunchieCharacter(
    stage: MunchieStage,
    theme: MunchieTheme,
    isPaused: Boolean,
    personality: MunchiePersonalityState,
    isWinking: Boolean,
    centerX: Float,
    centerY: Float,
    baseRadius: Float,
    extraScaleX: Float = 1f,
    extraScaleY: Float = 1f
) {
    // Proportions: Body expands progressively from Baby to Megachonk
    val stageScaleX = when (stage) {
        MunchieStage.BABY -> 0.86f
        MunchieStage.CURIOUS -> 0.95f
        MunchieStage.HUNGRY -> 1.05f
        MunchieStage.STUFFED -> 1.18f
        MunchieStage.MEGACHONK -> 1.28f
    }
    val stageScaleY = when (stage) {
        MunchieStage.BABY -> 0.90f
        MunchieStage.CURIOUS -> 0.96f
        MunchieStage.HUNGRY -> 1.00f
        MunchieStage.STUFFED -> 0.96f
        MunchieStage.MEGACHONK -> 0.92f
    }

    val bodyColor = theme.getStageBodyColor(stage)
    val hornColor = theme.hornColor
    val outlineColor = Color(0xFF0F3E2E)
    val strokeW = 4.5f

    val radiusX = baseRadius * stageScaleX * extraScaleX
    val radiusY = baseRadius * stageScaleY * extraScaleY

    // 1. Cute Horns / Antennas
    val hornRadius = when (stage) {
        MunchieStage.BABY -> radiusX * 0.18f
        MunchieStage.CURIOUS -> radiusX * 0.22f
        MunchieStage.HUNGRY -> radiusX * 0.24f
        MunchieStage.STUFFED -> radiusX * 0.22f
        MunchieStage.MEGACHONK -> radiusX * 0.19f
    }

    // Left Horn
    drawCircle(
        color = hornColor,
        radius = hornRadius,
        center = Offset(centerX - radiusX * 0.65f, centerY - radiusY * 0.75f)
    )
    drawCircle(
        color = outlineColor,
        radius = hornRadius,
        center = Offset(centerX - radiusX * 0.65f, centerY - radiusY * 0.75f),
        style = Stroke(width = strokeW)
    )
    // Right Horn
    drawCircle(
        color = hornColor,
        radius = hornRadius,
        center = Offset(centerX + radiusX * 0.65f, centerY - radiusY * 0.75f)
    )
    drawCircle(
        color = outlineColor,
        radius = hornRadius,
        center = Offset(centerX + radiusX * 0.65f, centerY - radiusY * 0.75f),
        style = Stroke(width = strokeW)
    )

    // 2. Stubby Feet
    val footY = centerY + radiusY * 0.85f
    drawOval(
        color = hornColor,
        topLeft = Offset(centerX - radiusX * 0.65f, footY - radiusY * 0.15f),
        size = Size(radiusX * 0.4f, radiusY * 0.35f)
    )
    drawOval(
        color = outlineColor,
        topLeft = Offset(centerX - radiusX * 0.65f, footY - radiusY * 0.15f),
        size = Size(radiusX * 0.4f, radiusY * 0.35f),
        style = Stroke(width = strokeW)
    )
    drawOval(
        color = hornColor,
        topLeft = Offset(centerX + radiusX * 0.25f, footY - radiusY * 0.15f),
        size = Size(radiusX * 0.4f, radiusY * 0.35f)
    )
    drawOval(
        color = outlineColor,
        topLeft = Offset(centerX + radiusX * 0.25f, footY - radiusY * 0.15f),
        size = Size(radiusX * 0.4f, radiusY * 0.35f),
        style = Stroke(width = strokeW)
    )

    // 3. Main Monster Body (Chonky Rounded Ellipse)
    drawOval(
        color = bodyColor,
        topLeft = Offset(centerX - radiusX, centerY - radiusY),
        size = Size(radiusX * 2, radiusY * 2)
    )
    drawOval(
        color = outlineColor,
        topLeft = Offset(centerX - radiusX, centerY - radiusY),
        size = Size(radiusX * 2, radiusY * 2),
        style = Stroke(width = strokeW)
    )

    // 4. Belly Highlight
    drawOval(
        color = Color.White.copy(alpha = 0.28f),
        topLeft = Offset(centerX - radiusX * 0.55f, centerY + radiusY * 0.05f),
        size = Size(radiusX * 1.1f, radiusY * 0.75f)
    )

    // 5. Kawaii Eyes & Facial Expressions
    val eyeOffsetX = radiusX * 0.42f
    val eyeOffsetY = centerY - radiusY * 0.15f
    val eyeRadius = when (stage) {
        MunchieStage.BABY -> radiusX * 0.24f
        MunchieStage.CURIOUS -> radiusX * 0.22f
        MunchieStage.HUNGRY -> radiusX * 0.20f
        MunchieStage.STUFFED -> radiusX * 0.17f
        MunchieStage.MEGACHONK -> radiusX * 0.15f
    }

    if (isPaused || personality == MunchiePersonalityState.RESTING) {
        // Peaceful sleepy closed curved eyes ( ︶   ︶ )
        val sleepEyeWidth = eyeRadius * 1.8f
        val sleepEyeHeight = eyeRadius * 1.0f
        drawArc(
            color = outlineColor,
            startAngle = 10f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(centerX - eyeOffsetX - sleepEyeWidth / 2, eyeOffsetY - sleepEyeHeight / 3),
            size = Size(sleepEyeWidth, sleepEyeHeight),
            style = Stroke(width = strokeW * 1.1f)
        )
        drawArc(
            color = outlineColor,
            startAngle = 10f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(centerX + eyeOffsetX - sleepEyeWidth / 2, eyeOffsetY - sleepEyeHeight / 3),
            size = Size(sleepEyeWidth, sleepEyeHeight),
            style = Stroke(width = strokeW * 1.1f)
        )
    } else if (isWinking || personality == MunchiePersonalityState.CELEBRATING) {
        // Left eye winking (playful arc), right eye sparkling wide awake
        val winkWidth = eyeRadius * 1.8f
        val winkHeight = eyeRadius * 1.0f
        drawArc(
            color = outlineColor,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(centerX - eyeOffsetX - winkWidth / 2, eyeOffsetY - winkHeight / 2),
            size = Size(winkWidth, winkHeight),
            style = Stroke(width = strokeW * 1.2f)
        )
        // Right eye round awake with big sparkle
        drawCircle(
            color = Color(0xFF111827),
            radius = eyeRadius,
            center = Offset(centerX + eyeOffsetX, eyeOffsetY)
        )
        drawCircle(
            color = Color.White,
            radius = eyeRadius * 0.45f,
            center = Offset(centerX + eyeOffsetX - eyeRadius * 0.25f, eyeOffsetY - eyeRadius * 0.25f)
        )
    } else if (personality == MunchiePersonalityState.SURPRISED) {
        // Wide surprised eyes with small pupils and big reflection
        val surprisedRadius = eyeRadius * 1.22f
        drawCircle(color = Color(0xFF111827), radius = surprisedRadius, center = Offset(centerX - eyeOffsetX, eyeOffsetY))
        drawCircle(color = Color(0xFF111827), radius = surprisedRadius, center = Offset(centerX + eyeOffsetX, eyeOffsetY))
        drawCircle(color = Color.White, radius = surprisedRadius * 0.55f, center = Offset(centerX - eyeOffsetX - surprisedRadius * 0.2f, eyeOffsetY - surprisedRadius * 0.2f))
        drawCircle(color = Color.White, radius = surprisedRadius * 0.55f, center = Offset(centerX + eyeOffsetX - surprisedRadius * 0.2f, eyeOffsetY - surprisedRadius * 0.2f))
    } else if (personality == MunchiePersonalityState.SLEEPY || stage == MunchieStage.STUFFED || stage == MunchieStage.MEGACHONK) {
        // Sleepy contented half-lids
        drawCircle(
            color = Color(0xFF111827),
            radius = eyeRadius,
            center = Offset(centerX - eyeOffsetX, eyeOffsetY)
        )
        drawCircle(
            color = Color(0xFF111827),
            radius = eyeRadius,
            center = Offset(centerX + eyeOffsetX, eyeOffsetY)
        )
        // Upper eyelid
        drawArc(
            color = bodyColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(centerX - eyeOffsetX - eyeRadius, eyeOffsetY - eyeRadius),
            size = Size(eyeRadius * 2, eyeRadius * 1.2f)
        )
        drawArc(
            color = bodyColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(centerX + eyeOffsetX - eyeRadius, eyeOffsetY - eyeRadius),
            size = Size(eyeRadius * 2, eyeRadius * 1.2f)
        )
    } else if (personality == MunchiePersonalityState.FOCUSING) {
        // Determined calm focused eyes with horizontal upper lid
        drawCircle(color = Color(0xFF111827), radius = eyeRadius, center = Offset(centerX - eyeOffsetX, eyeOffsetY))
        drawCircle(color = Color(0xFF111827), radius = eyeRadius, center = Offset(centerX + eyeOffsetX, eyeOffsetY))
        drawArc(
            color = bodyColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(centerX - eyeOffsetX - eyeRadius, eyeOffsetY - eyeRadius * 1.05f),
            size = Size(eyeRadius * 2, eyeRadius * 0.9f)
        )
        drawArc(
            color = bodyColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(centerX + eyeOffsetX - eyeRadius, eyeOffsetY - eyeRadius * 1.05f),
            size = Size(eyeRadius * 2, eyeRadius * 0.9f)
        )
        drawCircle(color = Color.White, radius = eyeRadius * 0.35f, center = Offset(centerX - eyeOffsetX - eyeRadius * 0.15f, eyeOffsetY - eyeRadius * 0.15f))
        drawCircle(color = Color.White, radius = eyeRadius * 0.35f, center = Offset(centerX + eyeOffsetX - eyeRadius * 0.15f, eyeOffsetY - eyeRadius * 0.15f))
    } else {
        // Wide awake round eyes (IDLE, HAPPY, EXCITED, CURIOUS, CONCERNED)
        drawCircle(
            color = Color(0xFF111827),
            radius = eyeRadius,
            center = Offset(centerX - eyeOffsetX, eyeOffsetY)
        )
        drawCircle(
            color = Color(0xFF111827),
            radius = eyeRadius,
            center = Offset(centerX + eyeOffsetX, eyeOffsetY)
        )

        val shiftX = if (personality == MunchiePersonalityState.CURIOUS) eyeRadius * 0.2f else -eyeRadius * 0.25f
        val shiftY = if (personality == MunchiePersonalityState.CURIOUS) -eyeRadius * 0.3f else -eyeRadius * 0.25f

        // Eye Sparkles
        val sparkleRadius = eyeRadius * 0.42f
        drawCircle(
            color = Color.White,
            radius = sparkleRadius,
            center = Offset(centerX - eyeOffsetX + shiftX, eyeOffsetY + shiftY)
        )
        drawCircle(
            color = Color.White,
            radius = sparkleRadius,
            center = Offset(centerX + eyeOffsetX + shiftX, eyeOffsetY + shiftY)
        )
        // Secondary sparkle
        drawCircle(
            color = Color.White,
            radius = sparkleRadius * 0.5f,
            center = Offset(centerX - eyeOffsetX - shiftX * 0.8f, eyeOffsetY - shiftY * 0.8f)
        )
        drawCircle(
            color = Color.White,
            radius = sparkleRadius * 0.5f,
            center = Offset(centerX + eyeOffsetX - shiftX * 0.8f, eyeOffsetY - shiftY * 0.8f)
        )
    }

    // 6. Rosy Cheeks
    val blushColor = Color(0xFFF472B6).copy(alpha = 0.45f)
    drawOval(
        color = blushColor,
        topLeft = Offset(centerX - eyeOffsetX - eyeRadius * 1.1f, eyeOffsetY + eyeRadius * 0.65f),
        size = Size(eyeRadius * 1.2f, eyeRadius * 0.65f)
    )
    drawOval(
        color = blushColor,
        topLeft = Offset(centerX + eyeOffsetX - eyeRadius * 0.1f, eyeOffsetY + eyeRadius * 0.65f),
        size = Size(eyeRadius * 1.2f, eyeRadius * 0.65f)
    )

    // 7. Mouth Expression
    val mouthY = centerY + radiusY * 0.22f
    val mouthPath = Path()
    if (isPaused || personality == MunchiePersonalityState.RESTING) {
        // Tiny relaxed sleeping smile
        mouthPath.moveTo(centerX - radiusX * 0.12f, mouthY)
        mouthPath.quadraticTo(centerX, mouthY + radiusY * 0.12f, centerX + radiusX * 0.12f, mouthY)
        drawPath(
            path = mouthPath,
            color = outlineColor,
            style = Stroke(width = strokeW * 0.85f)
        )
    } else if (personality == MunchiePersonalityState.SURPRISED) {
        // Cute small open 'O' mouth
        drawOval(
            color = Color(0xFF111827),
            topLeft = Offset(centerX - radiusX * 0.08f, mouthY - radiusY * 0.05f),
            size = Size(radiusX * 0.16f, radiusY * 0.18f)
        )
    } else if (personality == MunchiePersonalityState.CONCERNED) {
        // Gentle wavy worried mouth
        mouthPath.moveTo(centerX - radiusX * 0.14f, mouthY + radiusY * 0.08f)
        mouthPath.quadraticTo(centerX, mouthY - radiusY * 0.04f, centerX + radiusX * 0.14f, mouthY + radiusY * 0.08f)
        drawPath(
            path = mouthPath,
            color = outlineColor,
            style = Stroke(width = strokeW * 0.85f)
        )
    } else if (personality == MunchiePersonalityState.CURIOUS) {
        // Asymmetric curious smirk
        mouthPath.moveTo(centerX - radiusX * 0.12f, mouthY + radiusY * 0.04f)
        mouthPath.quadraticTo(centerX, mouthY + radiusY * 0.14f, centerX + radiusX * 0.16f, mouthY - radiusY * 0.02f)
        drawPath(
            path = mouthPath,
            color = outlineColor,
            style = Stroke(width = strokeW * 0.85f)
        )
    } else if (personality == MunchiePersonalityState.FOCUSING) {
        // Calm steady straight mouth line
        mouthPath.moveTo(centerX - radiusX * 0.12f, mouthY)
        mouthPath.lineTo(centerX + radiusX * 0.12f, mouthY)
        drawPath(
            path = mouthPath,
            color = outlineColor,
            style = Stroke(width = strokeW * 0.85f)
        )
    } else if (personality == MunchiePersonalityState.EXCITED || personality == MunchiePersonalityState.CELEBRATING || stage == MunchieStage.HUNGRY) {
        // Open chomping mouth with tiny tooth
        mouthPath.moveTo(centerX - radiusX * 0.18f, mouthY - radiusY * 0.05f)
        mouthPath.quadraticTo(centerX, mouthY + radiusY * 0.30f, centerX + radiusX * 0.18f, mouthY - radiusY * 0.05f)
        mouthPath.close()
        drawPath(path = mouthPath, color = Color(0xFFEF4444), style = Fill)
        drawPath(path = mouthPath, color = outlineColor, style = Stroke(width = strokeW * 0.85f))
        // Cute tiny tooth
        val toothPath = Path().apply {
            moveTo(centerX - radiusX * 0.06f, mouthY - radiusY * 0.05f)
            lineTo(centerX - radiusX * 0.06f, mouthY + radiusY * 0.08f)
            lineTo(centerX + radiusX * 0.06f, mouthY - radiusY * 0.05f)
            close()
        }
        drawPath(path = toothPath, color = Color.White, style = Fill)
    } else if (stage == MunchieStage.STUFFED || stage == MunchieStage.MEGACHONK) {
        // Full contented belly smile
        mouthPath.moveTo(centerX - radiusX * 0.22f, mouthY + radiusY * 0.05f)
        mouthPath.quadraticTo(centerX, mouthY + radiusY * 0.22f, centerX + radiusX * 0.22f, mouthY + radiusY * 0.05f)
        drawPath(
            path = mouthPath,
            color = outlineColor,
            style = Stroke(width = strokeW * 0.9f)
        )
    } else {
        // Cheerful smiling curve
        mouthPath.moveTo(centerX - radiusX * 0.15f, mouthY)
        mouthPath.quadraticTo(centerX, mouthY + radiusY * 0.22f, centerX + radiusX * 0.15f, mouthY)
        drawPath(
            path = mouthPath,
            color = outlineColor,
            style = Stroke(width = strokeW * 0.85f)
        )
    }
}
