package com.jrblanco.boccantabria.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jrblanco.boccantabria.MainActivity
import com.jrblanco.boccantabria.fake.FakeBocRemoteDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.testGraphOverrides
import com.jrblanco.boccantabria.ui.home.component.TAG_MENU
import com.jrblanco.boccantabria.ui.sections.TAG_SECTIONS_DRAWER
import com.jrblanco.boccantabria.ui.sections.sectionRowTag
import com.jrblanco.boccantabria.ui.sections.sectionToggleTag
import org.junit.Rule
import org.junit.Test

/**
 * The whole chain, with the real activity and the real graph, replacing only the source: screen,
 * view model, use cases, repository, normaliser and database wired together and not merely
 * correct one piece at a time.
 */
class HomeNavigationTest {

    private val remote = FakeBocRemoteDataSource()

    @get:Rule(order = 0)
    val koinRule = KoinOverrideRule(testGraphOverrides(remote))

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun the_bulletin_of_the_day_reaches_the_screen() {
        awaitText(FakeBocRemoteDataSource.DISPOSICIONES_TITLE)

        composeRule.onNodeWithText(FakeBocRemoteDataSource.DISPOSICIONES_TITLE).assertIsDisplayed()
        // The 2.2 announcement is from a week earlier, so it is not part of today's bulletin.
        composeRule.onNodeWithText(FakeBocRemoteDataSource.OPOSICIONES_TITLE).assertDoesNotExist()
    }

    @Test
    fun choosing_a_subsection_in_the_panel_changes_the_list_and_the_header() {
        awaitText(FakeBocRemoteDataSource.DISPOSICIONES_TITLE)

        composeRule.onNodeWithTag(TAG_MENU).performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("2 · Autoridades y personal").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TAG_SECTIONS_DRAWER).assertIsDisplayed()

        composeRule.onNodeWithTag(sectionToggleTag("2")).performClick()
        composeRule.onNodeWithTag(sectionRowTag("2.2")).performClick()

        // A section is not limited to the latest date, so the older announcement shows up.
        awaitText(FakeBocRemoteDataSource.OPOSICIONES_TITLE)
        composeRule.onNodeWithText("Cursos, oposiciones y concursos").assertIsDisplayed()
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
