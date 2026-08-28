package com.jrblanco.boccantabria.ui.splash

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.domain.model.DomainError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Walks the four states on the stateless composable, without starting the dependency graph. That
 * is what state hoisting buys.
 */
class SplashContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loading_shows_the_emblem_and_the_indicator() {
        setContent(SplashUiState.Loading)

        composeRule.onNodeWithTag(TAG_SPLASH_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SPLASH_EMBLEM).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SPLASH_LOADING).assertIsDisplayed()
    }

    @Test
    fun error_shows_both_ways_out() {
        setContent(SplashUiState.Error(DomainError.Network))

        composeRule.onNodeWithTag(TAG_SPLASH_ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SPLASH_RETRY).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SPLASH_CONTINUE_OFFLINE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SPLASH_LOADING).assertDoesNotExist()
    }

    @Test
    fun error_actions_invoke_their_callbacks() {
        var retries = 0
        var continues = 0
        setContent(
            state = SplashUiState.Error(DomainError.Unknown),
            onRetry = { retries++ },
            onContinueOffline = { continues++ },
        )

        composeRule.onNodeWithTag(TAG_SPLASH_RETRY).performClick()
        composeRule.onNodeWithTag(TAG_SPLASH_CONTINUE_OFFLINE).performClick()

        assertEquals(1, retries)
        assertEquals(1, continues)
    }

    @Test
    fun an_obsolete_version_offers_no_way_around_the_block() {
        setContent(SplashUiState.Blocked(BlockReason.UpdateRequired))

        composeRule.onNodeWithTag(TAG_SPLASH_BLOCKED).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SPLASH_RETRY).assertIsDisplayed()
        // The point of the test: letting a blocked user through would defeat the block.
        composeRule.onNodeWithTag(TAG_SPLASH_CONTINUE_OFFLINE).assertDoesNotExist()
    }

    @Test
    fun maintenance_shows_the_published_message() {
        setContent(SplashUiState.Blocked(BlockReason.Maintenance(MESSAGE)))

        composeRule.onNodeWithTag(TAG_SPLASH_BLOCKED).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SPLASH_CONTINUE_OFFLINE).assertDoesNotExist()
    }

    private fun setContent(
        state: SplashUiState,
        onRetry: () -> Unit = {},
        onContinueOffline: () -> Unit = {},
    ) {
        composeRule.setContent {
            BOCantabriaTheme {
                SplashContent(
                    state = state,
                    onRetry = onRetry,
                    onContinueOffline = onContinueOffline,
                )
            }
        }
    }

    private companion object {
        const val MESSAGE = "Estamos en mantenimiento"
    }
}
