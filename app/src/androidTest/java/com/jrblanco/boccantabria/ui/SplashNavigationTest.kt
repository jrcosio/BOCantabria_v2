package com.jrblanco.boccantabria.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.jrblanco.boccantabria.MainActivity
import com.jrblanco.boccantabria.fake.FakeConnectivityDataSource
import com.jrblanco.boccantabria.fake.FakeRemoteConfigDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.startupGraphOverrides
import com.jrblanco.boccantabria.ui.splash.TAG_SPLASH_ROOT
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The startup reaches the main content on its own, and the cover is gone from the back stack.
 */
class SplashNavigationTest {

    private val connectivity = FakeConnectivityDataSource(online = true)
    private val remoteConfig = FakeRemoteConfigDataSource()

    @get:Rule(order = 0)
    val koinRule = KoinOverrideRule(startupGraphOverrides(connectivity, remoteConfig))

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun the_startup_reaches_the_main_content_on_its_own() {
        composeRule.onNodeWithTag(TAG_SPLASH_ROOT).assertIsDisplayed()

        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(HOME_TITLE).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(HOME_TITLE).assertIsDisplayed()
    }

    @Test
    fun back_from_the_main_content_closes_the_app_instead_of_returning_to_the_cover() {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(HOME_TITLE).fetchSemanticsNodes().isNotEmpty()
        }

        InstrumentationRegistry.getInstrumentation().apply {
            runOnMainSync { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
            waitForIdleSync()
        }

        // Returning to a cover whose work is already done would be a dead end for the user.
        assertTrue(composeRule.activity.isFinishing || composeRule.activity.isDestroyed)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val HOME_TITLE = "BOCantabria"
    }
}
