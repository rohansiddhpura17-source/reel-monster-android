package com.reelmonster.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    // Bottom Bar Destinations
    data object Home : Screen("home", "Home", Icons.Filled.GridView)
    data object Stats : Screen("stats", "Stats", Icons.Filled.Leaderboard)
    data object History : Screen("history", "History", Icons.Filled.CalendarMonth)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)

    // Flow Screens
    data object Onboarding : Screen("onboarding", "Welcome")
    data object GrowthGallery : Screen("growth_gallery", "Monster Evolution")
    data object ModeSelection : Screen("mode_selection", "Tracking Mode")
    data object PermissionSetup : Screen("permission_setup", "Permissions")

    // Phase 12 Screen
    data object Goals : Screen("goals", "Daily Goals", Icons.Filled.Flag)

    // Phase 13 Screen
    data object Focus : Screen("focus", "Focus Mode", Icons.Filled.SelfImprovement)

    // Phase 14 Screen
    data object Progression : Screen("progression", "Progression & Streaks", Icons.Filled.EmojiEvents)

    // Phase 15 Screen (flow screen, not a bottom-nav destination)
    data object WeeklyIntelligence : Screen("weekly_intelligence", "Weekly Intelligence")

    companion object {
        /**
         * Bottom navigation destinations evaluated on-demand via getter.
         * Dynamic evaluation guarantees that subclasses are already initialized
         * and prevents JVM circular classloader deadlocks / null entries.
         */
        val bottomNavItems: List<Screen>
            get() = listOf(Home, Stats, History, Settings)

        /**
         * Comprehensive destination catalog.
         */
        val allScreens: List<Screen>
            get() = listOf(
                Home,
                Stats,
                History,
                Settings,
                Onboarding,
                GrowthGallery,
                ModeSelection,
                PermissionSetup,
                Goals,
                Focus,
                Progression,
                WeeklyIntelligence
            )

        /**
         * Safe route resolution ensuring invalid, null, or corrupted routes
         * gracefully resolve to [Home] (Dashboard).
         */
        fun fromRoute(route: String?): Screen {
            if (route.isNullOrBlank()) return Home
            return allScreens.firstOrNull { it.route == route } ?: Home
        }

        /**
         * Resolves only valid bottom navigation items, falling back to [Home].
         */
        fun fromBottomRoute(route: String?): Screen {
            if (route.isNullOrBlank()) return Home
            return bottomNavItems.firstOrNull { it.route == route } ?: Home
        }
    }
}
