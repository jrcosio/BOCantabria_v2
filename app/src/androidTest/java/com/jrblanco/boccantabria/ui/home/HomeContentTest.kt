package com.jrblanco.boccantabria.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jrblanco.boccantabria.core.ui.component.TAG_EMPTY
import com.jrblanco.boccantabria.core.ui.component.TAG_ERROR
import com.jrblanco.boccantabria.core.ui.component.TAG_LOADING
import com.jrblanco.boccantabria.core.ui.component.TAG_RETRY
import com.jrblanco.boccantabria.domain.model.ContentItem
import com.jrblanco.boccantabria.domain.model.DomainError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Exercises the stateless composable directly, so the four states can be checked without
 * starting the dependency graph. That is what state hoisting buys.
 */
class HomeContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loading_state_shows_the_indicator() {
        composeRule.setContent { HomeContent(state = HomeUiState.Loading, onRetry = {}) }

        composeRule.onNodeWithTag(TAG_LOADING).assertIsDisplayed()
    }

    @Test
    fun content_state_shows_the_items() {
        composeRule.setContent {
            HomeContent(state = HomeUiState.Content(ITEMS), onRetry = {})
        }

        composeRule.onNodeWithTag(TAG_CONTENT).assertIsDisplayed()
        composeRule.onNodeWithText("Boletín del lunes").assertIsDisplayed()
        composeRule.onNodeWithText("Boletín del martes").assertIsDisplayed()
    }

    @Test
    fun empty_state_shows_its_own_message_not_an_error() {
        composeRule.setContent { HomeContent(state = HomeUiState.Empty, onRetry = {}) }

        composeRule.onNodeWithTag(TAG_EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ERROR).assertDoesNotExist()
    }

    @Test
    fun error_state_shows_the_message_and_the_retry_action() {
        composeRule.setContent {
            HomeContent(state = HomeUiState.Error(DomainError.Network), onRetry = {})
        }

        composeRule.onNodeWithTag(TAG_ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_RETRY).assertIsDisplayed()
    }

    @Test
    fun clicking_retry_invokes_the_callback() {
        var clicks = 0
        composeRule.setContent {
            HomeContent(state = HomeUiState.Error(DomainError.Unknown), onRetry = { clicks++ })
        }

        composeRule.onNodeWithTag(TAG_RETRY).performClick()

        assertEquals(1, clicks)
    }

    private companion object {
        val ITEMS = listOf(
            ContentItem(id = "1", title = "Boletín del lunes"),
            ContentItem(id = "2", title = "Boletín del martes"),
        )
    }
}
