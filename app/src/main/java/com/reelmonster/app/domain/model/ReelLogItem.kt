package com.reelmonster.app.domain.model

data class ReelLogItem(
    val id: Long = 0,
    val timestamp: Long,
    val targetApp: TargetApp,
    val trackingMode: TrackingMode,
    val countIncrement: Int = 1,
    val sessionDate: String // YYYY-MM-DD
)

data class DailySummary(
    val dateString: String,
    val dayOfWeek: String,
    val totalReels: Int,
    val instagramCount: Int,
    val youtubeCount: Int,
    val estimatedMinutesLost: Double,
    val isBingeSpike: Boolean = false,
    val isToday: Boolean = false
)
