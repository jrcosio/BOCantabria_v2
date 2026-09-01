package com.jrblanco.boccantabria.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jrblanco.boccantabria.core.ui.component.TAG_PUBLICATION_CARD
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.model.SearchQuery
import com.jrblanco.boccantabria.fake.publication
import com.jrblanco.boccantabria.ui.search.component.TAG_SEARCH_CLEAR
import com.jrblanco.boccantabria.ui.search.component.TAG_SEARCH_CHIPS
import com.jrblanco.boccantabria.ui.search.component.TAG_SEARCH_FIELD
import com.jrblanco.boccantabria.ui.search.component.searchChipTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * What Buscar draws, on the stateless composable.
 *
 * Mounted with `createComposeRule()` and not the real activity: every instrumented test that
 * launches it has to cross the cover's second and a bit, and this screen has nothing to do with it.
 */
class SearchContentTest {

    private val sections = BocSectionRepositoryImpl().sections()

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Por la etiqueta y no por el texto. Nació de una necesidad —barra y campo decían lo mismo y la
     * búsqueda por texto encontraba dos nodos—, y se queda por costumbre buena: nombrar el nodo
     * aguanta el siguiente cambio de redacción, y buscarlo por sus palabras no.
     *
     * La segunda aserción es la que fija que la barra dice `Buscar` y **no** repite lo que el campo
     * de debajo ya dice.
     */
    @Test
    fun the_bar_names_the_screen_and_offers_no_way_back() {
        setContent(SearchUiState())

        composeRule.onNodeWithTag(TAG_SEARCH_TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SEARCH_TITLE).assertTextEquals("Buscar")
        // Es un destino de la barra inferior, no una pantalla apilada: una flecha atrás aquí no
        // tendría a dónde ir. Desviación consciente respecto a la imagen de referencia.
        composeRule.onNodeWithContentDescription("Volver").assertDoesNotExist()
    }

    /** Neither an empty result nor a failure: nothing has been asked yet. */
    @Test
    fun before_there_is_anything_to_search_for_the_screen_says_so_without_saying_empty() {
        setContent(SearchUiState())

        composeRule.onNodeWithTag(TAG_SEARCH_INITIAL).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SEARCH_EMPTY).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_SEARCH_RESULTS).assertDoesNotExist()
    }

    @Test
    fun results_carry_the_issuer_the_title_the_section_and_the_date() {
        setContent(
            SearchUiState(
                query = SearchQuery(text = "pielagos"),
                content = SearchContentState.Results(
                    listOf(
                        publication(
                            "boc:1",
                            title = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva.",
                            issuer = "Ayuntamiento de Piélagos",
                        ),
                    ),
                    isTruncated = false,
                ),
            ),
        )

        composeRule.onNodeWithText("AYUNTAMIENTO DE PIÉLAGOS", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Aprobación definitiva.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Disposiciones generales", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("27 de agosto de 2026", substring = true).assertIsDisplayed()
    }

    @Test
    fun the_number_of_results_is_shown() {
        setContent(
            SearchUiState(
                query = SearchQuery(text = "pielagos"),
                content = SearchContentState.Results(
                    listOf(publication("boc:1"), publication("boc:2")),
                    isTruncated = false,
                ),
            ),
        )

        composeRule.onNodeWithTag(TAG_SEARCH_COUNT).assertIsDisplayed()
        composeRule.onNodeWithText("2 resultados").assertIsDisplayed()
    }

    /** A list quietly cut short reads as a complete list. */
    @Test
    fun a_list_that_had_to_be_cut_short_says_so() {
        setContent(
            SearchUiState(
                query = SearchQuery(text = "de"),
                content = SearchContentState.Results(listOf(publication("boc:1")), isTruncated = true),
            ),
        )

        composeRule.onNodeWithTag(TAG_SEARCH_TRUNCATED).assertIsDisplayed()
    }

    @Test
    fun a_complete_list_does_not_say_it_was_cut_short() {
        setContent(
            SearchUiState(
                query = SearchQuery(text = "pielagos"),
                content = SearchContentState.Results(listOf(publication("boc:1")), isTruncated = false),
            ),
        )

        composeRule.onNodeWithTag(TAG_SEARCH_TRUNCATED).assertDoesNotExist()
    }

    @Test
    fun nothing_found_suggests_dropping_a_filter_rather_than_just_saying_nothing() {
        setContent(
            SearchUiState(query = SearchQuery(text = "expropiacion"), content = SearchContentState.Empty),
        )

        composeRule.onNodeWithTag(TAG_SEARCH_EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("quita alguno de los filtros", substring = true).assertIsDisplayed()
    }

    @Test
    fun tapping_a_result_opens_it() {
        var opened: Publication? = null
        setContent(
            SearchUiState(
                query = SearchQuery(text = "pielagos"),
                content = SearchContentState.Results(listOf(publication("boc:1")), isTruncated = false),
            ),
            onOpenPublication = { opened = it },
        )

        composeRule.onNodeWithTag(TAG_PUBLICATION_CARD).performClick()

        assertEquals("boc:1", opened?.externalKey)
    }

    /** The state is told apart by the outline of the icon and by what it announces, never by colour. */
    @Test
    fun a_result_whose_key_is_saved_is_drawn_marked() {
        setContent(
            SearchUiState(
                query = SearchQuery(text = "pielagos"),
                content = SearchContentState.Results(listOf(publication("boc:1")), isTruncated = false),
                savedKeys = setOf("boc:1"),
            ),
        )

        composeRule.onNodeWithContentDescription("Quitar de guardados").assertIsDisplayed()
    }

    @Test
    fun the_clear_action_shows_up_only_when_there_is_something_to_clear() {
        setContent(SearchUiState())

        composeRule.onNodeWithTag(TAG_SEARCH_CLEAR).assertDoesNotExist()

        composeRule.onNodeWithTag(TAG_SEARCH_FIELD).performTextInput("pielagos")

        composeRule.onNodeWithTag(TAG_SEARCH_CLEAR).assertIsDisplayed()
    }

    // ---------- Los chips de filtros activos ----------

    @Test
    fun with_no_filters_there_are_no_chips() {
        setContent(SearchUiState(query = SearchQuery(text = "pielagos")))

        composeRule.onNodeWithTag(TAG_SEARCH_CHIPS).assertDoesNotExist()
    }

    @Test
    fun each_applied_filter_shows_up_as_a_chip_that_names_it() {
        setContent(
            SearchUiState(
                query = SearchQuery(text = "subvenciones", sectionCode = "6", issuer = "Gobierno de Cantabria"),
                content = SearchContentState.Empty,
            ),
        )

        composeRule.onNodeWithTag(TAG_SEARCH_CHIPS).assertIsDisplayed()
        composeRule.onNodeWithTag(searchChipTag("section")).assertIsDisplayed()
        composeRule.onNodeWithTag(searchChipTag("issuer")).assertIsDisplayed()
        composeRule.onNodeWithText("Sección: Subvenciones y ayudas", substring = true).assertIsDisplayed()
    }

    /** The requirement that breaks most easily: taking a filter off must not take the text with it. */
    @Test
    fun removing_a_chip_reports_it_without_touching_the_text() {
        var removedIssuer = false
        setContent(
            SearchUiState(
                query = SearchQuery(text = "subvenciones", issuer = "Gobierno de Cantabria"),
                content = SearchContentState.Empty,
            ),
            onRemoveIssuer = { removedIssuer = true },
        )

        composeRule.onNodeWithTag(searchChipTag("issuer")).performClick()

        assertEquals(true, removedIssuer)
        composeRule.onNodeWithText("subvenciones").assertIsDisplayed()
    }

    /**
     * Mounts the stateless screen with something holding its query, which is what the view model
     * does in the application. Without it, typing would report the change and the drawing would
     * never move. `setContent` can only be called once per test, so the state lives inside it.
     */
    @Suppress("LongParameterList")
    private fun setContent(
        state: SearchUiState,
        onOpenPublication: (Publication) -> Unit = {},
        onRemoveIssuer: () -> Unit = {},
    ) {
        composeRule.setContent {
            var current by remember { mutableStateOf(state) }
            BOCantabriaTheme {
                SearchContent(
                    state = current,
                    sections = sections,
                    onQueryChanged = { typed -> current = current.copy(query = current.query.copy(text = typed)) },
                    onClearQuery = { current = current.copy(query = current.query.copy(text = "")) },
                    onOpenPublication = onOpenPublication,
                    onRemoveIssuer = onRemoveIssuer,
                )
            }
        }
    }
}
