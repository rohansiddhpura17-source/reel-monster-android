package com.reelmonster.app.domain.model.focus

enum class FocusStatus(val label: String) {
    IDLE("Idle"),
    RUNNING("Running"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    val isActive: Boolean get() = this == RUNNING || this == PAUSED
}
