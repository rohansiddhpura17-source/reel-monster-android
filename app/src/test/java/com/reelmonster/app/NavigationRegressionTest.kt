package com.reelmonster.app

import com.reelmonster.app.presentation.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test suite verifying navigation safety, class initialization order,
 * route resolution, and fallback behavior for saved/restored navigation states.
 */
class NavigationRegressionTest {

    @Test
    fun testClassInitializationOrderSafeFromNulls() {
        // Accessing Screen.Home.route BEFORE accessing Screen.bottomNavItems
        // previously triggered JVM circular classloading which caused bottomNavItems
        // to contain null references. This test verifies that bottomNavItems is never null.
        val homeRoute = Screen.Home.route
        assertEquals("home", homeRoute)

        val bottomItems = Screen.bottomNavItems
        assertEquals(4, bottomItems.size)
        assertFalse("bottomNavItems must not contain null elements", bottomItems.any { it == null })

        bottomItems.forEach { screen ->
            assertNotNull("Screen destination must not be null", screen)
            assertNotNull("Route must not be null", screen.route)
            assertNotNull("Title must not be null", screen.title)
            assertNotNull("Icon must not be null for bottom nav items", screen.icon)
            assertTrue("Route must not be blank", screen.route.isNotBlank())
        }
    }

    @Test
    fun testFromRouteWithAllKnownRoutes() {
        assertEquals(Screen.Home, Screen.fromRoute("home"))
        assertEquals(Screen.Stats, Screen.fromRoute("stats"))
        assertEquals(Screen.History, Screen.fromRoute("history"))
        assertEquals(Screen.Settings, Screen.fromRoute("settings"))
        assertEquals(Screen.Onboarding, Screen.fromRoute("onboarding"))
        assertEquals(Screen.GrowthGallery, Screen.fromRoute("growth_gallery"))
        assertEquals(Screen.ModeSelection, Screen.fromRoute("mode_selection"))
        assertEquals(Screen.PermissionSetup, Screen.fromRoute("permission_setup"))
        assertEquals(Screen.Goals, Screen.fromRoute("goals"))
    }

    @Test
    fun testFromRouteFallbackOnCorruptedOrMissingState() {
        // Null or missing restored state -> Fallback to Home (Dashboard)
        assertEquals(Screen.Home, Screen.fromRoute(null))

        // Empty or whitespace restored state -> Fallback to Home (Dashboard)
        assertEquals(Screen.Home, Screen.fromRoute(""))
        assertEquals(Screen.Home, Screen.fromRoute("   "))

        // Corrupted or unrecognized route -> Fallback to Home (Dashboard)
        assertEquals(Screen.Home, Screen.fromRoute("corrupted_state_98765"))
        assertEquals(Screen.Home, Screen.fromRoute("non_existent_screen"))
        assertEquals(Screen.Home, Screen.fromRoute("undefined"))
    }

    @Test
    fun testFromBottomRouteLookup() {
        assertEquals(Screen.Home, Screen.fromBottomRoute("home"))
        assertEquals(Screen.Stats, Screen.fromBottomRoute("stats"))
        assertEquals(Screen.History, Screen.fromBottomRoute("history"))
        assertEquals(Screen.Settings, Screen.fromBottomRoute("settings"))

        // Non-bottom screens fall back to Home
        assertEquals(Screen.Home, Screen.fromBottomRoute("onboarding"))
        assertEquals(Screen.Home, Screen.fromBottomRoute("invalid_route"))
        assertEquals(Screen.Home, Screen.fromBottomRoute(null))
    }

    @Test
    fun testAllScreensHaveUniqueNonBlankRoutes() {
        val all = Screen.allScreens
        val routes = all.map { it.route }
        val uniqueRoutes = routes.toSet()

        assertEquals("Every screen must have a distinct route", routes.size, uniqueRoutes.size)
        routes.forEach { route ->
            assertTrue("Route must not be blank", route.isNotBlank())
        }
    }
}
