package com.reelmonster.app.domain.model

/**
 * DetectionState
 *
 * Finite state machine states representing the real-time detection lifecycle
 * of short-form video viewers (Instagram Reels and YouTube Shorts).
 */
enum class DetectionState(val label: String, val isActive: Boolean) {
    /** Outside monitored target applications or screen is off */
    IDLE("Idle", false),

    /** Monitored app (Instagram/YouTube) is in foreground, but user is not in the full-screen reel viewer */
    TARGET_APP_ACTIVE("Target App Active (Feed/Profile)", false),

    /** Full-screen immersive Reels/Shorts viewer is identified and actively displayed */
    REEL_VIEWER_ACTIVE("Reel Viewer Active", true),

    /** Vertical scroll or content change detected; awaiting dwell time floor & stabilization */
    CONTENT_TRANSITION_PENDING("Content Transition Pending", true),

    /** New distinct Reel/Short verified, deduplicated, and counter incremented */
    NEW_CONTENT_CONFIRMED("New Content Confirmed (+1)", true),

    /** Reel Monster tracking is paused by user preference */
    PAUSED("Tracking Paused", false),

    /** Monitored application UI cannot be parsed into a known viewer layout */
    UNSUPPORTED_UI("Unsupported Layout", false);

    companion object {
        fun fromName(name: String): DetectionState {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: IDLE
        }
    }
}
