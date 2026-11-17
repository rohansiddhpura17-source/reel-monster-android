package com.reelmonster.app.domain.model.progression

import com.reelmonster.app.domain.model.focus.FocusSession
import com.reelmonster.app.domain.model.goal.DailyGoalSnapshot
import com.reelmonster.app.domain.model.goal.DailyUsageMetrics
import com.reelmonster.app.domain.model.goal.GoalEligibilityEvaluator
import com.reelmonster.app.domain.model.monitoring.CoverageCalculator
import com.reelmonster.app.domain.model.monitoring.DailyMonitoringCoverage

/**
 * Canonical XP award event types.
 * Never awards XP for watching Reels/Shorts, increasing scroll time, tapping Munchie,
 * resetting statistics, exceeding goals, or merely connecting AccessibilityService.
 */
enum class XpEventType(val defaultXp: Int, val label: String) {
    FOCUS_SESSION_COMPLETED(20, "Focus Session Completed"),
    DAILY_GOAL_COUNT_MET(30, "Daily Count Goal Met"),
    DAILY_GOAL_TIME_MET(30, "Daily Viewing-Time Goal Met"),
    BOTH_DAILY_GOALS_BONUS(20, "Both Daily Goals Bonus"),
    MINDFUL_DAY_QUALIFIED(25, "Mindful Day Qualified"),
    STREAK_MILESTONE(50, "Streak Milestone"),
    ACHIEVEMENT_UNLOCKED(50, "Achievement Unlocked");

    companion object {
        fun fromName(name: String?): XpEventType {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MINDFUL_DAY_QUALIFIED
        }
    }
}

/**
 * Immutable, idempotent XP transaction record.
 * [eventId] is enforced as UNIQUE in SQLite so repeated evaluations or restarts never award duplicate XP.
 */
data class XpTransaction(
    val id: Long = 0L,
    val eventId: String,
    val eventType: XpEventType,
    val xpAmount: Int,
    val description: String,
    val createdAt: Long = System.currentTimeMillis(),
    val localDate: String
) {
    companion object {
        fun focusSessionKey(sessionId: Long): String = "focus_session_$sessionId"
        fun dailyCountGoalKey(dateKey: String): String = "goal_count_$dateKey"
        fun dailyTimeGoalKey(dateKey: String): String = "goal_time_$dateKey"
        fun bothDailyGoalsKey(dateKey: String): String = "goal_both_$dateKey"
        fun mindfulDayKey(dateKey: String): String = "mindful_day_$dateKey"
        fun streakMilestoneKey(milestoneDays: Int): String = "streak_milestone_$milestoneDays"
        fun achievementKey(achievementId: String): String = "achievement_$achievementId"
    }
}

/**
 * Definition of a user progression level tier.
 */
data class LevelTier(
    val level: Int,
    val title: String,
    val minXp: Int,
    val nextLevelXp: Int?
)

/**
 * Deterministic level calculator for the 10 canonical Reel Monster progression levels.
 */
object LevelCalculator {
    val TIERS: List<LevelTier> = listOf(
        LevelTier(level = 1, title = "Tiny Monster", minXp = 0, nextLevelXp = 100),
        LevelTier(level = 2, title = "Curious Monster", minXp = 100, nextLevelXp = 250),
        LevelTier(level = 3, title = "Focus Monster", minXp = 250, nextLevelXp = 500),
        LevelTier(level = 4, title = "Balanced Monster", minXp = 500, nextLevelXp = 900),
        LevelTier(level = 5, title = "Mindful Monster", minXp = 900, nextLevelXp = 1400),
        LevelTier(level = 6, title = "Habit Master", minXp = 1400, nextLevelXp = 2100),
        LevelTier(level = 7, title = "Monster Legend", minXp = 2100, nextLevelXp = 3000),
        LevelTier(level = 8, title = "Mega Mindful", minXp = 3000, nextLevelXp = 4200),
        LevelTier(level = 9, title = "Ultra Monster", minXp = 4200, nextLevelXp = 6000),
        LevelTier(level = 10, title = "Munchie Legend", minXp = 6000, nextLevelXp = null)
    )

    fun tierForXp(totalXp: Int): LevelTier {
        val safeXp = totalXp.coerceAtLeast(0)
        return TIERS.lastOrNull { safeXp >= it.minXp } ?: TIERS.first()
    }

    fun levelForXp(totalXp: Int): Int = tierForXp(totalXp).level

    fun titleForLevel(level: Int): String {
        return TIERS.firstOrNull { it.level == level }?.title ?: TIERS.first().title
    }

    fun progressFraction(totalXp: Int): Float {
        val safeXp = totalXp.coerceAtLeast(0)
        val tier = tierForXp(safeXp)
        val nextXp = tier.nextLevelXp ?: return 1.0f
        val span = (nextXp - tier.minXp).coerceAtLeast(1)
        val earnedInTier = (safeXp - tier.minXp).coerceIn(0, span)
        return (earnedInTier.toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }

    fun xpRemainingToNextLevel(totalXp: Int): Int {
        val safeXp = totalXp.coerceAtLeast(0)
        val tier = tierForXp(safeXp)
        val nextXp = tier.nextLevelXp ?: return 0
        return (nextXp - safeXp).coerceAtLeast(0)
    }
}

/**
 * Singleton user progression summary persisted in SQLite user_progress table.
 */
data class UserProgress(
    val totalXp: Int = 0,
    val currentLevel: Int = LevelCalculator.levelForXp(totalXp),
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastQualifiedDate: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val tier: LevelTier
        get() = LevelCalculator.tierForXp(totalXp)

    val levelTitle: String
        get() = tier.title

    val nextLevelThresholdXp: Int?
        get() = tier.nextLevelXp

    val xpRemainingToNextLevel: Int
        get() = LevelCalculator.xpRemainingToNextLevel(totalXp)

    val levelProgressFraction: Float
        get() = LevelCalculator.progressFraction(totalXp)

    val levelProgressPercent: Int
        get() = (levelProgressFraction * 100f).toInt().coerceIn(0, 100)
}

/**
 * Evaluation status of a calendar day's streak qualification.
 */
enum class StreakEvaluationStatus {
    QUALIFIED,
    DISQUALIFIED,
    PENDING
}

/**
 * Persistent historical streak qualification record for a completed past date.
 */
data class StreakRecord(
    val localDate: String,
    val isQualified: Boolean,
    val qualifyingReason: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Result of evaluating a calendar day's streak and daily XP awards.
 */
data class DailyProgressionEvaluation(
    val dateKey: String,
    val streakStatus: StreakEvaluationStatus,
    val streakReason: String,
    val awardCountGoalXp: Boolean = false,
    val awardTimeGoalXp: Boolean = false,
    val awardBothGoalsBonusXp: Boolean = false,
    val awardMindfulDayXp: Boolean = false
)

/**
 * Achievement category grouping.
 */
enum class AchievementCategory(val displayName: String) {
    FOCUS("Focus"),
    HABITS("Mindful Habits"),
    MILESTONES("Streaks & Levels");

    companion object {
        fun fromName(name: String?): AchievementCategory {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: FOCUS
        }
    }
}

/**
 * Achievement definition and live progress state.
 */
data class Achievement(
    val achievementId: String,
    val category: AchievementCategory,
    val title: String,
    val description: String,
    val targetProgress: Int,
    val currentProgress: Int = 0,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null,
    val xpReward: Int
) {
    val progressFraction: Float
        get() = if (targetProgress <= 0) 1f else (currentProgress.toFloat() / targetProgress.toFloat()).coerceIn(0f, 1f)
}

/**
 * Canonical catalog of healthy-habit achievements.
 * Never rewards scrolling, reel counts, or screen time.
 */
object AchievementCatalog {
    val DEFAULT_ACHIEVEMENTS: List<Achievement> = listOf(
        // Focus Category
        Achievement(
            achievementId = "first_focus",
            category = AchievementCategory.FOCUS,
            title = "First Step into Zen",
            description = "Complete your first eligible focus session (≥ 80% target duration).",
            targetProgress = 1,
            xpReward = 30
        ),
        Achievement(
            achievementId = "focus_5_sessions",
            category = AchievementCategory.FOCUS,
            title = "Focus Apprentice",
            description = "Complete 5 eligible focus sessions.",
            targetProgress = 5,
            xpReward = 50
        ),
        Achievement(
            achievementId = "focus_60_minutes",
            category = AchievementCategory.FOCUS,
            title = "Hour of Clarity",
            description = "Accumulate 60 minutes of completed focus time.",
            targetProgress = 60,
            xpReward = 75
        ),
        Achievement(
            achievementId = "focus_20_sessions",
            category = AchievementCategory.FOCUS,
            title = "Zen Guardian",
            description = "Complete 20 eligible focus sessions.",
            targetProgress = 20,
            xpReward = 120
        ),
        // Habits Category
        Achievement(
            achievementId = "first_goal_day",
            category = AchievementCategory.HABITS,
            title = "Boundary Keeper",
            description = "Meet an eligible daily goal on a verified monitored day.",
            targetProgress = 1,
            xpReward = 30
        ),
        Achievement(
            achievementId = "both_goals_day",
            category = AchievementCategory.HABITS,
            title = "Balanced Harmony",
            description = "Meet both count and viewing-time goals on the same verified day.",
            targetProgress = 1,
            xpReward = 50
        ),
        Achievement(
            achievementId = "mindful_days_5",
            category = AchievementCategory.HABITS,
            title = "Mindful Rhythm",
            description = "Qualify for 5 mindful days within your dynamic thresholds.",
            targetProgress = 5,
            xpReward = 75
        ),
        Achievement(
            achievementId = "goal_days_10",
            category = AchievementCategory.HABITS,
            title = "Habit Architect",
            description = "Meet your daily goals on 10 verified calendar days.",
            targetProgress = 10,
            xpReward = 100
        ),
        // Milestones Category
        Achievement(
            achievementId = "streak_3_days",
            category = AchievementCategory.MILESTONES,
            title = "Three-Day Spark",
            description = "Reach a 3-day mindful streak.",
            targetProgress = 3,
            xpReward = 40
        ),
        Achievement(
            achievementId = "streak_7_days",
            category = AchievementCategory.MILESTONES,
            title = "Seven-Day Balance",
            description = "Reach a 7-day mindful streak.",
            targetProgress = 7,
            xpReward = 80
        ),
        Achievement(
            achievementId = "reach_level_5",
            category = AchievementCategory.MILESTONES,
            title = "Mindful Monster",
            description = "Reach Progression Level 5 (900 XP).",
            targetProgress = 5,
            xpReward = 75
        ),
        Achievement(
            achievementId = "reach_level_10",
            category = AchievementCategory.MILESTONES,
            title = "Munchie Legend",
            description = "Reach Progression Level 10 (6,000 XP).",
            targetProgress = 10,
            xpReward = 150
        )
    )
}

/**
 * Deterministic decision engine implementing Section 2, 5, 6, and 7 of the Phase 14 specification.
 */
object ProgressionDecisionEngine {
    const val FOCUS_COMPLETION_RATIO_THRESHOLD = 0.80
    val STREAK_MILESTONES: Map<Int, Int> = mapOf(
        3 to 50,
        7 to 100,
        30 to 250
    )

    /**
     * Section 7: Focus XP eligibility check.
     * Awards +20 XP when status == COMPLETED and actualDurationMs >= 80% of targetDurationMs.
     * Independent of streak qualification.
     */
    fun isFocusSessionEligibleForXp(session: FocusSession): Boolean {
        if (session.completionStatus != FocusSession.STATUS_COMPLETED) return false
        if (session.targetDurationMs <= 0L) return false
        val requiredDuration = (session.targetDurationMs * FOCUS_COMPLETION_RATIO_THRESHOLD).toLong()
        return session.actualDurationMs >= requiredDuration
    }

    /**
     * Evaluates a calendar day according to the Section 5 & Section 6 decision table.
     *
     * @param dateKey Target calendar date ('YYYY-MM-DD').
     * @param todayDateKey Current local date ('YYYY-MM-DD').
     * @param coverage DailyMonitoringCoverage for [dateKey].
     * @param dailyResetCount Number of daily resets used on [dateKey].
     * @param goalSnapshots Timestamped DailyGoalSnapshots for [dateKey].
     * @param usage Recorded DailyUsageMetrics for [dateKey].
     * @param focusSessions Focus sessions recorded on [dateKey].
     */
    fun evaluateDay(
        dateKey: String,
        todayDateKey: String,
        coverage: DailyMonitoringCoverage,
        dailyResetCount: Int,
        goalSnapshots: List<DailyGoalSnapshot>,
        usage: DailyUsageMetrics,
        focusSessions: List<FocusSession>
    ): DailyProgressionEvaluation {
        // Rule: Current local day is Pending; never finalized early
        if (dateKey >= todayDateKey) {
            return DailyProgressionEvaluation(
                dateKey = dateKey,
                streakStatus = StreakEvaluationStatus.PENDING,
                streakReason = "CURRENT_DAY_PENDING"
            )
        }

        // Rule: Estimated verified monitoring coverage below 2 hours (7,200,000 ms) -> Disqualified
        if (coverage.estimatedVerifiedMonitoringMs < CoverageCalculator.QUALIFICATION_THRESHOLD_MS) {
            return DailyProgressionEvaluation(
                dateKey = dateKey,
                streakStatus = StreakEvaluationStatus.DISQUALIFIED,
                streakReason = "INSUFFICIENT_ESTIMATED_COVERAGE"
            )
        }

        // Rule: At least 2 hours, but a daily reset was used -> Disqualified & 0 Goal/Mindful XP
        if (dailyResetCount > 0) {
            return DailyProgressionEvaluation(
                dateKey = dateKey,
                streakStatus = StreakEvaluationStatus.DISQUALIFIED,
                streakReason = "DAILY_RESET_USED"
            )
        }

        val goalEval = GoalEligibilityEvaluator.evaluateDailyGoals(goalSnapshots, usage)

        // Rule: At least 2 hours, but an eligible/midnight-active goal was violated -> Disqualified
        if (goalEval.anyActiveGoalViolated) {
            return DailyProgressionEvaluation(
                dateKey = dateKey,
                streakStatus = StreakEvaluationStatus.DISQUALIFIED,
                streakReason = "ACTIVE_GOAL_VIOLATED"
            )
        }

        // Check default boundary when no midnight-active goals were configured
        val hasMidnightActiveGoals = goalSnapshots.any { it.enabledAtMidnight }
        val withinMindfulThresholds = usage.isWithinMindfulThresholds(goalEval.mindfulThresholds)
        if (!hasMidnightActiveGoals && !withinMindfulThresholds) {
            return DailyProgressionEvaluation(
                dateKey = dateKey,
                streakStatus = StreakEvaluationStatus.DISQUALIFIED,
                streakReason = "DEFAULT_BOUNDARY_EXCEEDED"
            )
        }

        val hasEligibleFocus = focusSessions.any { isFocusSessionEligibleForXp(it) }
        val hasSupportedAppActivity = coverage.detectionEventCount > 0 || !usage.isZeroViewing

        // Determine Streak Qualification according to Section 6 table:
        val (streakStatus, streakReason) = when {
            // Path 1: At least 2 hours, no goal violation, and eligible focus session completed -> Qualified
            hasEligibleFocus -> {
                StreakEvaluationStatus.QUALIFIED to "QUALIFIED_FOCUS_SESSION"
            }
            // Rule: Goal first activated during the day cannot qualify the day for progression
            goalEval.hasOnlyMiddayActivatedGoals -> {
                StreakEvaluationStatus.DISQUALIFIED to "MIDDAY_GOAL_NOT_ELIGIBLE"
            }
            // Path 2: At least 2 hours, no goal violation, and supported-app activity was monitored -> Qualified
            hasSupportedAppActivity -> {
                if (goalEval.hasAnyProgressionEligibleGoal || !hasMidnightActiveGoals) {
                    StreakEvaluationStatus.QUALIFIED to "QUALIFIED_MONITORED_ACTIVITY"
                } else {
                    // Had midnight goals, all were disabled mid-day without focus session
                    StreakEvaluationStatus.DISQUALIFIED to "GOAL_DISABLED_MIDDAY"
                }
            }
            // Path 3: At least 2 hours, no goal violation, and no supported-app viewing occurred -> Qualified as mindful zero-viewing day
            usage.isZeroViewing -> {
                if (hasMidnightActiveGoals && !goalEval.hasAnyProgressionEligibleGoal) {
                    StreakEvaluationStatus.DISQUALIFIED to "GOAL_DISABLED_MIDDAY"
                } else {
                    StreakEvaluationStatus.QUALIFIED to "QUALIFIED_MINDFUL_ZERO_VIEWING_DAY"
                }
            }
            else -> {
                StreakEvaluationStatus.DISQUALIFIED to "UNQUALIFIED"
            }
        }

        val isMindfulDayEligible = streakStatus == StreakEvaluationStatus.QUALIFIED &&
            withinMindfulThresholds &&
            !goalEval.hasOnlyMiddayActivatedGoals

        return DailyProgressionEvaluation(
            dateKey = dateKey,
            streakStatus = streakStatus,
            streakReason = streakReason,
            awardCountGoalXp = goalEval.countGoalTypeEligibleAndPassed,
            awardTimeGoalXp = goalEval.timeGoalTypeEligibleAndPassed,
            awardBothGoalsBonusXp = goalEval.bothGoalTypesEligibleAndPassed,
            awardMindfulDayXp = isMindfulDayEligible
        )
    }
}
