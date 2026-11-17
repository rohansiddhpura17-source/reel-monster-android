package com.reelmonster.app.domain.model.goal

/**
 * Calculated progress snapshot of an active daily goal.
 *
 * @property goal The underlying goal configuration.
 * @property currentValue Observed count or minutes today.
 * @property remainingValue Remaining allowance before reaching limit (min 0).
 * @property usageRatio Ratio of usage (currentValue / targetValue), e.g. 0.50f = 50%.
 * @property state Real-time pacing state.
 */
data class DailyGoalProgress(
    val goal: DailyGoal,
    val currentValue: Int,
    val remainingValue: Int,
    val usageRatio: Float,
    val state: GoalState
) {
    val usagePercentage: Int
        get() = (usageRatio * 100).toInt().coerceAtLeast(0)

    val isLimitBreached: Boolean
        get() = currentValue > goal.targetValue

    val formattedRemaining: String
        get() = if (remainingValue > 0) {
            "$remainingValue ${goal.type.unit} remaining"
        } else if (currentValue == goal.targetValue) {
            "Limit reached (${goal.targetValue} ${goal.type.unit})"
        } else {
            "${currentValue - goal.targetValue} ${goal.type.unit} over limit"
        }
}
