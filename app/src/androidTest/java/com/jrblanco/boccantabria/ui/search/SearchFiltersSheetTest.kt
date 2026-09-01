package com.jrblanco.boccantabria.ui.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.SearchQuery
import com.jrblanco.boccantabria.ui.search.component.SearchFiltersSheet
import com.jrblanco.boccantabria.ui.search.component.TAG_SEARCH_FILTERS_APPLY
import com.jrblanco.boccantabria.ui.search.component.TAG_SEARCH_FILTERS_CLEAR
import com.jrblanco.boccantabria.ui.search.component.TAG_SEARCH_FILTERS_SHEET
import com.jrblanco.boccantabria.ui.search.component.TAG_SEARCH_FILTER_ISSUER
import com.jrblanco.boccantabria.ui.search.component.TAG_SEARCH_FILTER_SECTION
import com.jrblanco.boccantabria.ui.search.component.TAG_SEARCH_FILTER_SUBSECTION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * `Filtrar resultados`, on its own.
 *
 * Mounted directly rather than through the screen: what needs checking is what the sheet offers and
 * what it refuses, and going through Buscar would drag a view model and a store into a test about
 * six form fields.
 */
class SearchFiltersSheetTest {

    private val sections = BocSectionRepositoryImpl().sections()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_sheet_offers_dates_section_and_issuer() {
        setContent(SearchQuery())

        composeRule.onNodeWithTag(TAG_SEARCH_FILTERS_SHEET).assertIsDisplayed()
        composeRule.onNodeWithText("Filtrar resultados").assertIsDisplayed()
        composeRule.onNodeWithText("Fecha desde").assertIsDisplayed()
        composeRule.onNodeWithText("Fecha hasta").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SEARCH_FILTER_SECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SEARCH_FILTER_ISSUER).assertIsDisplayed()
    }

    /**
     * The bulletin does not publish a municipality and it would only be guessable from the issuer,
     * and only for town halls. Deferred by decision, so its absence is asserted rather than assumed.
     */
    @Test
    fun there_is_no_municipality_filter() {
        setContent(SearchQuery())

        composeRule.onNodeWithText("Municipio").assertDoesNotExist()
    }

    /**
     * A subsection alone belongs to nothing, so it only shows up once a section is chosen.
     *
     * Two tests and not one: `setContent` can only be called once per test, and calling it twice
     * throws rather than recomposing.
     */
    @Test
    fun with_no_section_chosen_no_subsection_is_offered() {
        setContent(SearchQuery())

        composeRule.onNodeWithTag(TAG_SEARCH_FILTER_SUBSECTION).assertDoesNotExist()
    }

    @Test
    fun choosing_a_section_brings_out_its_subsections() {
        setContent(SearchQuery(sectionCode = "2"))

        composeRule.onNodeWithTag(TAG_SEARCH_FILTER_SUBSECTION).assertIsDisplayed()
    }

    @Test
    fun a_section_with_no_subsections_offers_none() {
        // La 1, «Disposiciones generales», no tiene subsecciones en el catálogo oficial.
        setContent(SearchQuery(sectionCode = "1"))

        composeRule.onNodeWithTag(TAG_SEARCH_FILTER_SUBSECTION).assertDoesNotExist()
    }

    @Test
    fun a_sound_range_can_be_applied() {
        setContent(
            SearchQuery(from = LocalDate.of(2026, 1, 1), to = LocalDate.of(2026, 8, 27)),
        )

        composeRule.onNodeWithTag(TAG_SEARCH_FILTERS_APPLY).assertIsEnabled()
    }

    /**
     * The impossible combination is refused in the interface. The model reports it rather than
     * throwing, so that a handling mistake never closes the application.
     */
    @Test
    fun an_inverted_range_cannot_be_applied() {
        setContent(
            SearchQuery(from = LocalDate.of(2026, 8, 27), to = LocalDate.of(2026, 1, 1)),
        )

        composeRule.onNodeWithTag(TAG_SEARCH_FILTERS_APPLY).assertIsNotEnabled()
    }

    @Test
    fun applying_hands_over_what_was_chosen() {
        var applied: SearchQuery? = null
        setContent(SearchQuery(text = "subvenciones", sectionCode = "6"), onApply = { applied = it })

        composeRule.onNodeWithTag(TAG_SEARCH_FILTERS_APPLY).performClick()

        assertEquals("6", applied?.sectionCode)
    }

    /** Clearing inside the sheet clears filters. The text is not the sheet's to touch. */
    @Test
    fun clearing_inside_the_sheet_drops_the_filters_and_not_the_text() {
        var applied: SearchQuery? = null
        setContent(
            SearchQuery(text = "subvenciones", sectionCode = "6", issuer = "Gobierno de Cantabria"),
            onApply = { applied = it },
        )

        composeRule.onNodeWithTag(TAG_SEARCH_FILTERS_CLEAR).performClick()
        composeRule.onNodeWithTag(TAG_SEARCH_FILTERS_APPLY).performClick()

        assertNull(applied?.sectionCode)
        assertNull(applied?.issuer)
        assertEquals("subvenciones", applied?.text)
    }

    private fun setContent(query: SearchQuery, onApply: (SearchQuery) -> Unit = {}) {
        composeRule.setContent {
            BOCantabriaTheme {
                SearchFiltersSheet(
                    query = query,
                    sections = sections,
                    issuers = listOf("Ayuntamiento de Piélagos", "Gobierno de Cantabria"),
                    onApply = onApply,
                    onDismiss = {},
                )
            }
        }
    }
}
