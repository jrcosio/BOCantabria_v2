package com.jrblanco.boccantabria.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.domain.model.InAppAlert
import com.jrblanco.boccantabria.domain.repository.InAppAlertStore
import com.jrblanco.boccantabria.fake.FakeBocRemoteDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.testGraphOverrides
import com.jrblanco.boccantabria.ui.alerts.TAG_ALERTS_NEWS_EMPTY
import com.jrblanco.boccantabria.ui.alerts.TAG_ALERTS_SCREEN
import com.jrblanco.boccantabria.ui.home.TAG_PUBLICATIONS
import com.jrblanco.boccantabria.ui.main.MainShell
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_ALERTS
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_SEARCH
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

/**
 * The in-app message (FR-050, FR-051): shown with «VER» wherever the person is except on the alerts,
 * consumed either way, and never marking anything read.
 */
class AlertSnackbarTest {

    @get:Rule(order = 0)
    val koinRule = KoinOverrideRule(testGraphOverrides(FakeBocRemoteDataSource()))

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val store: InAppAlertStore get() = GlobalContext.get().get()

    @Before
    fun setUp() {
        composeRule.setContent {
            BOCantabriaTheme {
                MainShell(navController = rememberNavController(), onOpenPublication = {})
            }
        }
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TAG_PUBLICATIONS).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun outside_alerts_the_message_shows_with_ver_and_ver_lands_on_the_news() {
        composeRule.onNodeWithTag(TAG_BOTTOM_SEARCH).performClick()

        composeRule.runOnUiThread { store.publish(InAppAlert(publicationCount = 1, ruleName = "Ganadería")) }

        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("VER").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Una nueva publicación coincide con «Ganadería»").assertIsDisplayed()
        // Consumed as soon as it is shown: dismissing it later loses nothing, and reads nothing.
        assertNull(runBlocking { store.observePending().first() })

        composeRule.onNodeWithText("VER").performClick()

        composeRule.onNodeWithTag(TAG_ALERTS_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ALERTS_NEWS_EMPTY).assertIsDisplayed()
    }

    @Test
    fun on_the_alerts_the_message_is_swallowed_and_consumed() {
        composeRule.onNodeWithTag(TAG_BOTTOM_ALERTS).performClick()
        composeRule.onNodeWithTag(TAG_ALERTS_SCREEN).assertIsDisplayed()

        composeRule.runOnUiThread { store.publish(InAppAlert(publicationCount = 2, ruleName = null)) }
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            runBlocking { store.observePending().first() } == null
        }

        composeRule.onAllNodesWithText("VER").fetchSemanticsNodes().let { nodes ->
            if (nodes.isNotEmpty()) throw AssertionError("el mensaje interno no debe mostrarse dentro de Avisos")
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
