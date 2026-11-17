package com.reelmonster.app.domain.model.goal

import java.util.TimeZone

/**
 * Types of immutable events recorded in the append-only daily_goal_history table.
 */
enum class GoalHistoryEventType {
    CREATED,
    ENABLED,
    DISABLED,
    TARGET_CHANGED,
    SCOPE_CHANGED;

    companion object {
        fun fromName(name: String?): GoalHistoryEventType {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: TARGET_CHANGED
        }
    }
}

/**
 * Immutable, append-only timestamped record of a daily goal lifecycle change.
 */
data class DailyGoalHistoryEvent(
    val id: Long = 0L,
    val eventId: String,
    val goalId: Long,
    val goalType: GoalType,
    val platformScope: GoalScope,
    val eventType: GoalHistoryEventType,
    val previousValue: String?,
    val newValue: String,
    val effectiveTimestamp: Long,
    val dateKey: String,
    val timezoneId: String = TimeZone.getDefault().id,
    val createdAt: Long = effectiveTimestamp
)

/**
 * Snapshot of a daily goal's state at midnight and its timestamped evolution across a calendar day.
 *
 * Rules:
 * - Goals active at local midnight (enabledAtMidnight == true) and not disabled during the day
 *   are progressionEligible == true.
 * - Goals first created or enabled after local midnight (enabledAtMidnight == false) are active
 *   for live feedback (usageSinceActivation), but progressionEligible == false until the next complete day.
 * - Disabling an active goal mid-day keeps it in the day's violation evaluation (cannot erase a violation),
 *   and re-enabling it mid-day does not restore same-day progression eligibility.
 * - Strictest target in effect during the day is used for evaluation; neither target increases nor
 *   target reductions can erase an already-recorded violation.
 */
data class DailyGoalSnapshot(
    val id: Long = 0L,
    val dateKey: String,
    val goalId: Long,
    val goalType: GoalType,
    val platformScope: GoalScope,
    val enabledAtMidnight: Boolean,
    val targetAtMidnight: Int?,
    val strictestTarget: Int,
    val firstActivationTimestamp: Long?,
    val wasDisabledDuringDay: Boolean = false,
    val wasReenabledMidday: Boolean = false,
    val wasViolated: Boolean = false,
    val progressionEligible: Boolean = enabledAtMidnight && !wasDisabledDuringDay,
    val usageSinceMidnight: Int = 0,
    val usageSinceActivation: Int = 0,
    val lastHistoryEventId: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Whether this goal is part of the day's violation check.
     * Any goal active at midnight remains part of that day's evaluation even if later disabled.
     */
    val isEvaluatedForViolation: Boolean
        get() = enabledAtMidnight

    /**
     * Whether this goal passed its strictest boundary without violation and is eligible for progression rewards.
     */
    val isPassedAndProgressionEligible: Boolean
        get() = progressionEligible && !wasViolated && usageSinceMidnight <= strictestTarget
}

/**
 * Dynamic mindful-day thresholds across all scopes, capped at 30.
 */
data class MindfulThresholds(
    val maxCombinedReels: Int = 30,
    val maxInstagramReels: Int = 30,
    val maxYoutubeReels: Int = 30,
    val maxCombinedMinutes: Int = 30,
    val maxInstagramMinutes: Int = 30,
    val maxYoutubeMinutes: Int = 30
)

/**
 * Recorded usage metrics for a calendar day across platforms.
 */
data class DailyUsageMetrics(
    val totalReels: Int = 0,
    val instagramReels: Int = 0,
    val youtubeReels: Int = 0,
    val totalViewingMs: Long = 0L,
    val instagramViewingMs: Long = 0L,
    val youtubeViewingMs: Long = 0L
) {
    val totalViewingMinutes: Int
        get() = (totalViewingMs / 60_000L).toInt()

    val instagramViewingMinutes: Int
        get() = (instagramViewingMs / 60_000L).toInt()

    val youtubeViewingMinutes: Int
        get() = (youtubeViewingMs / 60_000L).toInt()

    val isZeroViewing: Boolean
        get() = totalReels == 0 && totalViewingMs == 0L

    fun usageFor(type: GoalType, scope: GoalScope): Int {
        return when (type) {
            GoalType.REEL_COUNT -> when (scope) {
                GoalScope.COMBINED -> totalReels
                GoalScope.INSTAGRAM -> instagramReels
                GoalScope.YOUTUBE -> youtubeReels
            }
            GoalType.VIEWING_TIME_MINUTES -> when (scope) {
                GoalScope.COMBINED -> totalViewingMinutes
                GoalScope.INSTAGRAM -> instagramViewingMinutes
                GoalScope.YOUTUBE -> youtubeViewingMinutes
            }
        }
    }

    fun isWithinMindfulThresholds(thresholds: MindfulThresholds): Boolean {
        return totalReels <= thresholds.maxCombinedReels &&
            instagramReels <= thresholds.maxInstagramReels &&
            youtubeReels <= thresholds.maxYoutubeReels &&
            totalViewingMinutes <= thresholds.maxCombinedMinutes &&
            instagramViewingMinutes <= thresholds.maxInstagramMinutes &&
            youtubeViewingMinutes <= thresholds.maxYoutubeMinutes
    }
}

/**
 * Result of evaluating daily goal snapshots for a finalized day.
 */
data class GoalEvaluationSummary(
    val anyActiveGoalViolated: Boolean,
    val countGoalTypeEligibleAndPassed: Boolean,
    val timeGoalTypeEligibleAndPassed: Boolean,
    val bothGoalTypesEligibleAndPassed: Boolean,
    val hasAnyProgressionEligibleGoal: Boolean,
    val hasOnlyMiddayActivatedGoals: Boolean,
    val mindfulThresholds: MindfulThresholds
)

/**
 * Deterministic evaluator for timestamped goal history and daily goal snapshots.
 */
object GoalEligibilityEvaluator {
    const val DEFAULT_MINDFUL_CAP = 30

    /**
     * Reconstructs or updates a DailyGoalSnapshot from its midnight baseline, timestamped history events,
     * and timestamped usage events on [dateKey].
     *
     * @param dateKey Local date key ('YYYY-MM-DD').
     * @param dayStartMidnightMs Epoch timestamp of 00:00:00.000 local time on [dateKey].
     * @param goalId Database ID of the goal.
     * @param goalType GoalType (REEL_COUNT or VIEWING_TIME_MINUTES).
     * @param platformScope GoalScope (COMBINED, INSTAGRAM, YOUTUBE).
     * @param events Chronological history events for this goal up to the end of [dateKey].
     * @param timestampedUsage Checkpoints of (timestampMs, cumulativeUsageSinceMidnight) on [dateKey].
     * @param finalFullDayUsage Total usage for the entire [dateKey].
     */
    fun buildSnapshotFromHistory(
        dateKey: String,
        dayStartMidnightMs: Long,
        goalId: Long,
        goalType: GoalType,
        platformScope: GoalScope,
        events: List<DailyGoalHistoryEvent>,
        timestampedUsage: List<Pair<Long, Int>> = emptyList(),
        finalFullDayUsage: Int = 0
    ): DailyGoalSnapshot? {
        val sorted = events.sortedWith(compareBy<DailyGoalHistoryEvent> { it.effectiveTimestamp }.thenBy { it.id })
        if (sorted.isEmpty()) return null

        val beforeOrAtMidnight = sorted.filter { it.effectiveTimestamp <= dayStartMidnightMs }
        val duringDay = sorted.filter { it.effectiveTimestamp > dayStartMidnightMs && it.dateKey == dateKey }

        // Determine state at midnight
        var enabledAtMidnight = false
        var targetAtMidnight: Int? = null
        var firstActivationTimestamp: Long? = null

        for (ev in beforeOrAtMidnight) {
            when (ev.eventType) {
                GoalHistoryEventType.CREATED -> {
                    // Format of newValue can be "target:enabled" (e.g. "30:true") or plain int target
                    val parsed = parseTargetAndEnabled(ev.newValue, defaultEnabled = true)
                    targetAtMidnight = parsed.first
                    enabledAtMidnight = parsed.second
                    if (enabledAtMidnight && firstActivationTimestamp == null) {
                        firstActivationTimestamp = ev.effectiveTimestamp
                    }
                }
                GoalHistoryEventType.ENABLED -> {
                    enabledAtMidnight = true
                    ev.newValue.toIntOrNull()?.let { targetAtMidnight = it }
                    if (firstActivationTimestamp == null) {
                        firstActivationTimestamp = ev.effectiveTimestamp
                    }
                }
                GoalHistoryEventType.DISABLED -> {
                    enabledAtMidnight = false
                }
                GoalHistoryEventType.TARGET_CHANGED -> {
                    ev.newValue.toIntOrNull()?.let { targetAtMidnight = it }
                }
                GoalHistoryEventType.SCOPE_CHANGED -> Unit
            }
        }

        // If never created before or during this day, return null
        if (beforeOrAtMidnight.isEmpty() && duringDay.isEmpty()) return null

        var currentlyEnabled = enabledAtMidnight
        var currentTarget = targetAtMidnight ?: duringDay.firstNotNullOfOrNull {
            parseTargetAndEnabled(it.newValue, true).first.takeIf { t -> t > 0 }
        } ?: DEFAULT_MINDFUL_CAP

        var strictestTarget = if (enabledAtMidnight && targetAtMidnight != null) targetAtMidnight else Int.MAX_VALUE
        var wasDisabledDuringDay = false
        var wasReenabledMidday = false
        var wasViolated = false

        // Helper to check usage at a given timestamp
        fun usageAt(ts: Long): Int {
            val latestCheckpoint = timestampedUsage.lastOrNull { it.first <= ts }?.second
            return latestCheckpoint ?: 0
        }

        // Check if already violated at midnight
        if (enabledAtMidnight && usageAt(dayStartMidnightMs) > currentTarget) {
            wasViolated = true
        }

        for (ev in duringDay) {
            // Before applying event at ev.effectiveTimestamp, check if usage up to ev.effectiveTimestamp violated active goal
            if (currentlyEnabled && usageAt(ev.effectiveTimestamp) > currentTarget) {
                wasViolated = true
            }

            when (ev.eventType) {
                GoalHistoryEventType.CREATED -> {
                    val (t, en) = parseTargetAndEnabled(ev.newValue, defaultEnabled = true)
                    currentTarget = t
                    currentlyEnabled = en
                    if (en) {
                        if (firstActivationTimestamp == null) {
                            firstActivationTimestamp = ev.effectiveTimestamp
                        }
                        strictestTarget = minOf(strictestTarget, currentTarget)
                    }
                }
                GoalHistoryEventType.ENABLED -> {
                    currentlyEnabled = true
                    ev.newValue.toIntOrNull()?.let { currentTarget = it }
                    if (firstActivationTimestamp == null) {
                        firstActivationTimestamp = ev.effectiveTimestamp
                    }
                    if (wasDisabledDuringDay) {
                        wasReenabledMidday = true
                    }
                    strictestTarget = minOf(strictestTarget, currentTarget)
                }
                GoalHistoryEventType.DISABLED -> {
                    if (currentlyEnabled && usageAt(ev.effectiveTimestamp) > currentTarget) {
                        wasViolated = true
                    }
                    if (enabledAtMidnight || currentlyEnabled) {
                        wasDisabledDuringDay = true
                    }
                    currentlyEnabled = false
                }
                GoalHistoryEventType.TARGET_CHANGED -> {
                    val newTarget = ev.newValue.toIntOrNull() ?: currentTarget
                    currentTarget = newTarget
                    if (currentlyEnabled || enabledAtMidnight) {
                        strictestTarget = minOf(strictestTarget, newTarget)
                    }
                    if (currentlyEnabled && usageAt(ev.effectiveTimestamp) > currentTarget) {
                        wasViolated = true
                    }
                }
                GoalHistoryEventType.SCOPE_CHANGED -> Unit
            }
        }

        if (strictestTarget == Int.MAX_VALUE) {
            strictestTarget = currentTarget
        }

        // Check if any usage checkpoint during the day while active (or for midnight-enabled goals) exceeded strictestTarget
        if (enabledAtMidnight) {
            if (finalFullDayUsage > strictestTarget) {
                wasViolated = true
            }
            for ((_, u) in timestampedUsage) {
                if (u > strictestTarget) {
                    wasViolated = true
                }
            }
        }

        // Usage before activation is NEVER retroactively attributed to usageSinceActivation
        val usageBeforeActivation = if (firstActivationTimestamp != null && firstActivationTimestamp > dayStartMidnightMs) {
            usageAt(firstActivationTimestamp)
        } else {
            0
        }
        val usageSinceActivation = if (firstActivationTimestamp != null) {
            (finalFullDayUsage - usageBeforeActivation).coerceAtLeast(0)
        } else {
            0
        }

        val progressionEligible = enabledAtMidnight && !wasDisabledDuringDay && !wasReenabledMidday

        return DailyGoalSnapshot(
            dateKey = dateKey,
            goalId = goalId,
            goalType = goalType,
            platformScope = platformScope,
            enabledAtMidnight = enabledAtMidnight,
            targetAtMidnight = targetAtMidnight,
            strictestTarget = strictestTarget,
            firstActivationTimestamp = firstActivationTimestamp,
            wasDisabledDuringDay = wasDisabledDuringDay,
            wasReenabledMidday = wasReenabledMidday,
            wasViolated = wasViolated,
            progressionEligible = progressionEligible,
            usageSinceMidnight = finalFullDayUsage,
            usageSinceActivation = usageSinceActivation,
            lastHistoryEventId = sorted.lastOrNull()?.id,
            updatedAt = sorted.lastOrNull()?.effectiveTimestamp ?: dayStartMidnightMs
        )
    }

    /**
     * Computes dynamic MindfulThresholds from midnight-eligible snapshots.
     * - Effective target is capped at 30.
     * - A platform scope without its own target inherits the applicable combined target.
     * - Default threshold is 30 when no combined target exists.
     */
    fun computeMindfulThresholds(snapshots: List<DailyGoalSnapshot>): MindfulThresholds {
        val eligible = snapshots.filter { it.enabledAtMidnight }

        val combinedCountGoal = eligible.firstOrNull {
            it.goalType == GoalType.REEL_COUNT && it.platformScope == GoalScope.COMBINED
        }
        val igCountGoal = eligible.firstOrNull {
            it.goalType == GoalType.REEL_COUNT && it.platformScope == GoalScope.INSTAGRAM
        }
        val ytCountGoal = eligible.firstOrNull {
            it.goalType == GoalType.REEL_COUNT && it.platformScope == GoalScope.YOUTUBE
        }

        val combinedTimeGoal = eligible.firstOrNull {
            it.goalType == GoalType.VIEWING_TIME_MINUTES && it.platformScope == GoalScope.COMBINED
        }
        val igTimeGoal = eligible.firstOrNull {
            it.goalType == GoalType.VIEWING_TIME_MINUTES && it.platformScope == GoalScope.INSTAGRAM
        }
        val ytTimeGoal = eligible.firstOrNull {
            it.goalType == GoalType.VIEWING_TIME_MINUTES && it.platformScope == GoalScope.YOUTUBE
        }

        val combinedCountCap = minOf(combinedCountGoal?.strictestTarget ?: DEFAULT_MINDFUL_CAP, DEFAULT_MINDFUL_CAP)
        val igCountCap = minOf(igCountGoal?.strictestTarget ?: combinedCountCap, DEFAULT_MINDFUL_CAP)
        val ytCountCap = minOf(ytCountGoal?.strictestTarget ?: combinedCountCap, DEFAULT_MINDFUL_CAP)

        val combinedTimeCap = minOf(combinedTimeGoal?.strictestTarget ?: DEFAULT_MINDFUL_CAP, DEFAULT_MINDFUL_CAP)
        val igTimeCap = minOf(igTimeGoal?.strictestTarget ?: combinedTimeCap, DEFAULT_MINDFUL_CAP)
        val ytTimeCap = minOf(ytTimeGoal?.strictestTarget ?: combinedTimeCap, DEFAULT_MINDFUL_CAP)

        return MindfulThresholds(
            maxCombinedReels = combinedCountCap,
            maxInstagramReels = igCountCap,
            maxYoutubeReels = ytCountCap,
            maxCombinedMinutes = combinedTimeCap,
            maxInstagramMinutes = igTimeCap,
            maxYoutubeMinutes = ytTimeCap
        )
    }

    /**
     * Evaluates all goal snapshots and usage for a calendar day according to Section 4 & Section 5.
     */
    fun evaluateDailyGoals(
        snapshots: List<DailyGoalSnapshot>,
        usage: DailyUsageMetrics
    ): GoalEvaluationSummary {
        // Refresh each snapshot's violation state against actual usage for its scope
        val updatedSnapshots = snapshots.map { snap ->
            val scopeUsage = usage.usageFor(snap.goalType, snap.platformScope)
            val violated = snap.wasViolated || (snap.enabledAtMidnight && scopeUsage > snap.strictestTarget)
            snap.copy(
                usageSinceMidnight = scopeUsage,
                wasViolated = violated
            )
        }

        // Any goal active at midnight (even if disabled later during the day) remains part of the violation check
        val midnightActiveGoals = updatedSnapshots.filter { it.enabledAtMidnight }
        val anyActiveGoalViolated = midnightActiveGoals.any { it.wasViolated }

        // Progression-eligible goals (enabled at midnight and never disabled mid-day)
        val countMidnightGoals = midnightActiveGoals.filter { it.goalType == GoalType.REEL_COUNT }
        val timeMidnightGoals = midnightActiveGoals.filter { it.goalType == GoalType.VIEWING_TIME_MINUTES }

        // For a goal type to pass and earn XP:
        // 1. At least one scope of that goal type was enabled at midnight and progressionEligible.
        // 2. ALL midnight-active scopes of that goal type must be progressionEligible and unviolated (passed).
        val countGoalTypeEligibleAndPassed = countMidnightGoals.isNotEmpty() &&
            countMidnightGoals.all { it.isPassedAndProgressionEligible }

        val timeGoalTypeEligibleAndPassed = timeMidnightGoals.isNotEmpty() &&
            timeMidnightGoals.all { it.isPassedAndProgressionEligible }

        val bothGoalTypesEligibleAndPassed = countGoalTypeEligibleAndPassed && timeGoalTypeEligibleAndPassed

        val hasAnyProgressionEligibleGoal = updatedSnapshots.any { it.progressionEligible }
        val hasMiddayActivatedGoal = updatedSnapshots.any {
            !it.enabledAtMidnight && it.firstActivationTimestamp != null
        }

        val thresholds = computeMindfulThresholds(updatedSnapshots)

        return GoalEvaluationSummary(
            anyActiveGoalViolated = anyActiveGoalViolated,
            countGoalTypeEligibleAndPassed = countGoalTypeEligibleAndPassed,
            timeGoalTypeEligibleAndPassed = timeGoalTypeEligibleAndPassed,
            bothGoalTypesEligibleAndPassed = bothGoalTypesEligibleAndPassed,
            hasAnyProgressionEligibleGoal = hasAnyProgressionEligibleGoal,
            hasOnlyMiddayActivatedGoals = !hasAnyProgressionEligibleGoal && hasMiddayActivatedGoal,
            mindfulThresholds = thresholds
        )
    }

    private fun parseTargetAndEnabled(raw: String, defaultEnabled: Boolean): Pair<Int, Boolean> {
        if (raw.contains(":")) {
            val parts = raw.split(":")
            val target = parts.getOrNull(0)?.toIntOrNull() ?: DEFAULT_MINDFUL_CAP
            val enabled = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: defaultEnabled
            return Pair(target, enabled)
        }
        return Pair(raw.toIntOrNull() ?: DEFAULT_MINDFUL_CAP, defaultEnabled)
    }
}
