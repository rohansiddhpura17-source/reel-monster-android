package com.reelmonster.app.domain.model

/**
 * ReactionStyle
 *
 * Configurable animation reaction style triggered on user count interactions.
 */
enum class ReactionStyle(
    val key: String,
    val displayName: String,
    val description: String
) {
    BOUNCE(
        key = "bounce",
        displayName = "Playful Bounce",
        description = "Munchie performs a springy upward hop"
    ),
    SQUASH_AND_STRETCH(
        key = "squash",
        displayName = "Squash & Stretch",
        description = "Cartoonish squash and energetic stretch"
    ),
    WINK_AND_HAPPY(
        key = "wink",
        displayName = "Happy Wiggle",
        description = "Joyful side-to-side celebration wiggle"
    );

    companion object {
        fun fromKey(key: String?): ReactionStyle {
            return entries.firstOrNull { it.key == key } ?: BOUNCE
        }
    }
}
