package com.reelmonster.app.domain.model

/**
 * MunchiePersonalityState
 *
 * The 10 canonical personality and emotional states for Munchie the Reel Monster (Phase 10).
 * Each state defines an inherent priority level used to resolve simultaneous conflicting events.
 */
enum class MunchiePersonalityState(
    val priority: Int,
    val displayName: String,
    val description: String
) {
    /**
     * Priority 10: Ecstatic celebration of milestones, stage evolution, or user celebration.
     */
    CELEBRATING(
        priority = 10,
        displayName = "Celebrating",
        description = "Joyful celebration of milestones, new evolution stages, and streaks"
    ),

    /**
     * Priority 9: High-energy dopamine burst, rapid chomping, or excited growth.
     */
    EXCITED(
        priority = 9,
        displayName = "Excited",
        description = "High energy dopamine rush and growth anticipation"
    ),

    /**
     * Priority 8: Gentle concern triggered by long continuous viewing sessions or heavy binge pacing.
     */
    CONCERNED(
        priority = 8,
        displayName = "Concerned",
        description = "Compassionate mindfulness nudge over long continuous screen sessions"
    ),

    /**
     * Priority 7: Peaceful rest when tracking is paused, global tracking is disabled, or screen is off.
     */
    RESTING(
        priority = 7,
        displayName = "Resting",
        description = "Peaceful slumber during tracking pause or global disabled state"
    ),

    /**
     * Priority 6: Calm, zen focus during mindful sessions or steady breathing.
     */
    FOCUSING(
        priority = 6,
        displayName = "Focusing",
        description = "Calm, centered mindfulness and steady breathing posture"
    ),

    /**
     * Priority 5: Playful surprise from sudden user interaction or unexpected events.
     */
    SURPRISED(
        priority = 5,
        displayName = "Surprised",
        description = "Playful wide-eyed surprise from sudden taps or rapid interactions"
    ),

    /**
     * Priority 4: Inquisitive state when exploring the dashboard, stats, or gentle user taps.
     */
    CURIOUS(
        priority = 4,
        displayName = "Curious",
        description = "Inquisitive head-tilt exploration of dashboard metrics and trends"
    ),

    /**
     * Priority 3: Cheerful companion state after tracking a reel or resuming monitoring.
     */
    HAPPY(
        priority = 3,
        displayName = "Happy",
        description = "Warm smiling companion enjoying short-form video companionship"
    ),

    /**
     * Priority 2: Drowsy state after extended inactivity or high overfed capacity.
     */
    SLEEPY(
        priority = 2,
        displayName = "Sleepy",
        description = "Drowsy relaxation after long screen sessions or late hours"
    ),

    /**
     * Priority 1 (Lowest): Default ambient companion state.
     */
    IDLE(
        priority = 1,
        displayName = "Idle",
        description = "Default calm resting and subtle breathing companion state"
    );

    companion object {
        fun fromName(name: String?): MunchiePersonalityState {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: IDLE
        }
    }
}
