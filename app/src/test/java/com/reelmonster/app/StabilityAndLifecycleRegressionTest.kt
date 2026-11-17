package com.reelmonster.app

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.core.util.OverlayPermissionHelper
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.MunchieStage
import com.reelmonster.app.domain.model.OverlayState
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import com.reelmonster.app.presentation.navigation.Screen
import com.reelmonster.app.service.overlay.FloatingMonsterOverlayService
import com.reelmonster.app.service.overlay.OverlayLifecycleOwner
import com.reelmonster.app.service.tracking.ReelAccessibilityService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * Comprehensive regression test suite covering the 14 critical Android stability,
 * startup, navigation, permission, and overlay service lifecycle scenarios.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StabilityAndLifecycleRegressionTest {

    private lateinit var context: Context
    private lateinit var dbHelper: ReelDbHelper
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var repository: ReelRepository

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        preferencesManager = UserPreferencesManager(context)
        preferencesManager.clearPreferences()
        dbHelper = ReelDbHelper(context)
        dbHelper.clearAllData()
        repository = ReelRepository(dbHelper, preferencesManager, preloadOnInit = true)
        ReelMonsterApp.setRepositoryForTesting(repository)
        ShadowSettings.setCanDrawOverlays(false)
        FloatingMonsterOverlayService.stopService(context)
    }

    @After
    fun tearDown() {
        FloatingMonsterOverlayService.stopService(context)
        ShadowSettings.setCanDrawOverlays(false)
        ReelMonsterApp.setRepositoryForTesting(null)
        dbHelper.close()
    }

    // 1. Fresh application startup
    @Test
    fun test01_FreshApplicationStartupNonBlockingInitialization() {
        val deferredRepo = ReelRepository(
            dbHelper = dbHelper,
            preferencesManager = preferencesManager,
            preloadOnInit = false
        )
        // Before background init completes, repository has safe defaults and zero crash
        assertFalse(deferredRepo.isInitialized.value)
        assertNull(deferredRepo.initError.value)
        assertEquals(0, deferredRepo.todayCount.value)
        assertEquals(MunchieStage.BABY, deferredRepo.munchieStage.value)

        // Complete initialization
        deferredRepo.initializeSync()
        assertTrue(deferredRepo.isInitialized.value)
        assertNull(deferredRepo.initError.value)
    }

    // 2. Dashboard navigation initialization
    @Test
    fun test02_DashboardNavigationInitialization() {
        val defaultRoute = Screen.Home.route
        val resolved = Screen.fromBottomRoute(defaultRoute)
        assertEquals(Screen.Home, resolved)
        assertEquals("home", resolved.route)
        assertEquals("Home", resolved.title)
        assertNotNull(resolved.icon)
    }

    // 3. Null or invalid restored navigation state fallback to Dashboard
    @Test
    fun test03_NullOrInvalidRestoredNavigationStateFallback() {
        assertEquals(Screen.Home, Screen.fromBottomRoute(null))
        assertEquals(Screen.Home, Screen.fromBottomRoute(""))
        assertEquals(Screen.Home, Screen.fromBottomRoute("   "))
        assertEquals(Screen.Home, Screen.fromBottomRoute("onboarding"))
        assertEquals(Screen.Home, Screen.fromBottomRoute("growth_gallery"))
        assertEquals(Screen.Home, Screen.fromBottomRoute("corrupted_bundle_route"))
    }

    // 4. Overlay permission denied
    @Test
    fun test04_OverlayPermissionDeniedDoesNotCrashOrStartOverlay() {
        ShadowSettings.setCanDrawOverlays(false)
        assertFalse(OverlayPermissionHelper.canDrawOverlays(context))

        // Attempt to start via companion helper
        FloatingMonsterOverlayService.startService(context)
        assertFalse(FloatingMonsterOverlayService.isRunning)
        assertEquals(OverlayState.PERMISSION_MISSING, FloatingMonsterOverlayService.overlayState.value)

        // Attempt direct service start command while permission is denied
        val controller = Robolectric.buildService(FloatingMonsterOverlayService::class.java)
        val service = controller.create().get()
        val startIntent = Intent(context, FloatingMonsterOverlayService::class.java).apply {
            action = FloatingMonsterOverlayService.ACTION_START
        }
        service.onStartCommand(startIntent, 0, 1)

        assertFalse(service.isViewAttachedForTesting())
        assertNull(service.getRootContainerForTesting())
        assertEquals(OverlayState.PERMISSION_MISSING, FloatingMonsterOverlayService.overlayState.value)
        controller.destroy()
    }

    // 5. Overlay permission granted (including ViewTreeLifecycleOwner verification on rootContainer!)
    @Test
    fun test05_OverlayPermissionGrantedAttachesRootContainerWithViewTreeOwners() {
        ShadowSettings.setCanDrawOverlays(true)
        assertTrue(OverlayPermissionHelper.canDrawOverlays(context))

        val controller = Robolectric.buildService(FloatingMonsterOverlayService::class.java)
        val service = controller.create().get()
        val startIntent = Intent(context, FloatingMonsterOverlayService::class.java).apply {
            action = FloatingMonsterOverlayService.ACTION_START
        }
        service.onStartCommand(startIntent, 0, 1)

        assertTrue(FloatingMonsterOverlayService.isRunning)
        assertTrue(FloatingMonsterOverlayService.isRunningFlow.value)
        assertTrue(service.isViewAttachedForTesting())
        assertEquals(OverlayState.COMPACT, FloatingMonsterOverlayService.overlayState.value)

        val rootContainer = service.getRootContainerForTesting()
        assertNotNull("Root FrameLayout container must be attached", rootContainer)

        // Direct regression check for:
        // IllegalStateException: ViewTreeLifecycleOwner not found from android.widget.FrameLayout
        assertNotNull(
            "rootContainer MUST have ViewTreeLifecycleOwner set for Compose WindowRecomposer",
            rootContainer!!.findViewTreeLifecycleOwner()
        )
        assertNotNull(
            "rootContainer MUST have ViewTreeSavedStateRegistryOwner set",
            rootContainer.findViewTreeSavedStateRegistryOwner()
        )
        assertNotNull(
            "rootContainer MUST have ViewTreeViewModelStoreOwner set",
            rootContainer.findViewTreeViewModelStoreOwner()
        )

        controller.destroy()
    }

    // 6. Returning from Android Settings
    @Test
    fun test06_ReturningFromAndroidSettingsUpdatesPermissionAndServiceState() {
        // Initially permission is missing
        ShadowSettings.setCanDrawOverlays(false)
        assertFalse(OverlayPermissionHelper.canDrawOverlays(context))

        // User opens Android Settings and grants permission
        ShadowSettings.setCanDrawOverlays(true)
        val nowHasOverlay = OverlayPermissionHelper.canDrawOverlays(context)
        assertTrue(nowHasOverlay)

        // Returning to app starts service cleanly
        val controller = Robolectric.buildService(FloatingMonsterOverlayService::class.java)
        val service = controller.create().get()
        service.onStartCommand(
            Intent(context, FloatingMonsterOverlayService::class.java).apply {
                action = FloatingMonsterOverlayService.ACTION_START
            },
            0,
            1
        )
        assertTrue(service.isViewAttachedForTesting())

        // If user later revokes permission in Settings and returns, service stops cleanly
        ShadowSettings.setCanDrawOverlays(false)
        FloatingMonsterOverlayService.stopService(context)
        controller.destroy()
        assertFalse(FloatingMonsterOverlayService.isRunning)
    }

    // 7. Repeated permission checks
    @Test
    fun test07_RepeatedPermissionChecksAreIdempotentAndSafe() {
        ShadowSettings.setCanDrawOverlays(true)
        repeat(50) {
            assertTrue(OverlayPermissionHelper.canDrawOverlays(context))
            assertFalse(ReelAccessibilityService.isAccessibilityPermissionGranted(context))
        }
    }

    // 8. Overlay service started once (duplicate start commands do not attach second view)
    @Test
    fun test08_OverlayServiceStartedOnceDoesNotAttachDuplicateViews() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildService(FloatingMonsterOverlayService::class.java)
        val service = controller.create().get()
        val startIntent = Intent(context, FloatingMonsterOverlayService::class.java).apply {
            action = FloatingMonsterOverlayService.ACTION_START
        }

        service.onStartCommand(startIntent, 0, 1)
        val firstContainer = service.getRootContainerForTesting()
        assertNotNull(firstContainer)

        // Subsequent start commands must reuse the existing attached container
        service.onStartCommand(startIntent, 0, 2)
        service.onStartCommand(startIntent, 0, 3)
        val secondContainer = service.getRootContainerForTesting()

        assertSame("Duplicate ACTION_START must not create or attach a second overlay container", firstContainer, secondContainer)
        controller.destroy()
    }

    // 9. Overlay service stopped safely
    @Test
    fun test09_OverlayServiceStoppedSafelyWithoutKillingApp() {
        ShadowSettings.setCanDrawOverlays(true)
        preferencesManager.setOverlayEnabled(true)

        val controller = Robolectric.buildService(FloatingMonsterOverlayService::class.java)
        val service = controller.create().get()
        service.onStartCommand(
            Intent(context, FloatingMonsterOverlayService::class.java).apply {
                action = FloatingMonsterOverlayService.ACTION_START
            },
            0,
            1
        )
        assertTrue(service.isViewAttachedForTesting())

        // Stop via ACTION_STOP
        service.onStartCommand(
            Intent(context, FloatingMonsterOverlayService::class.java).apply {
                action = FloatingMonsterOverlayService.ACTION_STOP
            },
            0,
            2
        )

        assertFalse(FloatingMonsterOverlayService.isRunning)
        assertFalse(FloatingMonsterOverlayService.isRunningFlow.value)
        assertFalse(service.isViewAttachedForTesting())
        assertNull(service.getRootContainerForTesting())
        assertEquals(OverlayState.HIDDEN, FloatingMonsterOverlayService.overlayState.value)

        // Calling stopService again when already stopped must be a safe no-op
        FloatingMonsterOverlayService.stopService(context)
        controller.destroy()
    }

    // 10. Accessibility service disabled
    @Test
    fun test10_AccessibilityServiceDisabledFallsBackToManualTracking() {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ""
        )
        assertFalse(ReelAccessibilityService.isAccessibilityPermissionGranted(context))

        // Level 1 manual tracking continues working seamlessly
        val logged = repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL)
        assertTrue(logged)
        assertEquals(1, repository.todayCount.value)
    }

    // 11. Accessibility service enabled (both full and short ComponentName formats)
    @Test
    fun test11_AccessibilityServiceEnabledRecognizesFullAndShortComponentNames() {
        val pkg = context.packageName
        val cls = ReelAccessibilityService::class.java.name

        // Full component string
        val fullList = "com.other.app/.SomeService:$pkg/$cls"
        assertTrue(
            ReelAccessibilityService.isComponentInEnabledServices(
                enabledServices = fullList,
                packageName = pkg,
                className = cls
            )
        )

        // Shortened relative component string (common on Samsung One UI / Android 14+)
        val relativeSuffix = cls.removePrefix(pkg)
        val shortList = "com.other.app/.SomeService:$pkg/$relativeSuffix"
        assertTrue(
            ReelAccessibilityService.isComponentInEnabledServices(
                enabledServices = shortList,
                packageName = pkg,
                className = cls
            )
        )

        // Unrelated services only
        assertFalse(
            ReelAccessibilityService.isComponentInEnabledServices(
                enabledServices = "com.other.app/.SomeService",
                packageName = pkg,
                className = cls
            )
        )
    }

    // 12. Database initialization and persistence
    @Test
    fun test12_DatabaseInitializationAndPersistenceAcrossRepositoryRecreations() {
        repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL, force = true)
        repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL, force = true)
        repository.logReel(TargetApp.YOUTUBE, TrackingMode.LEVEL_2_AUTOMATIC, force = true)

        assertEquals(3, repository.todayCount.value)
        assertEquals(Pair(2, 1), repository.platformBreakdown.value)

        // Recreate repository with preloadOnInit = false then initializeSync()
        val newRepo = ReelRepository(dbHelper, preferencesManager, preloadOnInit = false)
        newRepo.initializeSync()

        assertTrue(newRepo.isInitialized.value)
        assertNull(newRepo.initError.value)
        assertEquals(3, newRepo.todayCount.value)
        assertEquals(Pair(2, 1), newRepo.platformBreakdown.value)
        assertEquals(3, newRepo.recentEvents.value.size)
    }

    // 13. Pause/resume behavior
    @Test
    fun test13_PauseAndResumeBehaviorAcrossRepositoryAndOverlayService() = runTest {
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildService(FloatingMonsterOverlayService::class.java)
        val service = controller.create().get()
        service.onStartCommand(
            Intent(context, FloatingMonsterOverlayService::class.java).apply {
                action = FloatingMonsterOverlayService.ACTION_START
            },
            0,
            1
        )
        assertEquals(OverlayState.COMPACT, FloatingMonsterOverlayService.overlayState.value)

        // Pause via service action
        service.onStartCommand(
            Intent(context, FloatingMonsterOverlayService::class.java).apply {
                action = FloatingMonsterOverlayService.ACTION_PAUSE
            },
            0,
            2
        )
        assertTrue(repository.isTrackingPaused.first { it })

        // Resume via service action
        service.onStartCommand(
            Intent(context, FloatingMonsterOverlayService::class.java).apply {
                action = FloatingMonsterOverlayService.ACTION_RESUME
            },
            0,
            3
        )
        assertFalse(repository.isTrackingPaused.first { !it })
        controller.destroy()
    }

    // 14. App process recreation & OverlayLifecycleOwner repeated transitions
    @Test
    fun test14_OverlayLifecycleOwnerRepeatedTransitionsDoNotThrow() {
        val owner = OverlayLifecycleOwner()

        // Calling onCreate twice must not crash with "SavedStateRegistry was already restored"
        owner.onCreate()
        owner.onCreate()
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)

        owner.onStart()
        assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)

        // Screen off -> onStop, Screen on -> onStart
        owner.onPause()
        owner.onStop()
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)

        owner.onStart()
        assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)

        // Destroy twice must not crash
        owner.onDestroy()
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        owner.onDestroy()
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }
}
