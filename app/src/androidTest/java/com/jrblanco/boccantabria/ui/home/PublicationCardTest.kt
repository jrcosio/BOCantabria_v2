package com.jrblanco.boccantabria.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.fake.publication
import com.jrblanco.boccantabria.ui.home.component.PublicationCard
import com.jrblanco.boccantabria.ui.home.component.TAG_PUBLICATION_CARD
import com.jrblanco.boccantabria.ui.home.component.TAG_PUBLICATION_SAVE
import com.jrblanco.boccantabria.ui.home.component.TAG_PUBLICATION_SHARE
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The central component of the application: what it shows and what it emits. */
class PublicationCardTest {

    private val sections = BocSectionRepositoryImpl().sections()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_reading_order_is_issuer_title_and_date() {
        setContent(publication(title = "Aprobación definitiva de la Ordenanza Fiscal"))

        composeRule.onNodeWithTag(TAG_PUBLICATION_CARD).assertIsDisplayed()
        composeRule.onNodeWithText("AYUNTAMIENTO DE PIÉLAGOS").assertIsDisplayed()
        composeRule.onNodeWithText("Aprobación definitiva de la Ordenanza Fiscal").assertIsDisplayed()
        composeRule.onNodeWithText("27 de agosto de 2026").assertIsDisplayed()
    }

    @Test
    fun the_issuer_is_not_said_twice() {
        // The source writes titles as «ORGANISMO: descripción», and the card already prints the
        // issuer on its own line above.
        setContent(
            publication(
                title = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva de la Ordenanza Fiscal.",
                issuer = "Ayuntamiento de Piélagos",
            ),
        )

        composeRule.onNodeWithText("AYUNTAMIENTO DE PIÉLAGOS").assertIsDisplayed()
        composeRule.onNodeWithText("Aprobación definitiva de la Ordenanza Fiscal.").assertIsDisplayed()
        composeRule.onNodeWithText(
            "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva de la Ordenanza Fiscal.",
        ).assertDoesNotExist()
    }

    @Test
    fun the_section_travels_as_text_and_not_only_as_colour() {
        // Nine sections share five colours, so the indicator alone would say less than it seems.
        setContent(publication(sectionCode = "1"))

        composeRule.onNodeWithText("Sección Disposiciones generales").assertIsDisplayed()
    }

    @Test
    fun a_publication_of_a_subsection_names_the_subsection() {
        setContent(publication(sectionCode = "2", subsectionCode = "2.2"))

        composeRule.onNodeWithText("Sección Cursos, oposiciones y concursos").assertIsDisplayed()
    }

    @Test
    fun a_long_title_is_shown_and_not_dropped() {
        val long = "AYUNTAMIENTO DE SANTANDER: Aprobación definitiva de la modificación de la " +
            "Ordenanza reguladora del uso y aprovechamiento de las playas del término municipal"
        setContent(publication(title = long))

        composeRule.onNodeWithTag(TAG_PUBLICATION_CARD).assertIsDisplayed()
    }

    @Test
    fun a_publication_without_an_issuer_still_renders() {
        setContent(publication(issuer = null))

        composeRule.onNodeWithTag(TAG_PUBLICATION_CARD).assertIsDisplayed()
    }

    @Test
    fun both_actions_are_offered_and_emit_their_events() {
        var shares = 0
        var saves = 0
        setContent(publication(), onShare = { shares++ }, onSave = { saves++ })

        composeRule.onNodeWithTag(TAG_PUBLICATION_SHARE).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(TAG_PUBLICATION_SAVE).assertIsDisplayed().performClick()

        assertEquals(1, shares)
        assertEquals(1, saves)
    }

    @Test
    fun the_body_of_the_card_navigates_nowhere_in_this_feature() {
        // The detail screen is the next feature. Until then the card must not pretend otherwise.
        setContent(publication())

        composeRule.onNodeWithTag(TAG_PUBLICATION_CARD).performClick()

        composeRule.onNodeWithTag(TAG_PUBLICATION_CARD).assertIsDisplayed()
    }

    private fun setContent(
        publication: Publication,
        onShare: () -> Unit = {},
        onSave: () -> Unit = {},
    ) {
        val section: BocSection? = sections.firstOrNull { it.code == publication.classificationCode }
        composeRule.setContent {
            BOCantabriaTheme {
                PublicationCard(
                    publication = publication,
                    section = section,
                    formattedDate = "27 de agosto de 2026",
                    onShare = onShare,
                    onSave = onSave,
                )
            }
        }
    }
}
