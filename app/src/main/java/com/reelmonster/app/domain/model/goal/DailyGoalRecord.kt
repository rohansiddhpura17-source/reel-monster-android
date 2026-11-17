package com.reelmonster.app.domain.model.goal

/**
 * Historical record of a finalized daily goal for a specific past calendar date.
 *
 * Persisted permanently across restarts, updates, and daily tracking resets.
 * Evaluated at the local-day boundary.
 *
 * @property id Local SQLite primary key.
 * @property dateKey The calendar date in 'YYYY-MM-DD' format.
 * @property goalType Target metric (REEL_COUNT or VIEWING_TIME_MINUTES).
 * @property scope Target platform (COMBINED, INSTAGRAM, YOUTUBE).
 * @property targetValue Target limit in place on that date.
 * @property actualValue Final recorded value at end of day.
 * @property wasSuccessful True if the user remained within or equal to their limit (actualValue <= targetValue).
 * @property recordedAt Epoch timestamp when the record was finalized.
 */
data class DailyGoalRecord(
    val id: Long = 0,
    val dateKey: String,
    val goalType: GoalType,
    val scope: GoalScope,
    val targetValue: Int,
    val actualValue: Int,
    val wasSuccessful: Boolean,
    val recordedAt: Long = System.currentTimeMillis()
)
