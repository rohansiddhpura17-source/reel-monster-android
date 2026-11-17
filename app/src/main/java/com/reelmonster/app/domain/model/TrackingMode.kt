package com.reelmonster.app.domain.model

enum class TrackingMode(val label: String, val shortLabel: String) {
    LEVEL_1_MANUAL("Level 1: Manual (+1 Chomp)", "Level 1 (Manual)"),
    LEVEL_2_AUTOMATIC("Level 2: Automatic Detection", "Level 2 (Auto)")
}
