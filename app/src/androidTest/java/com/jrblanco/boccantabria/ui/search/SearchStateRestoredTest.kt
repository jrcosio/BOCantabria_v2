package com.jrblanco.boccantabria.ui.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.compose.rememberNavController
import com.jrblanco.boccantabria.core.ui.component.TAG_PUBLICATION_CARD
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.fake.FakeBocRemoteDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.testGraphOverrides
import com.jrblanco.boccantabria.ui.detail.TAG_DETAIL_BACK
import com.jrblanco.boccantabria.ui.detail.component.TAG_DETAIL_HEADER
import com.jrblanco.boccantabria.ui.home.TAG_PUBLICATIONS
import com.jrblanco.boccantabria.ui.navigation.BOCantabriaNavHost
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_HOME
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_SEARCH
import com.jrblanco.boccantabria.ui.search.component.TAG_SEARCH_FIELD
import org.junit.Rule
import org.junit.Test

/**
 * Coming back and finding it as you left it.
 *
 * Two different mechanisms, which is why both are here. Opening a publication does **not** destroy
 * the search: the detail lives in the outer graph, so the Buscar entry is only stopped and its view
 * model survives. Changing tab **does** destroy it — the bottom bar navigates with `saveState` — and
 * what brings the query back there is the saved state handle.
 *
 * Drives the real graph so the routes and the back stack are the ones the application uses.
 */
class SearchStateRestoredTest {

    @get:Rule(order = 0)
    val koinRule = KoinOverrideRule(testGraphOverrides(FakeBocRemoteDataSource()))

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun opening_a_result_and_coming_back_keeps_the_query() {
        start()
        searchFor("ayuntamiento")

        composeRule.onAllNodesWithTag(TAG_PUBLICATION_CARD)[0].performClick()
        composeRule.onNodeWithTag(TAG_DETAIL_HEADER).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_DETAIL_BACK).performClick()

        composeRule.onNodeWithTag(TAG_SEARCH_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("ayuntamiento").assertIsDisplayed()
    }

    /**
     * The one the saved state handle is there for: the bottom bar destroys the view model, so a
     * query held only in memory would be gone by the time somebody came back.
     */
    @Test
    fun going_to_the_bulletin_and_back_keeps_the_query() {
        start()
        searchFor("ayuntamiento")

        composeRule.onNodeWithTag(TAG_BOTTOM_HOME).performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TAG_PUBLICATIONS).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TAG_BOTTOM_SEARCH).performClick()

        composeRule.onNodeWithText("ayuntamiento").assertIsDisplayed()
    }

    /**
     * Goes to Buscar, types, and waits for **its** result list.
     *
     * Two things are deliberate here.
     *
     * `TAG_PUBLICATION_CARD` is on the bulletin and on the saved list too, so waiting for it would
     * say less than it looks: a card this screen never drew would satisfy it. The result list's own
     * tag exists nowhere else.
     *
     * And each step is asserted separately, because a `waitUntil` that runs out of time says
     * nothing about **what** broke. If the results never arrive, the failure now names which of the
     * three possible causes it was: the screen never appeared, the text never got in, or the search
     * ran and came back with nothing.
     */
    private fun searchFor(term: String) {
        composeRule.onNodeWithTag(TAG_BOTTOM_SEARCH).performClick()
        composeRule.onNodeWithTag(TAG_SEARCH_SCREEN).assertIsDisplayed()

        composeRule.onNodeWithTag(TAG_SEARCH_FIELD).performTextReplacement(term)
        composeRule.onNodeWithTag(TAG_SEARCH_FIELD).assertTextContains(term)

        runCatching {
            composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithTag(TAG_SEARCH_RESULTS).fetchSemanticsNodes().isNotEmpty()
            }
        }.onFailure {
            val stillInitial = composeRule.onAllNodesWithTag(TAG_SEARCH_INITIAL).fetchSemanticsNodes().isNotEmpty()
            val cameBackEmpty = composeRule.onAllNodesWithTag(TAG_SEARCH_EMPTY).fetchSemanticsNodes().isNotEmpty()
            throw AssertionError(
                "Buscar no mostró resultados para «$term» en ${TIMEOUT_MILLIS} ms. " +
                    "La pantalla estaba montada y el campo tenía el texto, así que: " +
                    when {
                        // La consulta ni siquiera se lanzó: el modelo no vio el texto.
                        stillInitial -> "seguía en el estado inicial — la consulta no llegó a lanzarse."
                        // La consulta se lanzó y el almacén devolvió cero: la base de datos que
                        // consulta Buscar no es la que llenó la sincronización.
                        cameBackEmpty -> "mostraba el estado vacío — la consulta se lanzó y el almacén devolvió cero."
                        else -> "no mostraba ni resultados, ni vacío, ni inicial."
                    },
            )
        }
    }

    private fun start() {
        composeRule.setContent {
            BOCantabriaTheme {
                BOCantabriaNavHost(navController = rememberNavController())
            }
        }
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TAG_PUBLICATION_CARD).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        /**
         * Generous, but **not** because the deadline was ever the problem.
         *
         * That was the first diagnosis and it was wrong, so it is written down rather than quietly
         * replaced: this class timed out at ten seconds inside the full run, the timeout was raised
         * to forty-five, and it timed out at forty-five. No local query takes that long. What was
         * actually happening is in [searchFor] — the text was going into the field before the
         * navigation to Buscar had settled, so it landed on a composition that was then discarded
         * and the view model never saw it. Under the load of a full run that window widened, which
         * is why the class passed on its own and failed in company.
         *
         * The margin stays wide because there is nothing to gain from a tight one here: two
         * complete runs of the suite took 3 h 24 m and 1 h 34 m on the same emulator, so the
         * machine's own speed varies by a factor of two and a short deadline would only measure it.
         */
        const val TIMEOUT_MILLIS = 45_000L
    }
}
