package com.jrblanco.boccantabria.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.ui.navigation.BocBottomBar
import com.jrblanco.boccantabria.ui.navigation.BottomDestination
import com.jrblanco.boccantabria.ui.navigation.TAG_BOTTOM_ALERTS_BADGE
import org.junit.Rule
import org.junit.Test

/** The number on the bell: hidden at zero, the number up to nine, «9+» beyond (FR-002). */
class MainShellBadgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun at_zero_there_is_no_badge() {
        setContent(0)

        composeRule.onNodeWithTag(TAG_BOTTOM_ALERTS_BADGE).assertDoesNotExist()
    }

    @Test
    fun a_small_number_is_shown_as_is() {
        setContent(3)

        composeRule.onNodeWithTag(TAG_BOTTOM_ALERTS_BADGE, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("3", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun past_nine_it_says_nine_plus() {
        setContent(12)

        composeRule.onNodeWithText("9+", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun setContent(badge: Int) {
        composeRule.setContent {
            BOCantabriaTheme {
                BocBottomBar(current = BottomDestination.HOME, onSelect = {}, alertBadge = badge)
            }
        }
    }
}
