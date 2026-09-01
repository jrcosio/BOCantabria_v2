package com.jrblanco.boccantabria.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.fake.publication
import com.jrblanco.boccantabria.ui.home.component.TAG_HOME_SEARCH_CLEAR
import com.jrblanco.boccantabria.ui.home.component.TAG_HOME_SEARCH_CLOSE
import com.jrblanco.boccantabria.ui.home.component.TAG_HOME_SEARCH_FIELD
import com.jrblanco.boccantabria.ui.home.component.TAG_MENU
import com.jrblanco.boccantabria.ui.home.component.TAG_SEARCH
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The magnifier, on the stateless composable.
 *
 * The state itself is exercised in `HomeViewModelTest`; what is checked here is that the bar really
 * changes shape, that the field is there to type in, and that closing puts the header back.
 */
class HomeSearchTest {

    private val sections = BocSectionRepositoryImpl().sections()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_magnifier_turns_the_header_into_a_field() {
        setContent(HomeUiState(search = HomeSearchState(isOpen = true)))

        composeRule.onNodeWithTag(TAG_HOME_SEARCH_FIELD).assertIsDisplayed()
        composeRule.onNodeWithText("Buscar en esta edición…").assertIsDisplayed()
        // El escudo y el menú dejan sitio: es la misma barra cambiando de forma.
        composeRule.onNodeWithTag(TAG_MENU).assertDoesNotExist()
    }

    @Test
    fun the_closed_bar_is_the_ordinary_one() {
        setContent(HomeUiState())

        composeRule.onNodeWithTag(TAG_MENU).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SEARCH).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_HOME_SEARCH_FIELD).assertDoesNotExist()
    }

    @Test
    fun tapping_the_magnifier_reports_it() {
        var opened = false
        setContent(HomeUiState(), onSearchOpened = { opened = true })

        composeRule.onNodeWithTag(TAG_SEARCH).performClick()

        assertEquals(true, opened)
    }

    @Test
    fun what_is_typed_is_reported_as_it_is_typed() {
        val typed = mutableListOf<String>()
        setContent(
            HomeUiState(search = HomeSearchState(isOpen = true)),
            onSearchQueryChanged = { typed += it },
        )

        composeRule.onNodeWithTag(TAG_HOME_SEARCH_FIELD).performTextInput("pie")

        assertEquals("pie", typed.lastOrNull())
    }

    @Test
    fun the_clear_action_shows_up_only_when_there_is_something_to_clear() {
        setContent(HomeUiState(search = HomeSearchState(isOpen = true)))

        composeRule.onNodeWithTag(TAG_HOME_SEARCH_CLEAR).assertDoesNotExist()

        composeRule.onNodeWithTag(TAG_HOME_SEARCH_FIELD).performTextReplacement("pielagos")

        composeRule.onNodeWithTag(TAG_HOME_SEARCH_CLEAR).assertIsDisplayed()
    }

    @Test
    fun closing_the_search_reports_it() {
        var closed = false
        setContent(
            HomeUiState(search = HomeSearchState(isOpen = true, query = "pielagos")),
            onSearchClosed = { closed = true },
        )

        composeRule.onNodeWithTag(TAG_HOME_SEARCH_CLOSE).performClick()

        assertEquals(true, closed)
    }

    @Test
    fun while_a_search_is_on_the_number_of_matches_is_shown() {
        setContent(
            HomeUiState(
                search = HomeSearchState(isOpen = true, query = "pielagos"),
                content = HomeContentState.Publications(listOf(publication("boc:1"))),
            ),
        )

        composeRule.onNodeWithTag(TAG_HOME_SEARCH_COUNT).assertIsDisplayed()
        composeRule.onNodeWithText("1 coincidencia").assertIsDisplayed()
    }

    @Test
    fun with_no_search_on_there_is_no_match_count() {
        setContent(HomeUiState(content = HomeContentState.Publications(listOf(publication("boc:1")))))

        composeRule.onNodeWithTag(TAG_HOME_SEARCH_COUNT).assertDoesNotExist()
    }

    /**
     * "Nothing here matches" is not "nothing has been published here", and only the first of the two
     * has somewhere to send you.
     */
    @Test
    fun nothing_matching_says_it_is_about_this_edition_and_offers_the_way_out() {
        setContent(
            HomeUiState(
                search = HomeSearchState(isOpen = true, query = "expropiacion"),
                content = HomeContentState.NoSearchResults("expropiacion"),
            ),
        )

        composeRule.onNodeWithTag(TAG_HOME_NO_RESULTS).assertIsDisplayed()
        composeRule.onNodeWithText("Nada en esta edición").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_HOME_SEARCH_GLOBALLY).assertIsDisplayed()
    }

    @Test
    fun the_way_out_hands_over_what_was_typed() {
        var handedOver: String? = null
        setContent(
            HomeUiState(
                search = HomeSearchState(isOpen = true, query = "expropiacion"),
                content = HomeContentState.NoSearchResults("expropiacion"),
            ),
            onSearchGlobally = { handedOver = it },
        )

        composeRule.onNodeWithTag(TAG_HOME_SEARCH_GLOBALLY).performClick()

        assertEquals("expropiacion", handedOver)
    }

    /**
     * Mounts the stateless screen with something holding its search text, which is what the view
     * model does in the application.
     *
     * Without it the field would be a controlled component nobody controls: typing would report the
     * change and the drawing would never move, and any assertion about what typing produces would
     * be checking the test's own inertia. `setContent` can only be called once per test, so the
     * state has to live inside it.
     */
    @Suppress("LongParameterList")
    private fun setContent(
        state: HomeUiState,
        onSearchOpened: () -> Unit = {},
        onSearchQueryChanged: (String) -> Unit = {},
        onSearchClosed: () -> Unit = {},
        onSearchGlobally: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            var current by remember { mutableStateOf(state) }
            BOCantabriaTheme {
                HomeContent(
                    state = current,
                    sections = sections,
                    onRefresh = {},
                    onRetry = {},
                    onSearchOpened = onSearchOpened,
                    onSearchQueryChanged = { typed ->
                        current = current.copy(search = current.search.copy(query = typed))
                        onSearchQueryChanged(typed)
                    },
                    onSearchClosed = onSearchClosed,
                    onSearchGlobally = onSearchGlobally,
                )
            }
        }
    }
}
