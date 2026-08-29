package com.jrblanco.boccantabria.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jrblanco.boccantabria.core.ui.component.TAG_EMPTY
import com.jrblanco.boccantabria.core.ui.component.TAG_ERROR
import com.jrblanco.boccantabria.core.ui.component.TAG_OFFLINE_BANNER
import com.jrblanco.boccantabria.core.ui.component.TAG_RETRY
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.BulletinHeaderData
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.HomeSelection
import com.jrblanco.boccantabria.fake.publication
import com.jrblanco.boccantabria.ui.home.component.TAG_HEADER
import com.jrblanco.boccantabria.ui.home.component.TAG_HEADER_COUNT
import com.jrblanco.boccantabria.ui.home.component.TAG_PUBLICATION_SKELETON
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * The five states of the list, on the stateless composable.
 *
 * No graph, no network and no splash screen: every instrumented test that launches the real
 * activity has to cross the cover's minimum second and a bit, and paying that toll to photograph
 * an empty state would be a waste.
 */
class HomeContentTest {

    private val sections = BocSectionRepositoryImpl().sections()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun skeletons_are_shown_while_the_first_synchronisation_runs() {
        // The placeholders pulse forever by design, and an assertion that waits for the
        // composition to go idle would wait for ever with it. Driving the clock by hand is the
        // supported way out; without this the test hangs rather than fails, which is worse.
        composeRule.mainClock.autoAdvance = false
        setContent(HomeUiState(content = HomeContentState.Skeleton))
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onAllNodesWithTag(TAG_PUBLICATION_SKELETON)[0].assertIsDisplayed()
    }

    @Test
    fun publications_are_shown_with_their_issuer_and_title() {
        setContent(
            HomeUiState(
                content = HomeContentState.Publications(
                    listOf(publication("boc:1", title = "Aprobación definitiva de la ordenanza")),
                ),
            ),
        )

        composeRule.onNodeWithTag(TAG_PUBLICATIONS).assertIsDisplayed()
        composeRule.onNodeWithText("Aprobación definitiva de la ordenanza").assertIsDisplayed()
        composeRule.onNodeWithText("AYUNTAMIENTO DE PIÉLAGOS").assertIsDisplayed()
    }

    @Test
    fun an_empty_selection_shows_its_own_message_and_not_an_error() {
        setContent(HomeUiState(content = HomeContentState.Empty))

        composeRule.onNodeWithTag(TAG_EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ERROR).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_RETRY).assertDoesNotExist()
    }

    @Test
    fun an_empty_section_and_an_empty_day_do_not_say_the_same_thing() {
        setContent(
            HomeUiState(
                selection = HomeSelection.Section("8", "8.1"),
                content = HomeContentState.Empty,
            ),
        )

        composeRule.onNodeWithText("Esta sección no tiene publicaciones guardadas.").assertIsDisplayed()
    }

    @Test
    fun the_error_state_shows_the_message_and_the_retry_action() {
        setContent(HomeUiState(content = HomeContentState.Error(DomainError.Network)))

        composeRule.onNodeWithTag(TAG_ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_RETRY).assertIsDisplayed()
    }

    @Test
    fun clicking_retry_invokes_the_callback() {
        var clicks = 0
        setContent(
            state = HomeUiState(content = HomeContentState.Error(DomainError.Unknown)),
            onRetry = { clicks++ },
        )

        composeRule.onNodeWithTag(TAG_RETRY).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun being_offline_shows_the_notice_without_hiding_the_content() {
        setContent(
            HomeUiState(
                content = HomeContentState.Publications(listOf(publication("boc:1"))),
                isOffline = true,
            ),
        )

        composeRule.onNodeWithTag(TAG_OFFLINE_BANNER).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_PUBLICATIONS).assertIsDisplayed()
    }

    @Test
    fun refreshing_keeps_the_content_on_screen() {
        setContent(
            HomeUiState(
                content = HomeContentState.Publications(listOf(publication("boc:1"))),
                isRefreshing = true,
            ),
        )

        composeRule.onNodeWithTag(TAG_PUBLICATIONS).assertIsDisplayed()
    }

    @Test
    fun the_header_shows_the_date_and_the_count_and_no_bulletin_number() {
        setContent(
            HomeUiState(
                header = BulletinHeaderData(
                    date = LocalDate.of(2026, 8, 27),
                    publicationCount = 48,
                ),
                content = HomeContentState.Publications(listOf(publication("boc:1"))),
            ),
        )

        composeRule.onNodeWithTag(TAG_HEADER).assertIsDisplayed()
        composeRule.onNodeWithText("Boletín de hoy").assertIsDisplayed()
        composeRule.onNodeWithText("27 de agosto de 2026").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_HEADER_COUNT).assertIsDisplayed()
        composeRule.onNodeWithText("48 anuncios").assertIsDisplayed()
    }

    @Test
    fun the_header_of_a_section_names_it() {
        setContent(
            HomeUiState(
                selection = HomeSelection.Section("2", "2.2"),
                header = BulletinHeaderData(
                    date = LocalDate.of(2026, 8, 20),
                    publicationCount = 1,
                    sectionName = "Cursos, oposiciones y concursos",
                ),
                content = HomeContentState.Publications(listOf(publication("boc:1"))),
            ),
        )

        composeRule.onNodeWithText("Cursos, oposiciones y concursos").assertIsDisplayed()
        composeRule.onNodeWithText("1 anuncio").assertIsDisplayed()
    }

    private fun setContent(
        state: HomeUiState,
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            BOCantabriaTheme {
                HomeContent(
                    state = state,
                    sections = sections,
                    onRefresh = {},
                    onRetry = onRetry,
                )
            }
        }
    }
}
