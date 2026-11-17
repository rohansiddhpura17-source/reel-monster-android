package com.reelmonster.app.domain.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MunchiePersonalityEngine
 *
 * Core personality, emotion, dialogue, and interaction engine for Munchie the Reel Monster (Phase 10).
 *
 * Key Architectural Features:
 * - Deterministic, priority-driven state machine managing 10 canonical personality states.
 * - Context-aware, offline dialogue generation preventing consecutive message repetition.
 * - Synchronized StateFlow streams shared identically between Dashboard and Floating Overlay.
 * - Interaction handling for tap and long-press gestures (guaranteed zero count incrementation).
 * - Configurable clock lambda for deterministic, non-blocking unit testing.
 */
class MunchiePersonalityEngine(
    var clock: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        /** Default duration in ms for temporary emotional reactions before returning to ambient state. */
        const val REACTION_DURATION_MS = 3_000L

        /** Threshold in ms (10 minutes) for continuous active viewing before triggering CONCERNED state. */
        const val LONG_SESSION_THRESHOLD_MS = 600_000L

        /** Authoritative singleton instance shared across app components. */
        val instance by lazy { MunchiePersonalityEngine() }

        // Contextual Dialogue Banks (offline & local)
        val STATE_MESSAGES: Map<MunchiePersonalityState, List<String>> = mapOf(
            MunchiePersonalityState.IDLE to listOf(
                "Just chilling and watching the world go by. 🌱",
                "Standing by for some mindful scrolling! ✨",
                "Calm, rested, and ready when you are. 🧘",
                "Zero stress, maximum monster zen. 🍃",
                "I'm in my peaceful floating zone. ☁️"
            ),
            MunchiePersonalityState.HAPPY to listOf(
                "We caught another one! 🎬",
                "That makes us a team! 🤝",
                "One more for the collection! 🍿",
                "Down the rabbit hole we go! 🚀",
                "Munchie is awake and ready for chomping! 👀"
            ),
            MunchiePersonalityState.EXCITED to listOf(
                "LOOK AT ME GROW! 🌟",
                "I'm getting HUGE! 🍩",
                "This is getting interesting! ⚡",
                "New evolution unlocked! Check my form! 🐾",
                "My horns are tingling with dopamine! 💥"
            ),
            MunchiePersonalityState.SLEEPY to listOf(
                "Are we still scrolling? 🥱",
                "My eyes need a vacation. 💤",
                "Can we blink for a minute? 🛌",
                "Drowsy monster mode loading... 🌙",
                "Even algorithms need sleep... right? 😴"
            ),
            MunchiePersonalityState.CURIOUS to listOf(
                "What are we checking out here? 🔍",
                "Ooh, let me see what's on screen! 👀",
                "Analyzing your scroll velocity... interesting! 📊",
                "Is that a new creator trend? Tell me more! 📱",
                "Head tilt activated! Show me the stats! 🧐"
            ),
            MunchiePersonalityState.SURPRISED to listOf(
                "Whoa! That was unexpected! 😲",
                "Hey! You tickled my horn! ✨",
                "Did you just tap me?! 👾",
                "Blink and you miss it! 👀",
                "Surprise chomp! I'm wide awake now! ⚡"
            ),
            MunchiePersonalityState.CONCERNED to listOf(
                "That was a long one... maybe take a tiny break? 🛑",
                "Your thumb deserves a holiday. 🏖️",
                "Continuous session alert! Go touch some grass! 🌳",
                "Time flies when dopamine flows... take a stretch! 🧘",
                "I care about your sleep schedule, friend. 💤"
            ),
            MunchiePersonalityState.CELEBRATING to listOf(
                "NEW RECORD! LET'S GOOO! 🎉",
                "Look how far we've grown together! 🏆",
                "Achievement unlocked! High five! 🌟",
                "You're doing amazing! Munchie celebration! 🥳",
                "Confetti cannon activated! 🎊"
            ),
            MunchiePersonalityState.FOCUSING to listOf(
                "Deep breath in... calm breath out. 🧘",
                "Focus mode: locked in and mindful. 🎯",
                "Zero distractions, pure intention. 🌿",
                "Centering ourselves. You've got this. ✨",
                "Mindful boundary in progress. 🛡️"
            ),
            MunchiePersonalityState.RESTING to listOf(
                "Taking a little break. 💤",
                "I'll be right here when you return. 🛌",
                "Rest mode activated. Sweet dreams! 🌙",
                "Munchie is sleeping 💤 Tracking is paused.",
                "All quiet on the monster front. 😴"
            )
        )
    }

    private val _currentState = MutableStateFlow(MunchiePersonalityState.IDLE)
    val currentState: StateFlow<MunchiePersonalityState> = _currentState.asStateFlow()

    private val _currentMessage = MutableStateFlow(STATE_MESSAGES[MunchiePersonalityState.IDLE]!!.first())
    val currentMessage: StateFlow<String> = _currentMessage.asStateFlow()

    private val _reactionTrigger = MutableStateFlow(0)
    val reactionTrigger: StateFlow<Int> = _reactionTrigger.asStateFlow()

    // Temporary reaction state tracking
    private var temporaryState: MunchiePersonalityState? = null
    private var temporaryStateExpiresAt: Long = 0L

    // Tracks last message index per state to prevent consecutive repeats
    private val lastSelectedIndices = mutableMapOf<MunchiePersonalityState, Int>()

    /**
     * Resolves and updates the current personality state.
     * Enforces the priority system: higher priority states override lower ones.
     *
     * @param targetState The proposed personality state.
     * @param isTemporary If true, state reverts back to baseline after REACTION_DURATION_MS.
     * @param durationMs Duration for temporary state.
     * @param customMessage Optional specific dialogue string (if null, selected from bank).
     * @return true if state was updated; false if rejected due to lower priority.
     */
    @Synchronized
    fun transitionTo(
        targetState: MunchiePersonalityState,
        isTemporary: Boolean = false,
        durationMs: Long = REACTION_DURATION_MS,
        customMessage: String? = null,
        force: Boolean = false
    ): Boolean {
        val now = clock()

        // Clear expired temporary state
        if (temporaryState != null && now >= temporaryStateExpiresAt) {
            temporaryState = null
            temporaryStateExpiresAt = 0L
        }

        // Check if existing temporary state is active and of higher priority
        if (!force && temporaryState != null && now < temporaryStateExpiresAt) {
            val activeTemp = temporaryState!!
            if (targetState.priority < activeTemp.priority) {
                // Rejected due to lower priority
                return false
            }
        }

        // Apply new state
        if (isTemporary) {
            temporaryState = targetState
            temporaryStateExpiresAt = now + durationMs
        } else {
            temporaryState = null
            temporaryStateExpiresAt = 0L
        }

        _currentState.value = targetState
        val msg = customMessage ?: selectNextMessage(targetState)
        _currentMessage.value = msg
        _reactionTrigger.value += 1
        return true
    }

    /**
     * Checks if a temporary reaction has expired and safely reverts to the ambient state.
     */
    @Synchronized
    fun checkExpiration(ambientState: MunchiePersonalityState): MunchiePersonalityState {
        val now = clock()
        if (temporaryState != null && now >= temporaryStateExpiresAt) {
            temporaryState = null
            temporaryStateExpiresAt = 0L
            _currentState.value = ambientState
            _currentMessage.value = selectNextMessage(ambientState)
        }
        return _currentState.value
    }

    /**
     * Selects a dialogue quote for the given state, strictly avoiding consecutive repeats.
     */
    @Synchronized
    fun selectNextMessage(state: MunchiePersonalityState): String {
        val list = STATE_MESSAGES[state] ?: listOf("Munchie is here! ✨")
        if (list.size <= 1) {
            return list.first()
        }

        val lastIndex = lastSelectedIndices[state] ?: -1
        val nextIndex = (lastIndex + 1) % list.size
        lastSelectedIndices[state] = nextIndex
        return list[nextIndex]
    }

    /**
     * Resets internal dialogue tracking and returns to initial state.
     */
    @Synchronized
    fun reset() {
        temporaryState = null
        temporaryStateExpiresAt = 0L
        lastSelectedIndices.clear()
        _currentState.value = MunchiePersonalityState.IDLE
        _currentMessage.value = STATE_MESSAGES[MunchiePersonalityState.IDLE]!!.first()
    }

    // =========================================================================
    // Standard Event Handlers (Called by ReelRepository & UI)
    // =========================================================================

    /**
     * Handles Reel logged/detected event.
     */
    @Synchronized
    fun onReelLogged(newCount: Int, oldCount: Int, isFirstReelToday: Boolean, stageEvolutionOccurred: Boolean) {
        if (stageEvolutionOccurred) {
            // Stage evolution triggers EXCITED (Priority 9)
            transitionTo(MunchiePersonalityState.EXCITED, isTemporary = true)
        } else if (isFirstReelToday || newCount == 1) {
            // First reel of day triggers HAPPY (Priority 3)
            transitionTo(MunchiePersonalityState.HAPPY, isTemporary = true)
        } else {
            // Consecutive reel: alternate between HAPPY or EXCITED depending on count
            if (newCount >= MunchieStage.STUFFED.minReels) {
                transitionTo(MunchiePersonalityState.SLEEPY, isTemporary = true)
            } else {
                transitionTo(MunchiePersonalityState.HAPPY, isTemporary = true)
            }
        }
    }

    /**
     * Handles continuous viewing session exceeding threshold (triggers CONCERNED).
     */
    @Synchronized
    fun onContinuousSessionThresholdReached(durationMs: Long) {
        if (durationMs >= LONG_SESSION_THRESHOLD_MS) {
            transitionTo(MunchiePersonalityState.CONCERNED, isTemporary = true, durationMs = 5_000L)
        }
    }

    /**
     * Handles tracking paused state (triggers RESTING).
     */
    @Synchronized
    fun onTrackingPaused() {
        transitionTo(MunchiePersonalityState.RESTING, isTemporary = false, force = true)
    }

    /**
     * Handles tracking resumed state (triggers HAPPY).
     */
    @Synchronized
    fun onTrackingResumed() {
        transitionTo(MunchiePersonalityState.HAPPY, isTemporary = true, force = true)
    }

    /**
     * Handles global tracking switch toggled OFF (triggers RESTING).
     */
    @Synchronized
    fun onGlobalTrackingDisabled() {
        transitionTo(
            targetState = MunchiePersonalityState.RESTING,
            isTemporary = false,
            customMessage = "Munchie is resting calmly. Global tracking is turned OFF.",
            force = true
        )
    }

    /**
     * Handles user tapping Munchie on dashboard or overlay.
     * Guaranteed: Playful reaction, zero count incrementation.
     */
    @Synchronized
    fun onMascotTapped(): MunchiePersonalityState {
        // Tapping alternates between CURIOUS and SURPRISED
        val target = if (_currentState.value == MunchiePersonalityState.CURIOUS) {
            MunchiePersonalityState.SURPRISED
        } else {
            MunchiePersonalityState.CURIOUS
        }
        transitionTo(target, isTemporary = true)
        return target
    }

    /**
     * Handles reaching 50% of the daily limit (Pacing milestone).
     */
    @Synchronized
    fun onGoalPacingMilestone(usagePercent: Int) {
        if (usagePercent in 48..55) {
            transitionTo(
                targetState = MunchiePersonalityState.HAPPY,
                isTemporary = true,
                durationMs = 4000L,
                customMessage = "Halfway to today's limit! Pacing nicely! 🎯"
            )
        }
    }

    /**
     * Handles approaching the configured limit (70% - 89%).
     */
    @Synchronized
    fun onGoalApproachingLimit(remainingCountOrMins: Int, unitLabel: String) {
        transitionTo(
            targetState = MunchiePersonalityState.CURIOUS,
            isTemporary = true,
            durationMs = 4000L,
            customMessage = "Getting close to today's boundary! Only $remainingCountOrMins $unitLabel left. ⏱️"
        )
    }

    /**
     * Handles reaching the daily limit (100%).
     * Note: Clearly an alert boundary, NOT an achievement celebration.
     */
    @Synchronized
    fun onGoalLimitReached() {
        transitionTo(
            targetState = MunchiePersonalityState.CONCERNED,
            isTemporary = true,
            durationMs = 5000L,
            customMessage = "Today's goal limit has been reached! Time to take a mindful break ⏸️"
        )
    }

    /**
     * Handles exceeding the daily limit (> 100%).
     * Compassionate, non-shaming response.
     */
    @Synchronized
    fun onGoalExceeded(overBy: Int, unitLabel: String) {
        transitionTo(
            targetState = MunchiePersonalityState.CONCERNED,
            isTemporary = true,
            durationMs = 5000L,
            customMessage = "We've passed today's limit by $overBy $unitLabel. Munchie suggests stepping away for a bit 🌿"
        )
    }

    /**
     * Handles successfully completing a past day within configured boundaries.
     * Celebrates actual mindful moderation!
     */
    @Synchronized
    fun onGoalDayCompletedSuccessfully() {
        transitionTo(
            targetState = MunchiePersonalityState.CELEBRATING,
            isTemporary = true,
            durationMs = 4000L,
            customMessage = "Mindful scrolling champion! Stayed within your goal boundary! 🎉"
        )
    }

    /**
     * Handles user long-pressing Munchie on dashboard or overlay.
     * Guaranteed: Celebration reaction, zero count incrementation.
     */
    @Synchronized
    fun onMascotLongPressed(): MunchiePersonalityState {
        transitionTo(MunchiePersonalityState.CELEBRATING, isTemporary = true, durationMs = 4_000L)
        return MunchiePersonalityState.CELEBRATING
    }

    /**
     * Handles dashboard screen opened.
     */
    @Synchronized
    fun onDashboardOpened(isPaused: Boolean, isGlobalEnabled: Boolean, todayCount: Int) {
        if (!isGlobalEnabled || isPaused) {
            transitionTo(MunchiePersonalityState.RESTING, isTemporary = false)
        } else if (_currentState.value == MunchiePersonalityState.IDLE) {
            transitionTo(MunchiePersonalityState.CURIOUS, isTemporary = true)
        }
    }

    // =========================================================================
    // Phase 13: Focus Mode Reactions
    // =========================================================================

    @Synchronized
    fun onFocusStarted(type: com.reelmonster.app.domain.model.focus.FocusType) {
        transitionTo(
            targetState = MunchiePersonalityState.FOCUSING,
            isTemporary = false,
            customMessage = "Locked in for ${type.label}! Zero distractions, pure intention 🎯"
        )
    }

    @Synchronized
    fun onFocusPaused() {
        transitionTo(
            targetState = MunchiePersonalityState.RESTING,
            isTemporary = false,
            customMessage = "Focus paused. Take a deep breath while the timer rests ⏸️"
        )
    }

    @Synchronized
    fun onFocusResumed() {
        transitionTo(
            targetState = MunchiePersonalityState.FOCUSING,
            isTemporary = false,
            customMessage = "Back in the zone! Let's finish this focus session strong 🎯"
        )
    }

    @Synchronized
    fun onFocusCompleted(actualDurationMs: Long) {
        val mins = actualDurationMs / 60_000L
        transitionTo(
            targetState = MunchiePersonalityState.CELEBRATING,
            isTemporary = true,
            durationMs = 5000L,
            customMessage = "Focus session crushed ($mins mins)! Outstanding discipline! 🎉"
        )
    }

    @Synchronized
    fun onFocusIntervention() {
        transitionTo(
            targetState = MunchiePersonalityState.CURIOUS,
            isTemporary = true,
            durationMs = 4000L,
            customMessage = "Mindful check-in: You're in focus mode! Want to stay in your flow? 🎯"
        )
    }

    // =========================================================================
    // Phase 14: XP, Streaks & Achievements Reactions
    // =========================================================================

    @Synchronized
    fun onXpEarned(xpAmount: Int, reason: String) {
        transitionTo(
            targetState = MunchiePersonalityState.HAPPY,
            isTemporary = true,
            durationMs = REACTION_DURATION_MS,
            customMessage = "+$xpAmount XP! $reason ✨"
        )
    }

    @Synchronized
    fun onAchievementUnlocked(title: String) {
        transitionTo(
            targetState = MunchiePersonalityState.EXCITED,
            isTemporary = true,
            durationMs = 4_000L,
            customMessage = "Achievement Unlocked: $title! 🌟"
        )
    }

    @Synchronized
    fun onLevelUp(newLevel: Int, levelTitle: String) {
        transitionTo(
            targetState = MunchiePersonalityState.CELEBRATING,
            isTemporary = true,
            durationMs = 5_000L,
            customMessage = "LEVEL UP! Level $newLevel: $levelTitle! 🎉"
        )
    }

    @Synchronized
    fun onStreakMilestoneReached(streakDays: Int) {
        transitionTo(
            targetState = MunchiePersonalityState.CELEBRATING,
            isTemporary = true,
            durationMs = 5_000L,
            customMessage = "$streakDays-Day Mindful Streak Milestone! Incredible consistency! 🔥"
        )
    }
}
