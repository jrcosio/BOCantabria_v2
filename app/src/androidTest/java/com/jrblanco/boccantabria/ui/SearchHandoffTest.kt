package com.jrblanco.boccantabria.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.compose.rememberNavController
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.fake.FakeBocRemoteDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.testGraphOverrides
import com.jrblanco.boccantabria.ui.home.TAG_HOME_SEARCH_GLOBALLY
import com.jrblanco.boccantabria.ui.home.TAG_PUBLICATIONS
import com.jrblanco.boccantabria.ui.home.component.TAG_HOME_SEARCH_FIELD
import com.jrblanco.boccantabria.ui.home.component.TAG_SEARCH
import com.jrblanco.boccantabria.ui.main.MainShell
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_HOME
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_SEARCH
import com.jrblanco.boccantabria.ui.search.TAG_SEARCH_SCREEN
import com.jrblanco.boccantabria.ui.search.component.TAG_SEARCH_FIELD
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The bridge between the two searches, and the one failure that would be invisible.
 *
 * The bottom bar navigates with `saveState`/`restoreState`, so if the hand-off navigated the same
 * way, the saved state of the Buscar tab would overwrite the argument and the term would be lost —
 * with no error and no exception, just a search screen that opened empty. The last test here walks
 * exactly the sequence that would expose it: search once in Buscar, go back to Inicio, search for
 * something else, hand it over.
 */
class SearchHandoffTest {

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
        // Espera a que aterrice la primera sincronización: mientras están los esqueletos, la
        // composición no llega nunca a reposo y una aserción que lo espere se colgaría.
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TAG_PUBLICATIONS).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun a_search_with_no_matches_in_the_edition_offers_the_way_out() {
        searchInTheBulletin("expropiacion forzosa de nada")

        composeRule.onNodeWithTag(TAG_HOME_SEARCH_GLOBALLY).assertIsDisplayed()
    }

    @Test
    fun the_way_out_lands_on_buscar_with_the_term_already_typed() {
        searchInTheBulletin("expropiacion forzosa de nada")

        composeRule.onNodeWithTag(TAG_HOME_SEARCH_GLOBALLY).performClick()

        composeRule.onNodeWithTag(TAG_SEARCH_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("expropiacion forzosa de nada").assertIsDisplayed()
    }

    /**
     * The sequence that a restored state would break. If this fails, the hand-off is navigating
     * with `restoreState` and the saved tab is winning over the argument.
     */
    @Test
    fun a_second_hand_off_brings_the_new_term_and_not_the_one_buscar_had() {
        // Primero, una consulta cualquiera en Buscar, para que la pestaña tenga estado guardado.
        composeRule.onNodeWithTag(TAG_BOTTOM_SEARCH).performClick()
        composeRule.onNodeWithTag(TAG_SEARCH_FIELD).performTextReplacement("subvenciones")
        composeRule.onNodeWithTag(TAG_BOTTOM_HOME).performClick()

        searchInTheBulletin("expropiacion forzosa de nada")
        composeRule.onNodeWithTag(TAG_HOME_SEARCH_GLOBALLY).performClick()

        composeRule.onNodeWithText("expropiacion forzosa de nada").assertIsDisplayed()
        composeRule.onNodeWithText("subvenciones").assertDoesNotExist()
    }

    private fun searchInTheBulletin(term: String) {
        composeRule.onNodeWithTag(TAG_SEARCH).performClick()
        composeRule.onNodeWithTag(TAG_HOME_SEARCH_FIELD).performTextReplacement(term)
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TAG_HOME_SEARCH_GLOBALLY).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
