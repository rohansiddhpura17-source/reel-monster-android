package com.reelmonster.app.domain.model

enum class MunchieStage(
    val level: Int,
    val stageName: String,
    val appetiteLabel: String,
    val minReels: Int,
    val maxReels: Int,
    val emoji: String,
    val defaultQuote: String
) {
    BABY(
        level = 1,
        stageName = "Baby Munchie",
        appetiteLabel = "Low Appetite",
        minReels = 0,
        maxReels = 5,
        emoji = "✨",
        defaultQuote = "\"Just one more? I just opened my eyes! ✨\""
    ),
    CURIOUS(
        level = 2,
        stageName = "Curious Munchie",
        appetiteLabel = "Moderate Appetite",
        minReels = 6,
        maxReels = 15,
        emoji = "👀",
        defaultQuote = "\"I can smell another Short! What are we watching next? 👀\""
    ),
    HUNGRY(
        level = 3,
        stageName = "Hungry Munchie",
        appetiteLabel = "Heavy Appetite",
        minReels = 16,
        maxReels = 30,
        emoji = "🍩",
        defaultQuote = "\"Feed me another Short! Your thumb is working overtime! 📱💨\""
    ),
    STUFFED(
        level = 4,
        stageName = "Stuffed Munchie",
        appetiteLabel = "Satiated",
        minReels = 31,
        maxReels = 50,
        emoji = "🛌",
        defaultQuote = "\"Burp! Your evening disappeared... homework called, it misses you! 🛌\""
    ),
    MEGACHONK(
        level = 5,
        stageName = "Megachonk Overfed",
        appetiteLabel = "Maximum Capacity",
        minReels = 51,
        maxReels = Int.MAX_VALUE,
        emoji = "🚨",
        defaultQuote = "\"I cannot move. You watched 50+ videos. Put the phone down or I am calling your mom! 🚨\""
    );

    companion object {
        fun fromCount(count: Int): MunchieStage {
            return entries.firstOrNull { count in it.minReels..it.maxReels } ?: MEGACHONK
        }
    }
}
