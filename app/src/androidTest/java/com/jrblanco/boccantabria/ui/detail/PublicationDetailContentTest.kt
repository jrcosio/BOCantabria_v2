package com.jrblanco.boccantabria.ui.detail

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jrblanco.boccantabria.core.ui.component.TAG_COMING_SOON
import com.jrblanco.boccantabria.core.ui.component.TAG_ERROR
import com.jrblanco.boccantabria.core.ui.component.TAG_RETRY
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.DetailTab
import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.fake.publication
import com.jrblanco.boccantabria.ui.detail.component.TAG_ACTION_ASK
import com.jrblanco.boccantabria.ui.detail.component.TAG_ACTION_OPEN
import com.jrblanco.boccantabria.ui.detail.component.TAG_DETAIL_METADATA
import com.jrblanco.boccantabria.ui.detail.component.TAG_DETAIL_PREVIEW_ERROR
import com.jrblanco.boccantabria.ui.detail.component.TAG_DETAIL_PREVIEW_LOADING
import com.jrblanco.boccantabria.ui.detail.component.TAG_DETAIL_TABS
import com.jrblanco.boccantabria.ui.detail.component.TAG_TAB_ASK
import com.jrblanco.boccantabria.ui.detail.component.TAG_TAB_SUMMARY
import com.jrblanco.boccantabria.ui.share.ShareState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The composition of the detail screen, mounted on its own.
 *
 * Every state arrives as a parameter, so none of this needs the graph, the network or the splash
 * screen every other instrumented test has to cross.
 */
class PublicationDetailContentTest {

    private val sections = BocSectionRepositoryImpl().sections()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_three_tabs_and_the_action_bar_are_present() {
        setContent(state())

        composeRule.onNodeWithTag(TAG_DETAIL_TABS).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ACTION_OPEN).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ACTION_ASK).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_DETAIL_METADATA).assertIsDisplayed()
    }

    @Test
    fun choosing_a_tab_reports_it_rather_than_deciding_by_itself() {
        var chosen: DetailTab? = null
        setContent(state(), onTabSelected = { chosen = it })

        composeRule.onNodeWithTag(TAG_TAB_SUMMARY).performClick()

        assertEquals(DetailTab.AI_SUMMARY, chosen)
    }

    @Test
    fun the_two_ai_tabs_say_what_is_coming_instead_of_looking_broken() {
        setContent(state(tab = DetailTab.AI_SUMMARY))
        composeRule.onNodeWithTag(TAG_COMING_SOON).assertIsDisplayed()

        composeRule.onNodeWithTag(TAG_TAB_ASK).performClick()
    }

    @Test
    fun a_publication_that_is_no_longer_stored_is_explained() {
        setContent(PublicationDetailUiState(isMissing = true))

        composeRule.onNodeWithTag(TAG_DETAIL_MISSING).assertIsDisplayed()
        // With nothing to open, the action bar has nothing to offer either.
        composeRule.onNodeWithTag(TAG_ACTION_OPEN).assertDoesNotExist()
    }

    @Test
    fun a_download_in_progress_is_visible_and_a_failure_offers_a_retry() {
        // Both scenarios inside a single `setContent`: calling it twice in one test throws, and
        // the state is driven from outside instead.
        var retries = 0
        val document = mutableStateOf<DocumentStatus>(
            DocumentStatus.Downloading(bytesRead = 10, totalBytes = 100),
        )
        composeRule.setContent {
            BOCantabriaTheme {
                PublicationDetailContent(
                    state = state(document = document.value),
                    onBack = {},
                    onSave = {},
                    onShare = {},
                    onTabSelected = {},
                    onOpenDocument = {},
                    onAsk = {},
                    onRetry = { retries++ },
                )
            }
        }

        composeRule.onNodeWithTag(TAG_DETAIL_PREVIEW_LOADING).assertIsDisplayed()

        document.value = DocumentStatus.Failed(DomainError.Network)
        composeRule.onNodeWithTag(TAG_DETAIL_PREVIEW_ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_RETRY).performClick()

        assertEquals(1, retries)
    }

    @Test
    fun preparing_a_share_is_said_out_loud() {
        // FR-032: fetching a document takes seconds, and a share sheet that simply does not appear
        // reads as the application ignoring the tap.
        setContent(state(share = ShareState.Preparing))

        composeRule.onNodeWithTag(TAG_DETAIL_SHARE_PREPARING).assertIsDisplayed()
    }

    private fun state(
        tab: DetailTab = DetailTab.DOCUMENT,
        document: DocumentStatus = DocumentStatus.Absent,
        share: ShareState =
            ShareState.Idle,
    ): PublicationDetailUiState {
        val publication = publication(sectionCode = "1")
        return PublicationDetailUiState(
            publication = publication,
            section = sections.firstOrNull { it.code == publication.classificationCode },
            selectedTab = tab,
            document = document,
            share = share,
        )
    }

    private fun setContent(
        state: PublicationDetailUiState,
        onTabSelected: (DetailTab) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            BOCantabriaTheme {
                PublicationDetailContent(
                    state = state,
                    onBack = {},
                    onSave = {},
                    onShare = {},
                    onTabSelected = onTabSelected,
                    onOpenDocument = {},
                    onAsk = {},
                    onRetry = onRetry,
                )
            }
        }
    }
}
