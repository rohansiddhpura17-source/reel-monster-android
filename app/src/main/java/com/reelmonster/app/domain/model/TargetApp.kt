package com.reelmonster.app.domain.model

enum class TargetApp(val displayName: String, val mediaType: String, val packageName: String) {
    INSTAGRAM("Instagram", "Reels", "com.instagram.android"),
    YOUTUBE("YouTube", "Shorts", "com.google.android.youtube");

    companion object {
        fun fromPackage(pkg: String): TargetApp? {
            return entries.firstOrNull { it.packageName == pkg }
        }

        fun fromPackageName(pkg: String): TargetApp? = fromPackage(pkg)
    }
}
