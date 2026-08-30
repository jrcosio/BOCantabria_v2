package com.jrblanco.boccantabria.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import com.jrblanco.boccantabria.core.ui.component.TAG_COMING_SOON
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.fake.FakeBocRemoteDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.testGraphOverrides
import com.jrblanco.boccantabria.ui.home.TAG_PUBLICATIONS
import com.jrblanco.boccantabria.ui.home.component.TAG_HEADER
import com.jrblanco.boccantabria.ui.main.MainShell
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_BAR
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_HOME
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_SAVED
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_SEARCH
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Three destinations, all of which lead somewhere. */
class BottomBarNavigationTest {

    @get:Rule(order = 0)
    val koinRule = KoinOverrideRule(testGraphOverrides(FakeBocRemoteDataSource()))

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        composeRule.setContent {
            BOCantabriaTheme {
                MainShell(navController = rememberNavController(), onOpenPublication = {})
            }
        }
        // Waits for the first synchronisation to land. It is not politeness: while the loading
        // placeholders are on screen they pulse for ever, the composition never goes idle, and an
        // assertion that waits for idleness would hang rather than fail.
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TAG_PUBLICATIONS).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun the_bar_offers_exactly_three_destinations_and_none_of_them_is_alerts() {
        composeRule.onNodeWithTag(TAG_BOTTOM_BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_BOTTOM_HOME).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_BOTTOM_SEARCH).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_BOTTOM_SAVED).assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_alerts").assertDoesNotExist()
    }

    @Test
    fun search_and_saved_say_they_are_coming_and_home_comes_back() {
        composeRule.onNodeWithTag(TAG_BOTTOM_SEARCH).performClick()
        composeRule.onNodeWithTag(TAG_COMING_SOON).assertIsDisplayed()

        composeRule.onNodeWithTag(TAG_BOTTOM_SAVED).performClick()
        composeRule.onNodeWithTag(TAG_COMING_SOON).assertIsDisplayed()

        composeRule.onNodeWithTag(TAG_BOTTOM_HOME).performClick()
        composeRule.onNodeWithTag(TAG_HEADER).assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
