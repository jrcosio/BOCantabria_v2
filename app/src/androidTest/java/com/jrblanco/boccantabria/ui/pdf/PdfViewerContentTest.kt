package com.jrblanco.boccantabria.ui.pdf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jrblanco.boccantabria.core.ui.component.TAG_ERROR
import com.jrblanco.boccantabria.core.ui.component.TAG_RETRY
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.domain.model.DomainError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The viewer's two states that do not need a document.
 *
 * The third —a rendered document— is covered by `PdfViewerSmokeTest`, which opens a real PDF: what
 * matters there is the library, and what matters here is everything around it.
 */
class PdfViewerContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun while_it_opens_it_says_so() {
        setContent(PdfViewerUiState.Loading)

        composeRule.onNodeWithTag(TAG_PDF_VIEWER_LOADING).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_PDF_VIEWER_BACK).assertIsDisplayed()
    }

    @Test
    fun a_failure_is_shown_with_a_retry_and_not_an_endless_wait() {
        var retries = 0
        setContent(PdfViewerUiState.Error(DomainError.Network), onRetry = { retries++ })

        composeRule.onNodeWithTag(TAG_PDF_VIEWER_ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_RETRY).performClick()

        assertEquals(1, retries)
    }

    @Test
    fun sharing_is_offered_from_the_bar_while_reading() {
        var shares = 0
        setContent(PdfViewerUiState.Loading, onShare = { shares++ })

        composeRule.onNodeWithTag(TAG_PDF_VIEWER_SHARE).performClick()

        assertEquals(1, shares)
    }

    private fun setContent(
        state: PdfViewerUiState,
        onShare: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            BOCantabriaTheme {
                PdfViewerContent(
                    state = state,
                    initialPage = 0,
                    onBack = {},
                    onShare = onShare,
                    onRetry = onRetry,
                )
            }
        }
    }
}
