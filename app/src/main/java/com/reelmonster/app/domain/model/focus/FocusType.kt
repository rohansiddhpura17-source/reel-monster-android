package com.reelmonster.app.domain.model.focus

enum class FocusType(val label: String, val defaultMinutes: Int) {
    QUICK_BREAK("Quick Break", 5),
    DEEP_FOCUS("Deep Focus", 25),
    CUSTOM("Custom Focus", 20);

    val displayName: String get() = label

    companion object {
        fun fromName(name: String?): FocusType {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEEP_FOCUS
        }
    }
}
