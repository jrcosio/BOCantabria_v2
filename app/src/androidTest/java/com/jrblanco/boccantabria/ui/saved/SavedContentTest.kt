package com.jrblanco.boccantabria.ui.saved

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jrblanco.boccantabria.core.ui.component.TAG_PUBLICATION_CARD
import com.jrblanco.boccantabria.core.ui.component.TAG_PUBLICATION_SAVE
import com.jrblanco.boccantabria.core.ui.component.TAG_PUBLICATION_SHARE
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.fake.publication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * What Guardados draws, on the stateless composable.
 *
 * Mounted with `createComposeRule()` and not the real activity: every instrumented test that
 * launches it has to cross the cover's second and a bit, and this screen has nothing to do with the
 * cover.
 */
class SavedContentTest {

    private val sections = BocSectionRepositoryImpl().sections()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_header_names_the_screen() {
        setContent(SavedUiState())

        composeRule.onNodeWithText("Guardados").assertIsDisplayed()
    }

    @Test
    fun saved_publications_are_drawn_with_the_same_card_as_the_bulletin() {
        // Dos organismos distintos a propósito: con el mismo, el texto del emisor coincidiría dos
        // veces y la aserción diría más de lo que puede comprobar.
        setContent(
            SavedUiState(
                content = SavedContentState.Publications(
                    listOf(
                        publication(
                            "boc:1",
                            title = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva.",
                            issuer = "Ayuntamiento de Piélagos",
                        ),
                        publication(
                            "boc:2",
                            title = "AYUNTAMIENTO DE SANTANDER: Bases de la convocatoria.",
                            issuer = "Ayuntamiento de Santander",
                        ),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag(TAG_SAVED_LIST).assertIsDisplayed()
        assertEquals(2, composeRule.onAllNodesWithTag(TAG_PUBLICATION_CARD).fetchSemanticsNodes().size)
        composeRule.onNodeWithText("AYUNTAMIENTO DE PIÉLAGOS").assertIsDisplayed()
        composeRule.onNodeWithText("Aprobación definitiva.").assertIsDisplayed()
        composeRule.onNodeWithText("AYUNTAMIENTO DE SANTANDER").assertIsDisplayed()
        composeRule.onNodeWithText("Bases de la convocatoria.").assertIsDisplayed()
        // La fecha aparece en las dos tarjetas: se comprueba que está, no que esté una sola vez.
        assertEquals(
            2,
            composeRule.onAllNodesWithText("27 de agosto de 2026").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun every_item_here_is_saved_so_the_action_offers_taking_it_off() {
        // En esta pantalla no hay nada sin guardar, así que el marcador va relleno y lo que la
        // acción anuncia es lo contrario de lo que hace en el boletín.
        setContent(SavedUiState(content = SavedContentState.Publications(listOf(publication("boc:1")))))

        composeRule.onNodeWithContentDescription("Quitar de guardados").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Guardar").assertDoesNotExist()
    }

    @Test
    fun tapping_an_item_opens_that_publication() {
        var opened: Publication? = null
        setContent(
            SavedUiState(content = SavedContentState.Publications(listOf(publication("boc:7")))),
            onOpenPublication = { opened = it },
        )

        composeRule.onNodeWithTag(TAG_PUBLICATION_CARD).performClick()

        assertEquals("boc:7", opened?.externalKey)
    }

    @Test
    fun the_actions_of_an_item_emit_their_events() {
        var shared: Publication? = null
        var toggled: Publication? = null
        setContent(
            SavedUiState(content = SavedContentState.Publications(listOf(publication("boc:7")))),
            onShare = { shared = it },
            onToggleSaved = { toggled = it },
        )

        composeRule.onNodeWithTag(TAG_PUBLICATION_SHARE).performClick()
        composeRule.onNodeWithTag(TAG_PUBLICATION_SAVE).performClick()

        assertEquals("boc:7", shared?.externalKey)
        assertEquals("boc:7", toggled?.externalKey)
    }

    // ---------- Estado vacío (US4) ----------

    @Test
    fun with_nothing_saved_the_screen_explains_what_is_missing() {
        setContent(SavedUiState(content = SavedContentState.Empty))

        composeRule.onNodeWithTag(TAG_SAVED_EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("Aún no has guardado publicaciones").assertIsDisplayed()
        composeRule.onNodeWithText("Toca el marcador de una publicación y la encontrarás aquí.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SAVED_LIST).assertDoesNotExist()
    }

    @Test
    fun the_empty_state_offers_a_way_out_and_it_works() {
        var explored = 0
        setContent(SavedUiState(content = SavedContentState.Empty), onExplore = { explored++ })

        composeRule.onNodeWithText("Explorar el BOC").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SAVED_EMPTY_ACTION).performClick()

        assertEquals(1, explored)
    }

    @Test
    fun with_content_the_empty_state_is_gone() {
        setContent(
            SavedUiState(content = SavedContentState.Publications(listOf(publication("boc:1")))),
        )

        composeRule.onNodeWithTag(TAG_SAVED_EMPTY).assertDoesNotExist()
    }

    @Suppress("LongParameterList")
    private fun setContent(
        state: SavedUiState,
        onOpenPublication: (Publication) -> Unit = {},
        onShare: (Publication) -> Unit = {},
        onToggleSaved: (Publication) -> Unit = {},
        onExplore: () -> Unit = {},
    ) {
        composeRule.setContent {
            BOCantabriaTheme {
                SavedContent(
                    state = state,
                    sections = sections,
                    onOpenPublication = onOpenPublication,
                    onShare = onShare,
                    onToggleSaved = onToggleSaved,
                    onExplore = onExplore,
                )
            }
        }
    }
}
