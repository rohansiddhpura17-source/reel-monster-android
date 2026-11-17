package com.reelmonster.app.domain.model.goal

/**
 * Type of consumption metric targeted by a daily goal.
 */
enum class GoalType(val label: String, val unit: String) {
    REEL_COUNT("Reel Count", "reels"),
    VIEWING_TIME_MINUTES("Viewing Time", "mins")
}
