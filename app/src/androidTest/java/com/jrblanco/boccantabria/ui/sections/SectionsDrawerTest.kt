package com.jrblanco.boccantabria.ui.sections

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The sections panel, driven by its real view model but drawn without the graph: what the panel
 * does is presentation, and presentation is what this checks.
 *
 * The two filtering tests and the empty-panel one are gone since feature 013, **with the field they
 * exercised**. Their place is taken by the header: shield, name and a way to put the panel away.
 */
class SectionsDrawerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val selected = mutableListOf<BocSection>()

    private var closed = 0

    @Test
    fun the_nine_sections_are_listed_with_their_number_and_name() {
        setContent()

        composeRule.onNodeWithText("1 · Disposiciones generales").assertIsDisplayed()
        composeRule.onNodeWithText("2 · Autoridades y personal").assertIsDisplayed()
        composeRule.onNodeWithTag(sectionRowTag("9")).assertIsDisplayed()
    }

    @Test
    fun there_are_no_bells_and_no_alerts_card() {
        setContent()

        composeRule.onNodeWithText("Alertas personalizadas").assertDoesNotExist()
    }

    @Test
    fun a_section_expands_and_collapses() {
        setContent()

        composeRule.onNodeWithTag(sectionRowTag("2.2")).assertDoesNotExist()
        composeRule.onNodeWithTag(sectionToggleTag("2")).performClick()
        composeRule.onNodeWithTag(sectionRowTag("2.2")).assertIsDisplayed()
        composeRule.onNodeWithTag(sectionToggleTag("2")).performClick()
        composeRule.onNodeWithTag(sectionRowTag("2.2")).assertDoesNotExist()
    }

    @Test
    fun the_panel_says_whose_panel_it_is() {
        setContent()

        composeRule.onNodeWithTag(TAG_SECTIONS_HEADER).assertIsDisplayed()
        composeRule.onNodeWithText("BOC Cantabria").assertIsDisplayed()
    }

    @Test
    fun the_arrow_puts_the_panel_away() {
        setContent()

        composeRule.onNodeWithTag(TAG_SECTIONS_CLOSE).performClick()

        assertEquals(1, closed)
    }

    @Test
    fun the_arrow_is_reachable_by_its_description() {
        // FR-023: whoever cannot see the arrow has to be able to find it.
        setContent()

        composeRule.onNodeWithContentDescription("Recoger el panel").assertIsDisplayed()
    }

    @Test
    fun there_is_no_text_field_in_the_panel_any_more() {
        setContent()

        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun choosing_a_section_emits_it() {
        setContent()

        composeRule.onNodeWithTag(sectionRowTag("3")).performClick()

        assertEquals(listOf("3"), selected.map { it.code })
    }

    @Test
    fun choosing_a_subsection_emits_the_subsection_and_not_its_parent() {
        setContent()

        composeRule.onNodeWithTag(sectionToggleTag("7")).performClick()
        composeRule.onNodeWithTag(sectionRowTag("7.1")).performClick()

        assertEquals(listOf("7.1"), selected.map { it.code })
    }

    private fun setContent() {
        composeRule.setContent {
            val viewModel = remember {
                SectionsViewModel(GetBocSectionsUseCase(BocSectionRepositoryImpl()))
            }
            val state by viewModel.uiState.collectAsState()

            BOCantabriaTheme {
                SectionsDrawerContent(
                    state = state,
                    onToggleExpanded = viewModel::onToggleExpanded,
                    onSelect = { selected += it },
                    onClose = { closed++ },
                )
            }
        }
    }
}
