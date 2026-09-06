package com.jrblanco.boccantabria.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jrblanco.boccantabria.MainActivity
import com.jrblanco.boccantabria.fake.FakeConnectivityDataSource
import com.jrblanco.boccantabria.fake.FakeRemoteConfigDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.FakeBocRemoteDataSource
import com.jrblanco.boccantabria.fake.testGraphOverrides
import com.jrblanco.boccantabria.ui.home.component.TAG_HOME_TOP_BAR
import com.jrblanco.boccantabria.ui.splash.TAG_SPLASH_ROOT
import org.junit.Rule
import org.junit.Test

/**
 * Runs the real activity: the startup reaches the main content on its own, with nobody touching
 * the screen.
 */
class SplashNavigationTest {

    private val connectivity = FakeConnectivityDataSource(online = true)
    private val remoteConfig = FakeRemoteConfigDataSource()

    @get:Rule(order = 0)
    val koinRule = KoinOverrideRule(testGraphOverrides(FakeBocRemoteDataSource()))

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun the_startup_reaches_the_main_content_on_its_own() {
        composeRule.onNodeWithTag(TAG_SPLASH_ROOT).assertIsDisplayed()

        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(HOME_TITLE).fetchSemanticsNodes().isNotEmpty()
        }
        // Anchored to the top bar. Since feature 013 the sections panel also carries the name, and a
        // closed drawer is still composed, so matching the words alone finds two nodes and fails on
        // the ambiguity instead of on what this test is about.
        composeRule.onNode(
            hasText(HOME_TITLE) and hasAnyAncestor(hasTestTag(TAG_HOME_TOP_BAR)),
        ).assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val HOME_TITLE = "BOC Cantabria"
    }
}
