package com.jrblanco.boccantabria.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.fake.publication
import com.jrblanco.boccantabria.ui.detail.component.DocumentHeader
import com.jrblanco.boccantabria.ui.detail.component.TAG_DETAIL_DATE
import com.jrblanco.boccantabria.ui.detail.component.TAG_DETAIL_HEADER
import com.jrblanco.boccantabria.ui.detail.component.TAG_DETAIL_ISSUER
import com.jrblanco.boccantabria.ui.detail.component.TAG_DETAIL_OFFICIAL_BADGE
import com.jrblanco.boccantabria.ui.detail.component.TAG_DETAIL_SECTION
import com.jrblanco.boccantabria.ui.detail.component.TAG_DETAIL_TITLE
import org.junit.Rule
import org.junit.Test

/** The head of the detail screen: sections 18.2 and 18.3 of the design document. */
class DocumentHeaderTest {

    private val sections = BocSectionRepositoryImpl().sections()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_five_elements_are_there_in_the_order_the_design_fixes() {
        setContent(publication(sectionCode = "1"))

        composeRule.onNodeWithTag(TAG_DETAIL_HEADER).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_DETAIL_SECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_DETAIL_TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_DETAIL_ISSUER).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_DETAIL_DATE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_DETAIL_OFFICIAL_BADGE).assertIsDisplayed()

        val top = { tag: String -> composeRule.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y }
        val order = listOf(
            TAG_DETAIL_SECTION,
            TAG_DETAIL_TITLE,
            TAG_DETAIL_ISSUER,
            TAG_DETAIL_DATE,
            TAG_DETAIL_OFFICIAL_BADGE,
        ).map(top)

        assert(order == order.sorted()) { "the header is out of order: $order" }
    }

    @Test
    fun a_long_title_is_shown_whole() {
        // The card truncates at four lines because it competes with a hundred others. Here the
        // title is the reason the screen exists: cutting it would force opening the PDF to learn
        // what the announcement is about.
        val title = "AYUNTAMIENTO DE SANTANDER: Aprobación definitiva del expediente de " +
            "modificación de créditos número tres del presupuesto general para el ejercicio " +
            "corriente, con detalle de las partidas afectadas y su financiación."

        setContent(publication(title = title, issuer = "Ayuntamiento de Santander"))

        composeRule.onNodeWithText(title.removePrefix("AYUNTAMIENTO DE SANTANDER: "))
            .assertIsDisplayed()
    }

    @Test
    fun without_an_issuer_there_is_no_empty_line_where_it_would_be() {
        setContent(publication(issuer = null))

        composeRule.onNodeWithTag(TAG_DETAIL_ISSUER).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_DETAIL_DATE).assertIsDisplayed()
    }

    private fun setContent(publication: Publication) {
        val section = sections.firstOrNull { it.code == publication.classificationCode }
        composeRule.setContent {
            BOCantabriaTheme {
                DocumentHeader(
                    publication = publication,
                    section = section,
                    formattedDate = "27 de agosto de 2026",
                )
            }
        }
    }
}
