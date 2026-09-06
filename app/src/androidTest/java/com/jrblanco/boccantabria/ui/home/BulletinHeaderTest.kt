package com.jrblanco.boccantabria.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.domain.model.BulletinHeaderData
import com.jrblanco.boccantabria.ui.home.component.BulletinHeader
import com.jrblanco.boccantabria.ui.home.component.TAG_HEADER
import com.jrblanco.boccantabria.ui.home.component.TAG_HEADER_COUNT
import com.jrblanco.boccantabria.ui.home.component.TAG_HEADER_DATE
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * The editorial header, and above all the label its date carries.
 *
 * The two labels are the whole point of this class: the same date means the published edition on the
 * day's bulletin and the section's most recent announcement inside a section, and a header that says
 * neither is what made somebody ask what the date was for.
 */
class BulletinHeaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_day_s_bulletin_labels_its_date_as_the_edition() {
        setContent(BulletinHeaderData(date = DATE, publicationCount = 39))

        composeRule.onNodeWithTag(TAG_HEADER).assertIsDisplayed()
        composeRule.onNodeWithText("Edición del 4 de septiembre de 2026").assertIsDisplayed()
    }

    @Test
    fun a_section_labels_its_date_as_the_most_recent_publication() {
        setContent(
            BulletinHeaderData(
                date = DATE,
                publicationCount = 336,
                sectionName = "Autoridades y personal",
            ),
        )

        composeRule.onNodeWithText("Última publicación: 4 de septiembre de 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Autoridades y personal").assertIsDisplayed()
    }

    @Test
    fun the_two_labels_are_not_the_same_text() {
        // Belt and braces on the branch itself: if somebody points both cases at one resource, the
        // header goes back to saying nothing useful and nothing else would notice.
        setContent(BulletinHeaderData(date = DATE, publicationCount = 1))

        composeRule.onNodeWithText("Última publicación: 4 de septiembre de 2026").assertDoesNotExist()
    }

    @Test
    fun with_no_date_there_is_no_orphan_label() {
        // The first run of an installation, before anything has been stored. FR-006.
        setContent(BulletinHeaderData.EMPTY)

        composeRule.onNodeWithTag(TAG_HEADER).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_HEADER_DATE).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_HEADER_COUNT).assertDoesNotExist()
    }

    private fun setContent(header: BulletinHeaderData) {
        composeRule.setContent {
            BOCantabriaTheme { BulletinHeader(header = header) }
        }
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 9, 4)
    }
}
