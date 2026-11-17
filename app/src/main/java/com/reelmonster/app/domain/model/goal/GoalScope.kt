package com.reelmonster.app.domain.model.goal

/**
 * Platform scope targeted by a daily goal.
 */
enum class GoalScope(val displayName: String) {
    COMBINED("Combined (All Feeds)"),
    INSTAGRAM("Instagram Reels"),
    YOUTUBE("YouTube Shorts")
}
