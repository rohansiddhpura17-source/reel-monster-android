package com.reelmonster.app.domain.model.goal

/**
 * User-configured daily boundary goal.
 *
 * @property id Local SQLite primary key.
 * @property type Whether this limits reel count or viewing minutes.
 * @property scope Target platform (Combined, Instagram, YouTube).
 * @property targetValue Maximum boundary allowance for the calendar day.
 * @property isEnabled Whether this goal is actively evaluated.
 */
data class DailyGoal(
    val id: Long = 0,
    val type: GoalType,
    val scope: GoalScope,
    val targetValue: Int,
    val isEnabled: Boolean = true
)
