package com.jrblanco.boccantabria.ui.detail

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jrblanco.boccantabria.ui.detail.component.TAG_COMING_SOON
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.DetailTab
import com.jrblanco.boccantabria.fake.publication
import org.junit.Rule
import org.junit.Test

/**
 * The deferred summary tab, section 20.1 of the design document.
 *
 * What is checked is that it keeps its identity —the icon and the AI label— and not merely its
 * colour: a violet panel saying «Próximamente» and nothing else would not tell anyone what is
 * coming. Asking has its own screen now, covered by `AskScreenTest`.
 */
class ComingSoonTabTest {

    private val sections = BocSectionRepositoryImpl().sections()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_summary_tab_keeps_the_ai_label_alongside_the_notice() {
        setContent(DetailTab.AI_SUMMARY)

        composeRule.onNodeWithTag(TAG_COMING_SOON).assertIsDisplayed()
        composeRule.onNodeWithText("Resumen generado por IA").assertIsDisplayed()
        composeRule.onNodeWithText("Próximamente").assertIsDisplayed()
        composeRule.onNodeWithText("Aquí verás un resumen del anuncio, con sus fuentes.")
            .assertIsDisplayed()
    }

    private fun setContent(initialTab: DetailTab) {
        val publication = publication(sectionCode = "1")
        composeRule.setContent {
            val tab = remember { mutableStateOf(initialTab) }
            BOCantabriaTheme {
                PublicationDetailContent(
                    state = PublicationDetailUiState(
                        publication = publication,
                        section = sections.firstOrNull { it.code == publication.classificationCode },
                        selectedTab = tab.value,
                    ),
                    onBack = {},
                    onSave = {},
                    onShare = {},
                    onTabSelected = { tab.value = it },
                    onOpenDocument = {},
                    onAsk = {},
                    onRetry = {},
                )
            }
        }
    }
}
