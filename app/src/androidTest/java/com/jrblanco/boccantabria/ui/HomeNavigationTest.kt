package com.jrblanco.boccantabria.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.fake.FakeBocRemoteDataSource
import com.jrblanco.boccantabria.fake.KoinOverrideRule
import com.jrblanco.boccantabria.fake.testGraphOverrides
import com.jrblanco.boccantabria.ui.home.TAG_PUBLICATIONS
import com.jrblanco.boccantabria.ui.home.component.TAG_HEADER
import com.jrblanco.boccantabria.ui.home.component.TAG_MENU
import com.jrblanco.boccantabria.ui.main.MainShell
import com.jrblanco.boccantabria.ui.sections.TAG_SECTIONS_DRAWER
import com.jrblanco.boccantabria.ui.sections.sectionRowTag
import com.jrblanco.boccantabria.ui.sections.sectionToggleTag
import org.junit.Rule
import org.junit.Test

/**
 * The whole chain from the frame down: panel, screen, view model, use cases, repository,
 * normaliser and database, with only the source replaced.
 *
 * Mounts [MainShell] directly instead of launching the real activity. Two reasons, both
 * practical: the cover's minimum second and a bit would be paid on every test for nothing, and
 * crossing it here proved racy — the cover navigates from a `LaunchedEffect`, and while the test
 * pumps frames from its own thread that effect can resume off the main thread, where touching a
 * `Lifecycle` throws. The transition from the cover is covered where it belongs, in
 * `SplashNavigationTest` and `SplashBackStackTest`.
 */
class HomeNavigationTest {

    private val remote = FakeBocRemoteDataSource()

    @get:Rule(order = 0)
    val koinRule = KoinOverrideRule(testGraphOverrides(remote))

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun the_bulletin_of_the_day_reaches_the_screen() {
        setContent()

        awaitText(FakeBocRemoteDataSource.DISPOSICIONES_DISPLAYED)

        composeRule.onNodeWithTag(TAG_PUBLICATIONS).assertIsDisplayed()
        composeRule.onNodeWithText(FakeBocRemoteDataSource.DISPOSICIONES_DISPLAYED).assertIsDisplayed()
        // The 2.2 announcement is a week older, so it is not part of today's bulletin.
        composeRule.onNodeWithText(FakeBocRemoteDataSource.OPOSICIONES_DISPLAYED).assertDoesNotExist()
    }

    @Test
    fun choosing_a_subsection_in_the_panel_changes_the_list_and_the_header() {
        setContent()
        awaitText(FakeBocRemoteDataSource.DISPOSICIONES_DISPLAYED)

        composeRule.onNodeWithTag(TAG_MENU).performClick()
        composeRule.onNodeWithTag(TAG_SECTIONS_DRAWER).assertIsDisplayed()

        composeRule.onNodeWithTag(sectionToggleTag("2")).performClick()
        composeRule.onNodeWithTag(sectionRowTag("2.2")).performClick()

        // A section is not limited to the latest date, so the older announcement shows up.
        awaitText(FakeBocRemoteDataSource.OPOSICIONES_DISPLAYED)
        // Anchored to the header: the subsection name also appears on every card, so plain text
        // matching would find two nodes and say nothing about which one changed.
        composeRule.onNode(
            hasText("Cursos, oposiciones y concursos") and hasAnyAncestor(hasTestTag(TAG_HEADER)),
        ).assertIsDisplayed()
    }

    @Test
    fun the_filter_chip_returns_to_the_bulletin_of_the_day() {
        setContent()
        awaitText(FakeBocRemoteDataSource.DISPOSICIONES_DISPLAYED)

        composeRule.onNodeWithTag(TAG_MENU).performClick()
        composeRule.onNodeWithTag(sectionToggleTag("2")).performClick()
        composeRule.onNodeWithTag(sectionRowTag("2.2")).performClick()
        awaitText(FakeBocRemoteDataSource.OPOSICIONES_DISPLAYED)

        composeRule.onNodeWithTag(com.jrblanco.boccantabria.ui.home.component.TAG_CHIP_ALL).performClick()

        awaitText(FakeBocRemoteDataSource.DISPOSICIONES_DISPLAYED)
        composeRule.onNodeWithText("Boletín de hoy").assertIsDisplayed()
    }

    private fun setContent() {
        composeRule.setContent {
            BOCantabriaTheme {
                MainShell(navController = rememberNavController())
            }
        }
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
