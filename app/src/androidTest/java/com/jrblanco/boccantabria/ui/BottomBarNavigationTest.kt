package com.jrblanco.boccantabria.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.ui.alerts.TAG_ALERTS_SCREEN
import com.jrblanco.boccantabria.fake.FakeBocRemoteDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.testGraphOverrides
import com.jrblanco.boccantabria.ui.home.TAG_PUBLICATIONS
import com.jrblanco.boccantabria.ui.home.component.TAG_HEADER
import com.jrblanco.boccantabria.ui.main.MainShell
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_ALERTS
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_BAR
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_HOME
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_SAVED
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_SEARCH
import com.jrblanco.boccantabria.ui.saved.TAG_SAVED_EMPTY
import com.jrblanco.boccantabria.ui.search.TAG_SEARCH_INITIAL
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Four destinations, all of which lead somewhere. */
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
    fun the_bar_offers_four_destinations_and_alerts_opens_its_screen() {
        // Esta prueba afirmaba hasta la feature 012 que había exactamente tres destinos y que
        // «Avisos» no existía. La campana volvió con las notificaciones, como el documento de
        // diseño prometió en su enmienda del 29 de agosto de 2026.
        composeRule.onNodeWithTag(TAG_BOTTOM_BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_BOTTOM_HOME).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_BOTTOM_SEARCH).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_BOTTOM_SAVED).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_BOTTOM_ALERTS).assertIsDisplayed()

        composeRule.onNodeWithTag(TAG_BOTTOM_ALERTS).performClick()
        composeRule.onNodeWithTag(TAG_ALERTS_SCREEN).assertIsDisplayed()

        composeRule.onNodeWithTag(TAG_BOTTOM_HOME).performClick()
        composeRule.onNodeWithTag(TAG_HEADER).assertIsDisplayed()
    }

    @Test
    fun search_is_a_real_screen_and_no_longer_announces_itself_as_coming() {
        // Esta prueba afirmaba justo lo contrario hasta la feature 006, y ese cambio es el que
        // demuestra que el marcador de posición se ha retirado de verdad (FR-021).
        composeRule.onNodeWithTag(TAG_BOTTOM_SEARCH).performClick()

        // La aserción sobre el marcador de posición se retiró en la feature 011 porque el
        // componible que lo dibujaba **ya no existe en el proyecto**, y eso es una garantía más
        // fuerte que comprobarlo en ejecución. Lo que sigue afirmándose es lo que importa: que en
        // su lugar hay una pantalla de verdad.
        // Sin nada escrito, lo que se ve es el estado inicial: ni un vacío ni un error.
        composeRule.onNodeWithTag(TAG_SEARCH_INITIAL).assertIsDisplayed()

        composeRule.onNodeWithTag(TAG_BOTTOM_HOME).performClick()
        composeRule.onNodeWithTag(TAG_HEADER).assertIsDisplayed()
    }

    @Test
    fun saved_is_a_real_screen_and_no_longer_announces_itself_as_coming() {
        // Esta es la prueba que demuestra que el marcador de posición se ha retirado de verdad
        // (FR-010, SC-008). Antes de la feature 005 afirmaba justo lo contrario.
        composeRule.onNodeWithTag(TAG_BOTTOM_SAVED).performClick()

        // Ídem: el marcador de posición ya no existe como componible (feature 011).
        // Sin nada guardado, lo que se ve es el estado vacío, que explica qué falta.
        composeRule.onNodeWithTag(TAG_SAVED_EMPTY).assertIsDisplayed()

        composeRule.onNodeWithTag(TAG_BOTTOM_HOME).performClick()
        composeRule.onNodeWithTag(TAG_HEADER).assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
