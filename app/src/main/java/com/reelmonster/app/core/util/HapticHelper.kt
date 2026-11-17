package com.reelmonster.app.core.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View

/**
 * HapticHelper
 *
 * Lightweight, hardware-guarded tactile and audio feedback helper.
 * - Adheres strictly to user settings: does nothing when disabled.
 * - Uses modern VibrationEffect.EFFECT_TICK and EFFECT_HEAVY_CLICK on API 29+.
 * - Provides graceful fallbacks across all Android API versions.
 * - Handles missing hardware or security restrictions safely.
 */
object HapticHelper {

    /**
     * Subtle tick vibration on each successful +1 Reel increment.
     */
    fun performCountTick(context: Context, isHapticEnabled: Boolean, view: View? = null) {
        if (!isHapticEnabled) return

        // Prefer View haptic if view is available
        if (view != null && view.isAttachedToWindow) {
            val performed = view.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
            if (performed) return
        }

        performSystemVibration(context, isHeavy = false)
    }

    /**
     * Substantial tactile buzz when Munchie evolves to a higher stage.
     */
    fun performStageEvolution(context: Context, isHapticEnabled: Boolean, view: View? = null) {
        if (!isHapticEnabled) return

        if (view != null && view.isAttachedToWindow) {
            val performed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(
                    HapticFeedbackConstants.CONFIRM,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                )
            } else {
                view.performHapticFeedback(
                    HapticFeedbackConstants.LONG_PRESS,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                )
            }
            if (performed) return
        }

        performSystemVibration(context, isHeavy = true)
    }

    /**
     * Plays optional stage-up celebration sound.
     */
    fun playStageUpSound(isSoundEnabled: Boolean, view: View? = null) {
        if (!isSoundEnabled) return
        try {
            if (view != null && view.isAttachedToWindow) {
                view.playSoundEffect(SoundEffectConstants.CLICK)
            } else {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
            }
        } catch (_: Exception) {
            // Audio hardware or permission restriction
        }
    }

    private fun performSystemVibration(context: Context, isHeavy: Boolean) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effectId = if (isHeavy) {
                    VibrationEffect.EFFECT_HEAVY_CLICK
                } else {
                    VibrationEffect.EFFECT_TICK
                }
                vibrator.vibrate(VibrationEffect.createPredefined(effectId))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val duration = if (isHeavy) 90L else 25L
                val amplitude = if (isHeavy) 255 else 120
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(if (isHeavy) 90L else 25L)
            }
        } catch (_: Exception) {
            // Safeguard against missing permission or hardware failure
        }
    }
}
