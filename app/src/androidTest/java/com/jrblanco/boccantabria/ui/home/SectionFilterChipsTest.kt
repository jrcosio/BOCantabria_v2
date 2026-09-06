package com.jrblanco.boccantabria.ui.home

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.ui.home.component.SectionFilterChips
import com.jrblanco.boccantabria.ui.home.component.TAG_CHIPS
import com.jrblanco.boccantabria.ui.home.component.TAG_CHIP_ALL
import com.jrblanco.boccantabria.ui.home.component.TAG_CHIP_WHOLE_SECTION
import com.jrblanco.boccantabria.ui.home.component.TAG_SUBCHIPS
import com.jrblanco.boccantabria.ui.home.component.chipTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The quick filters, on the stateless composable.
 *
 * `createComposeRule` and not `createAndroidComposeRule<MainActivity>()`: what is being checked is
 * a row of chips, and launching the real activity would drag the cover's minimum second and a bit
 * into every one of these tests for nothing.
 */
class SectionFilterChipsTest {

    private val sections: List<BocSection> = BocSectionRepositoryImpl().sections()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_first_chip_names_the_day_s_bulletin() {
        setContent()

        composeRule.onNodeWithTag(TAG_CHIPS).assertIsDisplayed()
        composeRule.onNodeWithText("Boletín de hoy").assertIsDisplayed()
    }

    @Test
    fun the_first_chip_no_longer_promises_everything() {
        setContent()

        // «Todo» is the word that started feature 013, and it must not come back: the query behind
        // this chip shows one date, not the archive. Asserted on the chip's own label rather than
        // with a blanket `assertDoesNotExist`, because the word is legitimate elsewhere.
        composeRule.onNodeWithTag(TAG_CHIP_ALL).assert(hasText("Todo").not())
    }

    @Test
    fun tapping_the_first_chip_asks_for_the_day_s_bulletin() {
        var selected: String? = "something"
        setContent(onSelect = { selected = it })

        composeRule.onNodeWithTag(TAG_CHIP_ALL).performClick()

        assertEquals(null, selected)
    }

    @Test
    fun tapping_a_section_chip_emits_its_code() {
        var selected: String? = null
        setContent(onSelect = { selected = it })

        composeRule.onNodeWithTag(chipTag("2")).performClick()

        assertEquals("2", selected)
    }

    // ---------- The second row: subsections ----------

    @Test
    fun without_subsections_there_is_no_second_row() {
        setContent()

        composeRule.onNodeWithTag(TAG_SUBCHIPS).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_CHIP_WHOLE_SECTION).assertDoesNotExist()
    }

    @Test
    fun a_section_with_subsections_shows_them_under_the_sections() {
        setContent(sectionCode = "2")

        composeRule.onNodeWithTag(TAG_SUBCHIPS).assertIsDisplayed()
        composeRule.onNodeWithText("Toda la sección").assertIsDisplayed()
        composeRule.onNodeWithText("Nombramientos").assertIsDisplayed()
        composeRule.onNodeWithText("Oposiciones").assertIsDisplayed()
        composeRule.onNodeWithText("Otros de personal").assertIsDisplayed()
    }

    @Test
    fun the_sections_row_comes_before_the_subsections_row() {
        setContent(sectionCode = "2")

        // Order is the contract: the second row has to read as depending on the first, and reversed
        // it would read as two lists of equals. Compared by vertical position, which is what a
        // reader actually sees, rather than by declaration order.
        val sectionsTop = composeRule.onNodeWithTag(TAG_CHIPS).fetchSemanticsNode().positionInRoot.y
        val subsectionsTop = composeRule.onNodeWithTag(TAG_SUBCHIPS).fetchSemanticsNode().positionInRoot.y
        assertTrue("Subsections must sit below the sections", subsectionsTop > sectionsTop)
    }

    @Test
    fun the_whole_section_entry_emits_the_parent_code() {
        var selected: String? = null
        setContent(sectionCode = "2", onSelect = { selected = it })

        composeRule.onNodeWithTag(TAG_CHIP_WHOLE_SECTION).performClick()

        assertEquals("2", selected)
    }

    @Test
    fun a_subsection_chip_emits_its_own_code() {
        var selected: String? = null
        setContent(sectionCode = "2", onSelect = { selected = it })

        composeRule.onNodeWithTag(chipTag("2.2")).performClick()

        assertEquals("2.2", selected)
    }

    @Test
    fun a_null_section_code_suppresses_the_second_row_even_with_subsections() {
        // Defence of the contract: the whole-section entry has nothing to emit without a parent
        // code, and a chip that emits null would silently send the reader to the day's bulletin.
        setContent(sectionCode = null, subsections = subsectionsOf("2"))

        composeRule.onNodeWithTag(TAG_SUBCHIPS).assertDoesNotExist()
    }

    private fun subsectionsOf(parent: String): List<SectionChip> =
        sections.filter { it.parentCode == parent }.map { subsection ->
            SectionChip(
                code = subsection.code,
                label = subsection.shortName,
                colorGroup = subsection.colorGroup,
                isSelected = false,
            )
        }

    private fun setContent(
        isTodaySelected: Boolean = true,
        sectionCode: String? = null,
        subsections: List<SectionChip> = sectionCode?.let(::subsectionsOf).orEmpty(),
        onSelect: (String?) -> Unit = {},
    ) {
        composeRule.setContent {
            BOCantabriaTheme {
                SectionFilterChips(
                    chips = sections.filter { it.isTopLevel }.map { section ->
                        SectionChip(
                            code = section.code,
                            label = section.shortName,
                            colorGroup = section.colorGroup,
                            isSelected = section.code == sectionCode,
                        )
                    },
                    isTodaySelected = isTodaySelected,
                    onSelect = onSelect,
                    subsections = subsections,
                    sectionCode = sectionCode,
                    isWholeSectionSelected = sectionCode != null,
                )
            }
        }
    }
}
