package com.jrblanco.boccantabria.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jrblanco.boccantabria.MainActivity
import com.jrblanco.boccantabria.core.ui.component.TAG_ERROR
import com.jrblanco.boccantabria.core.ui.component.TAG_RETRY
import com.jrblanco.boccantabria.data.source.remote.ContentItemDto
import com.jrblanco.boccantabria.data.source.remote.ContentRemoteDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.testGraphOverrides
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Acceptance scenarios 2 and 3 of user story 1: a failing source shows the error, and retrying
 * once the source recovers shows the content.
 *
 * The source fails from launch rather than being switched mid-test, because the view model
 * survives a configuration change by design (see HomeStateRestorationTest) and therefore would
 * never reload just because the activity was recreated.
 */
class HomeScreenErrorRecoveryTest {

    private val remote = FailingThenRecoveringRemoteDataSource()

    @get:Rule(order = 0)
    val koinRule = KoinOverrideRule(testGraphOverrides(remote))

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun a_failing_source_shows_the_error_and_retrying_recovers() {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TAG_ERROR).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TAG_ERROR).assertIsDisplayed()

        remote.recover()
        composeRule.onNodeWithTag(TAG_RETRY).performClick()

        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(ITEM_TITLE).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(ITEM_TITLE).assertIsDisplayed()
    }

    private class FailingThenRecoveringRemoteDataSource : ContentRemoteDataSource {

        @Volatile
        private var failing: Boolean = true

        fun recover() {
            failing = false
        }

        override suspend fun fetchContentItems(): List<ContentItemDto> {
            if (failing) throw IOException("offline")
            return listOf(ContentItemDto(id = "1", label = ITEM_TITLE))
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val ITEM_TITLE = "Boletín de prueba"
    }
}
