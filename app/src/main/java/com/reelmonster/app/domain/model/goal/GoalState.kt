package com.reelmonster.app.domain.model.goal

/**
 * Real-time pacing state of an active daily goal.
 *
 * Clearly distinguishes reaching a maximum usage limit from successfully
 * completing a day within that limit. Reaching 100% is an alert boundary,
 * not an achievement.
 */
enum class GoalState(val label: String) {
    /** Zero activity recorded towards this goal today (0%). */
    NOT_STARTED("Not Started"),

    /** Normal pacing well below configured boundary (1%–69%). */
    IN_PROGRESS("In Progress"),

    /** Approaching the configured boundary (70%–89%). Mindful wrapping up advised. */
    APPROACHING_LIMIT("Approaching Limit"),

    /** Reached or within 10% of the maximum limit (90%–100%). Boundary alert. */
    AT_LIMIT("At Limit"),

    /** Consumption exceeded the user-configured boundary (> 100%). */
    EXCEEDED("Exceeded"),

    /** Tracking is paused or master switch is OFF. */
    PAUSED("Paused")
}
