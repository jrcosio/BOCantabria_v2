package com.jrblanco.boccantabria.ui.detail

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
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.ui.detail.component.DetailActionBar
import com.jrblanco.boccantabria.ui.detail.component.TAG_ACTION_OPEN
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Regression: the action bar must not be drawn underneath the system navigation buttons.
 *
 * A Scaffold is no help here. The moment it is given a `bottomBar` it throws away its own bottom
 * inset and pins the bar to the raw window edge, so keeping clear of the navigation buttons is the
 * bar's own job. This asserts it does that job.
 *
 * On a device using gesture navigation the bottom inset can be zero and the assertion, while still
 * correct, proves nothing. The instrumented suite is meant to run on an emulator with **three-button
 * navigation** for exactly this reason; it is written down in `quickstart.md`.
 */
class DetailActionBarInsetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_buttons_stay_clear_of_the_system_navigation_bar() {
        var insetPx = 0
        var barPaddingPx = 0

        composeRule.setContent {
            BOCantabriaTheme {
                // Read inside the theme: the spacing tokens travel in a composition local that
                // only exists under it.
                insetPx = WindowInsets.systemBars.getBottom(LocalDensity.current)
                barPaddingPx = with(LocalDensity.current) { BocTheme.spacing.space3.roundToPx() }
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    DetailActionBar(onOpen = {}, onAsk = {})
                }
            }
        }

        val rootBottom = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        val buttonBottom = composeRule.onNodeWithTag(TAG_ACTION_OPEN)
            .fetchSemanticsNode().boundsInRoot.bottom

        // What the requirement says, and nothing more: the button ends before the strip the
        // navigation bar occupies. An exact gap was tried first and encoded Material's own
        // minimum-touch-target arithmetic, which is not ours to pin down.
        //
        // It still fails without the fix: the gap would then be the bar's own padding
        // ($barPaddingPx px), well short of the inset.
        val gap = (rootBottom - buttonBottom).toInt()
        assertTrue(
            "el botón principal se queda a $gap px del borde y la barra del sistema ocupa $insetPx",
            gap >= insetPx,
        )
    }
}
