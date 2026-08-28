package com.jrblanco.boccantabria.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jrblanco.boccantabria.MainActivity
import com.jrblanco.boccantabria.core.ui.component.TAG_LOADING
import com.jrblanco.boccantabria.data.source.remote.ContentRemoteDataSource
import com.jrblanco.boccantabria.fake.FakeContentRemoteDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.testGraphOverrides
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Covers acceptance scenario 4 of user story 1 (FR-005, FR-023): rotating the device keeps the
 * content and does not trigger a second load.
 *
 * Asserting on the fake's call count is the part that matters: showing the content again after
 * a reload would look identical on screen but would be the bug this test exists to catch.
 */
class HomeStateRestorationTest {

    private val remote = FakeContentRemoteDataSource()

    @get:Rule(order = 0)
    val koinRule = KoinOverrideRule(testGraphOverrides(remote))

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun content_survives_a_configuration_change_without_reloading() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(ITEM_TITLE).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(ITEM_TITLE).assertIsDisplayed()
        val callsBeforeRecreate = remote.calls

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText(ITEM_TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_LOADING).assertDoesNotExist()
        assertEquals(callsBeforeRecreate, remote.calls)
    }

    private companion object {
        const val ITEM_TITLE = "Boletín de prueba"
    }
}
