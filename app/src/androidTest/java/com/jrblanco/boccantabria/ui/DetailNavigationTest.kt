package com.jrblanco.boccantabria.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.fake.FakeBocRemoteDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.testGraphOverrides
import com.jrblanco.boccantabria.ui.ask.TAG_ASK_BACK
import com.jrblanco.boccantabria.ui.ask.TAG_ASK_SCREEN
import com.jrblanco.boccantabria.ui.detail.TAG_DETAIL_BACK
import com.jrblanco.boccantabria.ui.detail.component.TAG_ACTION_ASK
import com.jrblanco.boccantabria.ui.detail.component.TAG_ACTION_OPEN
import com.jrblanco.boccantabria.ui.detail.component.TAG_DETAIL_HEADER
import com.jrblanco.boccantabria.ui.home.TAG_PUBLICATIONS
import com.jrblanco.boccantabria.core.ui.component.TAG_PUBLICATION_CARD
import com.jrblanco.boccantabria.ui.navigation.BOCantabriaNavHost
import com.jrblanco.boccantabria.ui.navigation.Route
import com.jrblanco.boccantabria.ui.pdf.TAG_PDF_VIEWER_LOADING
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The path this feature adds: bulletin → publication → document.
 *
 * Drives the real graph so the routes, their arguments and the back stack are the ones the
 * application uses. The document itself never arrives —the graph's document repository is the one
 * that fetches nothing— because what is under test is the navigation, not the download.
 */
class DetailNavigationTest {

    @get:Rule(order = 0)
    val koinRule = KoinOverrideRule(testGraphOverrides(FakeBocRemoteDataSource()))

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun a_card_opens_the_publication_and_back_returns_to_the_bulletin() {
        val navController = start()

        composeRule.onAllNodesWithTag(TAG_PUBLICATION_CARD)[0].performClick()
        composeRule.onNodeWithTag(TAG_DETAIL_HEADER).assertIsDisplayed()

        composeRule.onNodeWithTag(TAG_DETAIL_BACK).performClick()

        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TAG_PUBLICATIONS).fetchSemanticsNodes().isNotEmpty()
        }
        // Back leaves the bulletin as the only thing on the stack, exactly as before opening.
        val routes = navController.currentBackStack.value.mapNotNull { it.destination.route }
        assertTrue("quedó algo por debajo del boletín: $routes", routes.none { it.contains(DETAIL) })
    }

    @Test
    fun opening_the_official_pdf_reaches_the_viewer() {
        start()

        composeRule.onAllNodesWithTag(TAG_PUBLICATION_CARD)[0].performClick()
        composeRule.onNodeWithTag(TAG_ACTION_OPEN).performClick()

        // The document never arrives in this graph, so the viewer is honestly still opening.
        composeRule.onNodeWithTag(TAG_PDF_VIEWER_LOADING).assertIsDisplayed()
    }

    /**
     * **011 FR-047**, and the claim the whole lifetime of the conversation rests on.
     *
     * Preguntar stacks **on top** of the detail rather than replacing it, so the detail's entry stays
     * alive while the conversation is used. That is what makes the detail the right place to release
     * the document and discard the conversation: its `onCleared()` is the only pop that means «the
     * visit is over» (011 research.md D-314). If anyone ever navigates here with `popUpTo`, this is
     * what goes red.
     *
     * The back **stack** is what is asserted, not the back **gesture**: driving a real gesture proved
     * impossible to do reliably part-way through a long run — three mechanisms, three different
     * failures — and the gesture is Android's behaviour rather than ours.
     */
    @Test
    fun asking_opens_its_own_screen_and_back_returns_to_the_detail() {
        val navController = start()

        composeRule.onAllNodesWithTag(TAG_PUBLICATION_CARD)[0].performClick()
        composeRule.onNodeWithTag(TAG_ACTION_ASK).performClick()

        composeRule.onNodeWithTag(TAG_ASK_SCREEN).assertIsDisplayed()

        // The detail is still underneath, which is the point.
        val whileAsking = navController.currentBackStack.value.mapNotNull { it.destination.route }
        assertTrue(
            "Preguntar reemplazó al detalle en vez de apilarse encima: $whileAsking",
            whileAsking.any { it.contains(DETAIL) } && whileAsking.any { it.contains(ASK) },
        )

        composeRule.onNodeWithTag(TAG_ASK_BACK).performClick()
        composeRule.onNodeWithTag(TAG_DETAIL_HEADER).assertIsDisplayed()

        val afterBack = navController.currentBackStack.value.mapNotNull { it.destination.route }
        assertTrue("Preguntar sigue en la pila: $afterBack", afterBack.none { it.contains(ASK) })
    }

    private fun start(): NavHostController {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            BOCantabriaTheme {
                BOCantabriaNavHost(navController = navController)
            }
        }
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TAG_PUBLICATION_CARD).fetchSemanticsNodes().isNotEmpty()
        }
        return navController
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        val DETAIL: String = Route.Detail::class.simpleName!!
        val ASK: String = Route.Ask::class.simpleName!!
    }
}
