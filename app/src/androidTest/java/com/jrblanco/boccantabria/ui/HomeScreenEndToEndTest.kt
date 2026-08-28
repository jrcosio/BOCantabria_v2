package com.jrblanco.boccantabria.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.jrblanco.boccantabria.MainActivity
import com.jrblanco.boccantabria.data.source.remote.ContentRemoteDataSource
import com.jrblanco.boccantabria.fake.FakeContentRemoteDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.testGraphOverrides
import org.junit.Rule
import org.junit.Test

/**
 * Runs the real activity against the real graph, replacing only the remote source. It is the
 * proof that the whole chain — screen, view model, use case, repository, sources — is wired
 * together and not merely correct piece by piece.
 */
class HomeScreenEndToEndTest {

    private val remote = FakeContentRemoteDataSource()

    @get:Rule(order = 0)
    val koinRule = KoinOverrideRule(testGraphOverrides(remote))

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun the_screen_shows_content_coming_from_the_remote_source() {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(ITEM_TITLE).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(ITEM_TITLE).assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val ITEM_TITLE = "Boletín de prueba"
    }
}
