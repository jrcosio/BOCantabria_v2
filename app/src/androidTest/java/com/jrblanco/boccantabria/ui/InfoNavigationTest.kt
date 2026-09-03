package com.jrblanco.boccantabria.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.jrblanco.boccantabria.core.ui.component.TAG_PUBLICATION_CARD
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.fake.FakeBocRemoteDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.testGraphOverrides
import com.jrblanco.boccantabria.ui.home.component.TAG_INFO
import com.jrblanco.boccantabria.ui.info.TAG_INFO_BACK
import com.jrblanco.boccantabria.ui.info.TAG_INFO_SCREEN
import com.jrblanco.boccantabria.ui.navigation.BOCantabriaNavHost
import com.jrblanco.boccantabria.ui.navigation.Route
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_BAR
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InfoNavigationTest {

    @get:Rule(order = 0)
    val koinRule = KoinOverrideRule(testGraphOverrides(FakeBocRemoteDataSource()))

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun information_opens_outside_the_main_shell_and_back_returns_to_the_bulletin() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            BOCantabriaTheme {
                BOCantabriaNavHost(navController = navController)
            }
        }
        awaitBulletin()

        composeRule.onNodeWithTag(TAG_INFO).performClick()
        composeRule.onNodeWithTag(TAG_INFO_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_BOTTOM_BAR).assertDoesNotExist()

        composeRule.onNodeWithTag(TAG_INFO_BACK).performClick()
        awaitBulletin()

        val routes = navController.currentBackStack.value.mapNotNull { it.destination.route }
        assertTrue(routes.none { it.contains(Route.Info::class.simpleName!!) })
    }

    private fun awaitBulletin() {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TAG_PUBLICATION_CARD).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
