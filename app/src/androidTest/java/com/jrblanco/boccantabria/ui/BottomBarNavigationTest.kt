package com.jrblanco.boccantabria.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jrblanco.boccantabria.MainActivity
import com.jrblanco.boccantabria.core.ui.component.TAG_COMING_SOON
import com.jrblanco.boccantabria.fake.FakeBocRemoteDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.testGraphOverrides
import com.jrblanco.boccantabria.ui.home.component.TAG_HEADER
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_BAR
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_HOME
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_SAVED
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_SEARCH
import org.junit.Rule
import org.junit.Test

/** Three destinations, all of which lead somewhere. */
class BottomBarNavigationTest {

    @get:Rule(order = 0)
    val koinRule = KoinOverrideRule(testGraphOverrides(FakeBocRemoteDataSource()))

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun the_bar_offers_exactly_three_destinations_and_none_of_them_is_alerts() {
        awaitBar()

        composeRule.onNodeWithTag(TAG_BOTTOM_HOME).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_BOTTOM_SEARCH).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_BOTTOM_SAVED).assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_alerts").assertDoesNotExist()
    }

    @Test
    fun search_and_saved_say_they_are_coming_and_home_comes_back() {
        awaitBar()

        composeRule.onNodeWithTag(TAG_BOTTOM_SEARCH).performClick()
        composeRule.onNodeWithTag(TAG_COMING_SOON).assertIsDisplayed()

        composeRule.onNodeWithTag(TAG_BOTTOM_SAVED).performClick()
        composeRule.onNodeWithTag(TAG_COMING_SOON).assertIsDisplayed()

        composeRule.onNodeWithTag(TAG_BOTTOM_HOME).performClick()
        composeRule.onNodeWithTag(TAG_HEADER).assertIsDisplayed()
    }

    private fun awaitBar() {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TAG_BOTTOM_BAR).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
