package com.reelmonster.app.domain.model

/**
 * OverlayState
 *
 * Represents the distinct display and operational states of the floating monster overlay.
 */
enum class OverlayState {
    /**
     * Overlay is dismissed or not visible on screen.
     */
    HIDDEN,

    /**
     * Floating compact Munchie bubble docked on screen edge with count badge.
     */
    COMPACT,

    /**
     * Expanded mini-HUD card with larger Munchie, quote, +1 TAP ME button, and controls.
     */
    EXPANDED,

    /**
     * Tracking is temporarily paused by the user. Shows pause badge and blocks counting.
     */
    PAUSED,

    /**
     * SYSTEM_ALERT_WINDOW permission is not granted in Android Settings.
     */
    PERMISSION_MISSING,

    /**
     * Neither Instagram nor YouTube is monitored, or tracking service is disabled.
     */
    TRACKING_UNAVAILABLE;

    val isVisible: Boolean
        get() = this == COMPACT || this == EXPANDED || this == PAUSED

    val isInteractive: Boolean
        get() = this == COMPACT || this == EXPANDED
}
