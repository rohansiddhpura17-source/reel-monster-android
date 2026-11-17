package com.reelmonster.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.reelmonster.app.core.design.MintBackground
import com.reelmonster.app.core.design.ReelMonsterTheme
import com.reelmonster.app.core.util.OverlayPermissionHelper
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.presentation.components.BottomNavigationBar
import com.reelmonster.app.presentation.components.ErrorState
import com.reelmonster.app.presentation.components.LoadingState
import com.reelmonster.app.presentation.dashboard.DashboardScreen
import com.reelmonster.app.presentation.focus.FocusModeScreen
import com.reelmonster.app.presentation.goals.DailyGoalsScreen
import com.reelmonster.app.presentation.history.HistoryScreen
import com.reelmonster.app.presentation.navigation.Screen
import com.reelmonster.app.presentation.onboarding.OnboardingScreen
import com.reelmonster.app.presentation.progression.ProgressionScreen
import com.reelmonster.app.presentation.settings.SettingsScreen
import com.reelmonster.app.presentation.statistics.StatisticsScreen
import com.reelmonster.app.presentation.weekly.WeeklyIntelligenceScreen
import com.reelmonster.app.service.overlay.FloatingMonsterOverlayService

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = ReelMonsterApp.getOrCreateRepository(this)
        handleTestExtras(intent, repository)

        setContent {
            ReelMonsterTheme {
                ReelMonsterAppRoot(repository = repository)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val repository = ReelMonsterApp.getOrCreateRepository(this)
        handleTestExtras(intent, repository)
    }

    private fun handleTestExtras(intent: android.content.Intent?, repository: ReelRepository) {
        if (intent == null) return
        if (intent.getBooleanExtra("enable_auto_mode_for_test", false)) {
            repository.preferencesManager.setOnboardingCompleted(true)
            repository.preferencesManager.setTrackingMode(com.reelmonster.app.domain.model.TrackingMode.LEVEL_2_AUTOMATIC)
            repository.preferencesManager.setTrackingPaused(false)
            repository.preferencesManager.setOverlayEnabled(true)
        }
        if (intent.hasExtra("set_paused_for_test")) {
            val paused = intent.getBooleanExtra("set_paused_for_test", false)
            repository.preferencesManager.setTrackingPaused(paused)
        }
        if (intent.getBooleanExtra("reset_today_for_test", false)) {
            repository.resetTodayCountForTesting()
            com.reelmonster.app.service.tracking.ReelAccessibilityService.dedupEngine.reset(clearRecentHistory = true)
            com.reelmonster.app.service.tracking.DetectorLogger.logRepositoryIncrement(
                app = com.reelmonster.app.domain.model.TargetApp.INSTAGRAM,
                fingerprint = "TEST_RESET_TODAY",
                success = true,
                newTotalToday = repository.todayCount.value
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val repository = ReelMonsterApp.getOrCreateRepository(this)
        val hasOverlayPerm = OverlayPermissionHelper.canDrawOverlays(this)
        val isOnboardingDone = repository.preferencesManager.isOnboardingCompleted.value
        val isOverlayEnabled = repository.preferencesManager.isOverlayEnabled.value

        if (!hasOverlayPerm && FloatingMonsterOverlayService.isRunning) {
            FloatingMonsterOverlayService.stopService(this)
        } else if (isOnboardingDone &&
            isOverlayEnabled &&
            hasOverlayPerm &&
            !FloatingMonsterOverlayService.isRunning
        ) {
            FloatingMonsterOverlayService.startService(this)
        }
    }
}

@Composable
fun ReelMonsterAppRoot(repository: ReelRepository) {
    val context = LocalContext.current
    val isInitialized by repository.isInitialized.collectAsState()
    val initError by repository.initError.collectAsState()
    val isOnboardingDone by repository.preferencesManager.isOnboardingCompleted.collectAsState()

    var currentRoute by rememberSaveable { mutableStateOf(Screen.Home.route) }
    val currentScreen = Screen.fromRoute(currentRoute)
    val bottomBarScreen = Screen.fromBottomRoute(currentRoute)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MintBackground)
    ) {
        when {
            !isInitialized -> {
                LoadingState(
                    message = "Waking up Munchie...",
                    modifier = Modifier.fillMaxSize()
                )
            }
            initError != null -> {
                ErrorState(
                    message = initError ?: "Failed to load local tracking database.",
                    onRetry = { repository.initializeSync() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            !isOnboardingDone -> {
                OnboardingScreen(
                    onStartClick = {
                        repository.preferencesManager.setOnboardingCompleted(true)
                        currentRoute = Screen.Home.route
                        if (repository.preferencesManager.isOverlayEnabled.value &&
                            OverlayPermissionHelper.canDrawOverlays(context) &&
                            !FloatingMonsterOverlayService.isRunning
                        ) {
                            FloatingMonsterOverlayService.startService(context)
                        }
                    }
                )
            }
            else -> {
                Scaffold(
                    containerColor = MintBackground,
                    bottomBar = {
                        BottomNavigationBar(
                            currentScreen = bottomBarScreen,
                            onNavigate = { screen -> currentRoute = screen.route }
                        )
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        when (currentScreen) {
                            Screen.Home -> DashboardScreen(
                                repository = repository,
                                onNavigateToStats = { currentRoute = Screen.Stats.route },
                                onNavigateToGoals = { currentRoute = Screen.Goals.route },
                                onNavigateToFocus = { currentRoute = Screen.Focus.route },
                                onNavigateToProgression = { currentRoute = Screen.Progression.route },
                                onNavigateToWeeklyIntelligence = { currentRoute = Screen.WeeklyIntelligence.route }
                            )
                            Screen.Stats -> StatisticsScreen(repository = repository)
                            Screen.History -> HistoryScreen(repository = repository)
                            Screen.Settings -> SettingsScreen(repository = repository)
                            Screen.Goals -> DailyGoalsScreen(
                                repository = repository,
                                onNavigateBack = { currentRoute = Screen.Home.route }
                            )
                            Screen.Focus -> FocusModeScreen(
                                repository = repository,
                                onNavigateBack = { currentRoute = Screen.Home.route }
                            )
                            Screen.Progression -> ProgressionScreen(
                                repository = repository,
                                onNavigateBack = { currentRoute = Screen.Home.route }
                            )
                            Screen.WeeklyIntelligence -> WeeklyIntelligenceScreen(
                                repository = repository,
                                onNavigateBack = { currentRoute = Screen.Home.route }
                            )
                            else -> DashboardScreen(
                                repository = repository,
                                onNavigateToStats = { currentRoute = Screen.Stats.route },
                                onNavigateToGoals = { currentRoute = Screen.Goals.route },
                                onNavigateToFocus = { currentRoute = Screen.Focus.route },
                                onNavigateToProgression = { currentRoute = Screen.Progression.route },
                                onNavigateToWeeklyIntelligence = { currentRoute = Screen.WeeklyIntelligence.route }
                            )
                        }
                    }
                }
            }
        }
    }
}
