package com.jrblanco.boccantabria.ui.sections

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
 */
class SectionsDrawerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val selected = mutableListOf<BocSection>()

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
    fun filtering_by_text_narrows_the_panel_and_opens_what_matched() {
        setContent()

        composeRule.onNodeWithTag(TAG_SECTIONS_QUERY).performTextInput("oposi")

        composeRule.onNodeWithTag(sectionRowTag("1")).assertDoesNotExist()
        composeRule.onNodeWithTag(sectionRowTag("2.2")).assertIsDisplayed()
    }

    @Test
    fun a_filter_that_matches_nothing_leaves_a_message_and_not_a_blank_panel() {
        setContent()

        composeRule.onNodeWithTag(TAG_SECTIONS_QUERY).performTextInput("zzz")

        composeRule.onNodeWithTag(TAG_SECTIONS_EMPTY).assertIsDisplayed()
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
                    onQueryChanged = viewModel::onQueryChanged,
                    onToggleExpanded = viewModel::onToggleExpanded,
                    onSelect = { selected += it },
                )
            }
        }
    }
}
