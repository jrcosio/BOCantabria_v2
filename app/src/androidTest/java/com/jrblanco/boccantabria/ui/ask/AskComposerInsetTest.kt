package com.jrblanco.boccantabria.ui.ask

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.ui.ask.component.AskComposer
import com.jrblanco.boccantabria.ui.ask.component.TAG_COMPOSER_SEND
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Regression: the composer must not be drawn underneath the system navigation buttons.
 *
 * A `Scaffold` is no help. The moment it is given a `bottomBar` it throws away its own bottom inset
 * and pins the bar to the raw window edge, so keeping clear of the navigation buttons is the bar's own
 * job. This is the same lesson `DetailActionBarInsetTest` fixed for the detail's action bar, and it
 * had to be learned again here because a `Scaffold` behaves the same way every time
 * (011 research.md D-324).
 *
 * **On a device using gesture navigation the bottom inset can be zero and this proves nothing.** The
 * instrumented suite is meant to run with three-button navigation for exactly this reason:
 * `adb shell settings put secure navigation_mode 0`, written down in `quickstart.md`.
 */
class AskComposerInsetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_send_button_stays_clear_of_the_system_navigation_bar() {
        var insetPx = 0

        composeRule.setContent {
            BOCantabriaTheme {
                insetPx = WindowInsets.systemBars.getBottom(LocalDensity.current)
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    AskComposer(
                        draft = "¿Cuál es el plazo?",
                        onDraftChange = {},
                        onSend = {},
                        canSend = true,
                        enabled = true,
                        showCounter = false,
                        isOverLimit = false,
                    )
                }
            }
        }

        val rootBottom = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        val buttonBottom = composeRule.onNodeWithTag(TAG_COMPOSER_SEND)
            .fetchSemanticsNode().boundsInRoot.bottom

        // What the requirement says and nothing more: the button ends before the strip the navigation
        // bar occupies. Without the fix the gap would be the composer's own padding, well short of
        // the inset.
        val gap = (rootBottom - buttonBottom).toInt()
        assertTrue(
            "el botón de enviar se queda a $gap px del borde y la barra del sistema ocupa $insetPx",
            gap >= insetPx,
        )
    }
}
