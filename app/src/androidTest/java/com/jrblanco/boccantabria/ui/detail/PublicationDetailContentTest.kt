package com.jrblanco.boccantabria.ui.detail

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
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
import com.jrblanco.boccantabria.ui.detail.component.TAG_DETAIL_PREVIEW
import com.jrblanco.boccantabria.ui.detail.component.TAG_DETAIL_PREVIEW_ERROR
import com.jrblanco.boccantabria.ui.detail.component.TAG_DETAIL_PREVIEW_LOADING
import com.jrblanco.boccantabria.ui.detail.component.TAG_DETAIL_TABS
import com.jrblanco.boccantabria.ui.detail.component.TAG_DETAIL_TITLE
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
    fun the_two_tabs_and_the_action_bar_are_present() {
        setContent(state())

        composeRule.onNodeWithTag(TAG_DETAIL_TABS).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ACTION_OPEN).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ACTION_ASK).assertIsDisplayed()
        composeRule.scrollTo(TAG_DETAIL_METADATA)
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
    fun the_summary_tab_says_what_is_coming_instead_of_looking_broken() {
        setContent(state(tab = DetailTab.AI_SUMMARY))

        composeRule.onNodeWithTag(TAG_COMING_SOON).assertIsDisplayed()
        // Asking is no longer a tab, but it is still an action: the button stays.
        composeRule.onNodeWithTag(TAG_ACTION_ASK).assertIsDisplayed()
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

        // Scrolled to first: the metadata card legitimately fills the top of the tab, so on a
        // phone the preview starts below the fold. Asserting it is on screen without scrolling
        // would be asserting a layout the design never promised.
        composeRule.scrollTo(TAG_DETAIL_PREVIEW_LOADING)
        composeRule.onNodeWithTag(TAG_DETAIL_PREVIEW_LOADING).assertIsDisplayed()

        document.value = DocumentStatus.Failed(DomainError.Network)
        composeRule.scrollTo(TAG_DETAIL_PREVIEW_ERROR)
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

    @Test
    fun the_header_scrolls_away_and_the_tabs_stay_put() {
        // The reason this screen uses a lazy list at all: a BOC title runs to a hundred and thirty
        // characters, and a fixed header left the metadata card a narrow strip at the bottom.
        setContent(state())

        composeRule.onNodeWithTag(TAG_DETAIL_TITLE).assertIsDisplayed()

        composeRule.scrollTo(TAG_DETAIL_PREVIEW)

        composeRule.onNodeWithTag(TAG_DETAIL_TABS).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_DETAIL_TITLE).assertIsNotDisplayed()
    }

    /** Lazy lists compose what they show, so a node has to be scrolled to before it exists. */
    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.scrollTo(tag: String) {
        onNodeWithTag(TAG_DETAIL_LIST).performScrollToNode(hasTestTag(tag))
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

    // ---------- Lo guardado (feature 005) ----------

    @Test
    fun the_bar_offers_saving_when_the_publication_is_not_on_the_list() {
        setContent(PublicationDetailUiState(publication = publication("boc:1"), isSaved = false))

        composeRule.onNodeWithTag(TAG_DETAIL_SAVE).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Guardar").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Quitar de guardados").assertDoesNotExist()
    }

    @Test
    fun the_bar_offers_taking_it_off_when_it_is_on_the_list() {
        // Sobre la barra azul el icono ya es blanco, así que el estado tiene que viajar en el
        // trazado **y** en palabras: un cambio de tinte no distinguiría nada.
        setContent(PublicationDetailUiState(publication = publication("boc:1"), isSaved = true))

        composeRule.onNodeWithContentDescription("Quitar de guardados").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Guardar").assertDoesNotExist()
    }

    @Test
    fun saving_is_not_offered_for_a_publication_that_is_no_longer_stored() {
        // FR-008: una acción que no puede hacer nada es un callejón.
        setContent(PublicationDetailUiState(publication = null, isMissing = true))

        composeRule.onNodeWithTag(TAG_DETAIL_SAVE).assertDoesNotExist()
    }

    @Test
    fun the_save_action_emits_its_event() {
        var saves = 0
        setContent(
            PublicationDetailUiState(publication = publication("boc:1")),
            onSave = { saves++ },
        )

        composeRule.onNodeWithTag(TAG_DETAIL_SAVE).performClick()

        assertEquals(1, saves)
    }

    private fun setContent(
        state: PublicationDetailUiState,
        onTabSelected: (DetailTab) -> Unit = {},
        onRetry: () -> Unit = {},
        onSave: () -> Unit = {},
    ) {
        composeRule.setContent {
            BOCantabriaTheme {
                PublicationDetailContent(
                    state = state,
                    onBack = {},
                    onSave = onSave,
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
