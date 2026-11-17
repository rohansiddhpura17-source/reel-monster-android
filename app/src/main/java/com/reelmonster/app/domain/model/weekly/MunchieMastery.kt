package com.reelmonster.app.domain.model.weekly

/**
 * Phase 15 — Munchie Mastery Auras (proposal §7).
 *
 * The five growth stages (BABY, CURIOUS, HUNGRY, STUFFED, MEGACHONK)
 * are preserved exactly and are NOT changed here.
 *
 * Mastery cosmetics are lightweight, visual-only, and depend ONLY on
 * XP-derived level and streaks:
 * - Level 3–4: small green sprout.
 * - Level 5–6: subtle mint aura.
 * - Level 7–8: restrained sparkle flecks.
 * - Level 9–10: golden leafy crown.
 * - Streak >= 7 days: optional streak emblem.
 *
 * No cosmetic depends on Reel count, viewing duration, or increased
 * scrolling. Scrolling more never earns XP or cosmetics. No continuous
 * animation while idle.
 */
enum class MasteryAura(val displayLabel: String) {
    NONE("No mastery aura"),
    SPROUT("Green sprout"),
    MINT_AURA("Mint aura"),
    SPARKLE("Sparkle flecks"),
    GOLDEN_CROWN("Golden leafy crown")
}

data class MunchieMastery(
    val level: Int,
    val streakDays: Int,
    val aura: MasteryAura,
    val streakEmblemVisible: Boolean
)

object MunchieMasteryCalculator {

    fun auraForLevel(level: Int): MasteryAura {
        return when (level) {
            3, 4 -> MasteryAura.SPROUT
            5, 6 -> MasteryAura.MINT_AURA
            7, 8 -> MasteryAura.SPARKLE
            9, 10 -> MasteryAura.GOLDEN_CROWN
            else -> MasteryAura.NONE
        }
    }

    fun streakEmblemVisible(streakDays: Int): Boolean = streakDays >= 7

    fun masteryFor(level: Int, streakDays: Int): MunchieMastery {
        return MunchieMastery(
            level = level,
            streakDays = streakDays.coerceAtLeast(0),
            aura = auraForLevel(level),
            streakEmblemVisible = streakEmblemVisible(streakDays)
        )
    }
}
