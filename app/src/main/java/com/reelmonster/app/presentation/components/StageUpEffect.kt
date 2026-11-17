package com.reelmonster.app.presentation.components

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.reelmonster.app.core.design.MintPrimaryBright
import com.reelmonster.app.core.util.HapticHelper
import com.reelmonster.app.domain.model.MunchieStage

/**
 * RadialFlashEffect
 *
 * Lightweight expanding radial gradient flash displayed when Munchie evolves.
 * - Expands radially and fades out over 450ms.
 * - Releases memory upon completion; zero overhead when idle.
 */
@Composable
fun RadialFlashEffect(
    stage: MunchieStage,
    isHapticEnabled: Boolean = false,
    isSoundEnabled: Boolean = false,
    flashColor: Color = MintPrimaryBright,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    var lastStage by remember { mutableStateOf(stage) }
    var isFlashing by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(stage) {
        if (stage.level > lastStage.level) {
            lastStage = stage
            isFlashing = true

            // Trigger Stage-Up Haptic and Sound
            HapticHelper.performStageEvolution(context, isHapticEnabled, view)
            HapticHelper.playStageUpSound(isSoundEnabled, view)

            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(450, easing = FastOutSlowInEasing)
            )
            isFlashing = false
        } else {
            lastStage = stage
        }
    }

    if (isFlashing && progress.value < 1f) {
        val p = progress.value
        val alpha = (1f - p) * 0.75f
        Canvas(modifier = modifier.fillMaxSize()) {
            val maxRadius = size.maxDimension * (0.4f + p * 0.6f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        flashColor.copy(alpha = alpha),
                        flashColor.copy(alpha = alpha * 0.4f),
                        Color.Transparent
                    ),
                    radius = maxRadius.coerceAtLeast(1f)
                ),
                radius = maxRadius
            )
        }
    }
}
