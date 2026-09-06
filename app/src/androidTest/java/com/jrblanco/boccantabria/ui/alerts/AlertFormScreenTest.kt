package com.jrblanco.boccantabria.ui.alerts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.AlertRuleDraft
import com.jrblanco.boccantabria.domain.model.KeywordMatchMode
import com.jrblanco.boccantabria.domain.model.SectionSelection
import com.jrblanco.boccantabria.fake.publication
import com.jrblanco.boccantabria.ui.alerts.form.AlertFormContent
import com.jrblanco.boccantabria.ui.alerts.form.AlertFormUiState
import com.jrblanco.boccantabria.ui.alerts.form.SectionPickerRow
import com.jrblanco.boccantabria.ui.alerts.form.TAG_ALERT_FORM_PREVIEW
import com.jrblanco.boccantabria.ui.alerts.form.TAG_ALERT_FORM_PREVIEW_OPEN
import com.jrblanco.boccantabria.ui.alerts.form.TAG_ALERT_FORM_SAVE
import com.jrblanco.boccantabria.ui.alerts.form.TAG_ALERT_FORM_SCREEN
import com.jrblanco.boccantabria.ui.alerts.form.TAG_ALERT_FORM_SECTIONS_OPEN
import com.jrblanco.boccantabria.ui.alerts.form.TAG_ALERT_FORM_SECTIONS_SUMMARY
import com.jrblanco.boccantabria.ui.alerts.form.component.TAG_ALERT_FORM_KEYWORD_ADD
import com.jrblanco.boccantabria.ui.alerts.form.component.TAG_ALERT_FORM_KEYWORD_INPUT
import com.jrblanco.boccantabria.ui.alerts.form.component.TAG_ALERT_FORM_MODE_ALL
import com.jrblanco.boccantabria.ui.alerts.form.component.TAG_ALERT_FORM_PREVIEW_SHEET
import com.jrblanco.boccantabria.ui.alerts.form.component.TAG_ALERT_FORM_SECTIONS_COUNT
import com.jrblanco.boccantabria.ui.alerts.form.component.TAG_ALERT_FORM_SECTIONS_SHEET
import com.jrblanco.boccantabria.ui.alerts.form.component.TAG_ALERT_FORM_SUMMARY
import com.jrblanco.boccantabria.ui.alerts.form.component.alertKeywordChipTag
import com.jrblanco.boccantabria.ui.alerts.form.component.alertSectionTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * What the form draws, on the stateless composable. The state is handed in ready-made, so what is
 * checked is the drawing and the events, not the model.
 */
class AlertFormScreenTest {

    private val sections = BocSectionRepositoryImpl().sections()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun without_a_criterion_the_save_button_is_disabled() {
        setContent(ready(AlertRuleDraft()))

        composeRule.onNodeWithTag(TAG_ALERT_FORM_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ALERT_FORM_SAVE).assertIsNotEnabled()
        composeRule.onNodeWithText("Guardar aviso").assertIsDisplayed()
    }

    @Test
    fun with_a_name_and_a_keyword_it_is_enabled_and_the_chip_shows() {
        setContent(ready(AlertRuleDraft(name = "Ganadería", keywords = listOf("ganadería"))))

        composeRule.onNodeWithTag(TAG_ALERT_FORM_SAVE).assertIsEnabled()
        composeRule.onNodeWithTag(alertKeywordChipTag("ganadería")).assertIsDisplayed()
    }

    @Test
    fun typing_a_word_and_tapping_plus_emits_it() {
        var added: String? = null
        setContent(ready(AlertRuleDraft()), onKeywordAdded = { added = it })

        // Afirmar la pantalla antes de teclear: la carrera de la feature 006.
        composeRule.onNodeWithTag(TAG_ALERT_FORM_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ALERT_FORM_KEYWORD_INPUT).performTextInput("medio rural")
        composeRule.onNodeWithTag(TAG_ALERT_FORM_KEYWORD_ADD).performClick()

        assertEquals("medio rural", added)
    }

    @Test
    fun the_cross_removes_a_keyword() {
        var removed: String? = null
        setContent(ready(AlertRuleDraft(keywords = listOf("ganadería"))), onKeywordRemoved = { removed = it })

        composeRule.onNodeWithTag(alertKeywordChipTag("ganadería")).performClick()

        assertEquals("ganadería", removed)
    }

    @Test
    fun choosing_all_words_emits_the_mode() {
        var mode: KeywordMatchMode? = null
        setContent(ready(AlertRuleDraft()), onMatchModeChanged = { mode = it })

        composeRule.onNodeWithTag(TAG_ALERT_FORM_MODE_ALL).performClick()

        assertEquals(KeywordMatchMode.ALL, mode)
    }

    @Test
    fun the_sections_picker_shows_the_hierarchy_and_emits_toggles() {
        var toggled: String? = null
        setContent(ready(AlertRuleDraft(sectionCodes = setOf("2.1", "2.2", "2.3")), sectionsOpen = true), onSectionToggled = { toggled = it })

        composeRule.onNodeWithTag(TAG_ALERT_FORM_SECTIONS_SHEET).assertIsDisplayed()
        composeRule.onNodeWithText("3 seleccionadas").assertIsDisplayed()
        composeRule.onNodeWithTag(alertSectionTag("2.3")).performClick()

        assertEquals("2.3", toggled)
    }

    @Test
    fun the_sections_summary_says_all_when_every_child_is_in() {
        setContent(ready(AlertRuleDraft(name = "Personal", sectionCodes = setOf("2.1", "2.2", "2.3"))))

        // Below the fold and inside a button, whose semantics merge the text: scroll, unmerged tree.
        composeRule.onNodeWithTag(TAG_ALERT_FORM_SECTIONS_OPEN).performScrollTo()
        composeRule.onNodeWithTag(TAG_ALERT_FORM_SECTIONS_SUMMARY, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Autoridades y personal (todas)", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun the_summary_reads_in_plain_spanish() {
        setContent(
            ready(AlertRuleDraft(name = "Ayudas", keywords = listOf("ganadería", "subvención"), sectionCodes = setOf("6"))),
        )

        composeRule.onNodeWithTag(TAG_ALERT_FORM_SUMMARY)
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("Te avisaremos cuando una publicación nueva de Subvenciones y ayudas incluya «ganadería» o «subvención».")
    }

    @Test
    fun editing_says_save_changes() {
        setContent(ready(AlertRuleDraft(name = "Ganadería", keywords = listOf("ganadería")), isEdit = true))

        composeRule.onNodeWithText("Guardar cambios").assertIsDisplayed()
        composeRule.onNodeWithText("Editar aviso").assertIsDisplayed()
    }

    @Test
    fun the_preview_counts_and_offers_the_results() {
        setContent(ready(AlertRuleDraft(name = "Cosío", keywords = listOf("Cosío")), previewCount = 3))

        composeRule.onNodeWithTag(TAG_ALERT_FORM_PREVIEW).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("3 publicaciones actuales coinciden con esta configuración").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ALERT_FORM_PREVIEW_OPEN).assertIsDisplayed()
    }

    @Test
    fun the_preview_sheet_lists_the_results() {
        setContent(
            ready(
                AlertRuleDraft(name = "Cosío", keywords = listOf("Cosío")),
                previewCount = 3,
                preview = listOf(publication("boc:1"), publication("boc:2"), publication("boc:3")),
                previewOpen = true,
            ),
        )

        composeRule.onNodeWithTag(TAG_ALERT_FORM_PREVIEW_SHEET).assertIsDisplayed()
    }

    @Test
    fun the_picker_opens_on_request() {
        var opened = 0
        setContent(ready(AlertRuleDraft()), onSectionsOpened = { opened++ })

        composeRule.onNodeWithTag(TAG_ALERT_FORM_SECTIONS_OPEN).performClick()

        assertEquals(1, opened)
        composeRule.onNodeWithTag(TAG_ALERT_FORM_SECTIONS_COUNT).assertDoesNotExist()
    }

    private fun ready(
        draft: AlertRuleDraft,
        isEdit: Boolean = false,
        sectionsOpen: Boolean = false,
        previewCount: Int? = null,
        preview: List<com.jrblanco.boccantabria.domain.model.Publication> = emptyList(),
        previewOpen: Boolean = false,
    ): AlertFormUiState.Ready {
        val childrenOf = sections.filter { !it.isTopLevel }.groupBy { it.parentCode!! }
        return AlertFormUiState.Ready(
            draft = draft,
            errors = draft.validate(),
            keywordRejection = null,
            sectionRows = sections.filter { it.isTopLevel }.map {
                SectionPickerRow(it, childrenOf[it.code].orEmpty(), SectionSelection.stateOf(it, sections, draft.sectionCodes))
            },
            sectionParts = SectionSelection.summaryParts(draft.sectionCodes, sections),
            selectedLeafCount = SectionSelection.leafCount(draft.sectionCodes, sections),
            organizationSuggestions = emptyList(),
            isEdit = isEdit,
            isSaving = false,
            sectionsOpen = sectionsOpen,
            previewCount = previewCount,
            previewOpen = previewOpen,
            preview = preview,
            saveFailed = false,
        )
    }

    @Suppress("LongParameterList")
    private fun setContent(
        state: AlertFormUiState,
        onKeywordAdded: (String) -> Unit = {},
        onKeywordRemoved: (String) -> Unit = {},
        onMatchModeChanged: (KeywordMatchMode) -> Unit = {},
        onSectionsOpened: () -> Unit = {},
        onSectionToggled: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            BOCantabriaTheme {
                AlertFormContent(
                    state = state,
                    sections = sections,
                    onKeywordAdded = onKeywordAdded,
                    onKeywordRemoved = onKeywordRemoved,
                    onMatchModeChanged = onMatchModeChanged,
                    onSectionsOpened = onSectionsOpened,
                    onSectionToggled = onSectionToggled,
                )
            }
        }
    }
}
