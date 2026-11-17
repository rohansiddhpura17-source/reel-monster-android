package com.reelmonster.app.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Modifier.tilt3D
 *
 * Implements subtle 3D perspective tilt on pointer touch / interaction.
 * Uses cameraDistance = 12f * density and spring return to zero upon release.
 * Completely idle with zero CPU / matrix recalculation when not interacting.
 */
fun Modifier.tilt3D(
    enabled: Boolean = true,
    maxTiltDegrees: Float = 8f
): Modifier = composed {
    if (!enabled) return@composed this

    var targetRotX by remember { mutableFloatStateOf(0f) }
    var targetRotY by remember { mutableFloatStateOf(0f) }

    val rotX by animateFloatAsState(
        targetValue = targetRotX,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessLow
        ),
        label = "tilt_rot_x"
    )

    val rotY by animateFloatAsState(
        targetValue = targetRotY,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessLow
        ),
        label = "tilt_rot_y"
    )

    this
        .pointerInput(enabled) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val width = size.width.toFloat()
                val height = size.height.toFloat()

                if (width > 0f && height > 0f) {
                    val normX = ((down.position.x - width / 2f) / (width / 2f)).coerceIn(-1f, 1f)
                    val normY = ((down.position.y - height / 2f) / (height / 2f)).coerceIn(-1f, 1f)
                    targetRotX = -normY * maxTiltDegrees
                    targetRotY = normX * maxTiltDegrees
                }

                // Track until up or cancel
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull()
                    if (change != null && change.pressed && width > 0f && height > 0f) {
                        val normX = ((change.position.x - width / 2f) / (width / 2f)).coerceIn(-1f, 1f)
                        val normY = ((change.position.y - height / 2f) / (height / 2f)).coerceIn(-1f, 1f)
                        targetRotX = -normY * maxTiltDegrees
                        targetRotY = normX * maxTiltDegrees
                    }
                } while (event.changes.any { it.pressed })

                // Released: Spring back to absolute 0
                targetRotX = 0f
                targetRotY = 0f
            }
        }
        .graphicsLayer {
            if (rotX != 0f || rotY != 0f) {
                rotationX = rotX
                rotationY = rotY
                cameraDistance = 12f * density
            }
        }
}
