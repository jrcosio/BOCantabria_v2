package com.jrblanco.boccantabria.ui.ask

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jrblanco.boccantabria.core.ui.component.TAG_COMING_SOON
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The screen that replaced the third tab.
 *
 * It must keep the AI identity rather than fall back to the generic grey notice: the point is to
 * say what is coming, and «Próximamente» on its own says only that something is missing.
 */
class AskScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun it_says_what_is_coming_and_keeps_the_ai_identity() {
        setContent()

        composeRule.onNodeWithTag(TAG_COMING_SOON).assertIsDisplayed()
        composeRule.onNodeWithText("Preguntar al BOC").assertIsDisplayed()
        composeRule.onNodeWithText("Pregunta al BOC").assertIsDisplayed()
        composeRule.onNodeWithText("Próximamente").assertIsDisplayed()
        composeRule.onNodeWithText("Aquí podrás preguntar sobre este documento.").assertIsDisplayed()
    }

    @Test
    fun going_back_is_offered_and_reported() {
        var backs = 0
        setContent(onBack = { backs++ })

        composeRule.onNodeWithTag(TAG_ASK_BACK).performClick()

        assertEquals(1, backs)
    }

    private fun setContent(onBack: () -> Unit = {}) {
        composeRule.setContent {
            BOCantabriaTheme { AskScreen(onBack = onBack) }
        }
    }
}
