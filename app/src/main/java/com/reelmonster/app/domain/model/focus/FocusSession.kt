package com.reelmonster.app.domain.model.focus

data class FocusSession(
    val id: Long = 0,
    val sessionType: FocusType,
    val targetDurationMs: Long,
    val actualDurationMs: Long,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val completionStatus: String,
    val dateString: String
) {
    val isCompleted: Boolean get() = completionStatus == STATUS_COMPLETED

    companion object {
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_PAUSED_ABANDONED = "ABANDONED"
    }
}
