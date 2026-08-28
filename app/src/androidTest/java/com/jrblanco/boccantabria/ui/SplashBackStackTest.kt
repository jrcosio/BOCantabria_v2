package com.jrblanco.boccantabria.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.fake.FakeConnectivityDataSource
import com.jrblanco.boccantabria.fake.FakeRemoteConfigDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.startupGraphOverrides
import com.jrblanco.boccantabria.ui.navigation.BOCantabriaNavHost
import com.jrblanco.boccantabria.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * Covers FR-007: once the main content is reached, the cover must no longer be reachable by going
 * back.
 *
 * Asserts on the navigation back stack rather than on the activity finishing. Driving a real back
 * gesture proved impossible to do reliably part-way through a long instrumented run — three
 * mechanisms were tried and each failed for its own reason, while the diagnosis showed the
 * navigation itself was always correct. What this feature controls is the back stack: with nothing
 * left below the main content, closing the application on back is Android's own behaviour, not
 * ours. That end result was confirmed by hand on the emulator and is a manual step in
 * `quickstart.md`.
 *
 * Uses a blank host activity so the test can drive the navigation graph itself: the real
 * `MainActivity` already sets its content, and calling `setContent` on top of it throws.
 */
class SplashBackStackTest {

    private val connectivity = FakeConnectivityDataSource(online = true)
    private val remoteConfig = FakeRemoteConfigDataSource()

    @get:Rule(order = 0)
    val koinRule = KoinOverrideRule(startupGraphOverrides(connectivity, remoteConfig))

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun the_cover_is_gone_from_the_back_stack_once_the_startup_completes() {
        lateinit var navController: NavHostController

        composeRule.setContent {
            navController = rememberNavController()
            BOCantabriaTheme {
                BOCantabriaNavHost(navController = navController)
            }
        }

        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(HOME_TITLE).fetchSemanticsNodes().isNotEmpty()
        }

        val destinations = navController.currentBackStack.value.mapNotNull { it.destination.route }

        // Returning to a cover whose work is already done would be a dead end for the user.
        assertFalse(
            "The cover is still on the back stack: $destinations",
            destinations.any { it.endsWith(SPLASH_ROUTE_SUFFIX) },
        )
        assertEquals("Only the main content should remain: $destinations", 1, destinations.size)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val HOME_TITLE = "BOCantabria"
        val SPLASH_ROUTE_SUFFIX: String = Route.Splash::class.simpleName!!
    }
}
