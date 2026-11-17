package com.reelmonster.app.domain.model

/**
 * MunchieMascotEngine
 *
 * Core intelligence and personality engine for Munchie the Reel Monster.
 * Manages growth stage progression, evolution thresholds, and contextual humorous quotes.
 */
object MunchieMascotEngine {

    const val MSG_ZERO_COUNT = "Zero reels today! Munchie is calm, relaxed, and zen. 🌱"
    const val MSG_FIRST_REEL = "First reel of the day! Down the rabbit hole we go! 🚀"
    const val MSG_PAUSED = "Munchie is sleeping 💤 Tracking is paused."
    const val MSG_RESUMED = "Munchie is awake and ready for chomping! 👀"

    val STAGE_MESSAGES: Map<MunchieStage, List<String>> = mapOf(
        MunchieStage.BABY to listOf(
            "Just one? I'm still hungry! 🌱",
            "Just one more? I just opened my eyes! ✨",
            "A tiny reel for a tiny monster! 🍼",
            "Only just getting started... right?",
            "Baby steps! What are we exploring today? 🔍"
        ),
        MunchieStage.CURIOUS to listOf(
            "What's this Reel thing? Let me see! 👀",
            "I can smell another Short! What are we watching next?",
            "Ooh, that creator has serious moves! 🔍",
            "Just checking one more dance trend...",
            "Curiosity didn't kill the monster, it queued another clip! 🍿"
        ),
        MunchieStage.HUNGRY to listOf(
            "Feed me another!",
            "Feed me another Reel! Your thumb is working overtime! 📱💨",
            "Chomp chomp! Keep the dopamine flowing!",
            "Munching on memes! Don't stop now! 🍩",
            "Your scroll velocity is breaking sound barriers! ⚡"
        ),
        MunchieStage.STUFFED to listOf(
            "I think we've watched enough... 🛌",
            "Burp! Your evening disappeared.",
            "Homework called. It misses you.",
            "My tummy is expanding faster than your screen time!",
            "Okay, maybe just put the phone face down for a minute... 🛑",
            "Satiated! Even a monster has limits! 💤"
        ),
        MunchieStage.MEGACHONK to listOf(
            "WE HAVE BECOME THE ALGORITHM! 🚨",
            "I'm not a monster anymore... I'm a server rack!",
            "I cannot move. You watched 50+ videos. Put the phone down or I am calling your mom! 🚨",
            "Emergency chonker status! Go touch grass! 🌳",
            "Dopamine tank overflow! Your sleep schedule is crying! 😴",
            "Infinite scroll champion! Time for a digital detox nap! 🛑"
        )
    )

    /**
     * Selects a funny contextual message for Munchie based on stage, today's reel count,
     * and current pause state.
     * Guaranteed deterministic output for unit tests.
     */
    fun getMessage(stage: MunchieStage, count: Int = 0, isPaused: Boolean = false): String {
        if (isPaused) {
            return MSG_PAUSED
        }
        if (count == 0) {
            return MSG_ZERO_COUNT
        }
        if (count == 1) {
            return MSG_FIRST_REEL
        }

        val messages = STAGE_MESSAGES[stage] ?: listOf(stage.defaultQuote)
        val safeIndex = if (count >= 0) count % messages.size else kotlin.math.abs(count) % messages.size
        return messages[safeIndex]
    }

    /**
     * Calculates the progress fraction (0.0 to 1.0) towards the next evolution stage.
     */
    fun getProgressToNextStage(currentCount: Int): Float {
        val stage = MunchieStage.fromCount(currentCount)
        if (stage == MunchieStage.MEGACHONK) return 1.0f

        val stageRange = (stage.maxReels - stage.minReels + 1).toFloat()
        val countInStage = (currentCount - stage.minReels).toFloat()
        return (countInStage / stageRange).coerceIn(0.0f, 1.0f)
    }

    /**
     * Returns the count of reels needed to reach the next stage.
     * Returns 0 if already at MEGACHONK.
     */
    fun getReelsUntilNextStage(currentCount: Int): Int {
        val stage = MunchieStage.fromCount(currentCount)
        if (stage == MunchieStage.MEGACHONK) return 0
        return (stage.maxReels + 1 - currentCount).coerceAtLeast(0)
    }

    /**
     * Returns the next stage in the evolution chain, or null if maximum reached.
     */
    fun getNextStage(stage: MunchieStage): MunchieStage? {
        return when (stage) {
            MunchieStage.BABY -> MunchieStage.CURIOUS
            MunchieStage.CURIOUS -> MunchieStage.HUNGRY
            MunchieStage.HUNGRY -> MunchieStage.STUFFED
            MunchieStage.STUFFED -> MunchieStage.MEGACHONK
            MunchieStage.MEGACHONK -> null
        }
    }
}
