package com.jrblanco.boccantabria.core.ui.theme

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Forcing the configuration to night must not change a single colour.
 *
 * The architecture rule proves the mechanism is absent from the source; this proves the behaviour
 * from the outside, which is what the specification actually promises (FR-016b, SC-005).
 */
class SingleThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_palette_is_identical_in_day_and_night_configurations() {
        var day: Palette? = null
        var night: Palette? = null

        // Both captures happen in a single composition: setContent may only be called once per
        // test, so capturing them in two separate calls throws.
        composeRule.setContent {
            WithNightMode(enabled = false) { day = capturePalette() }
            WithNightMode(enabled = true) { night = capturePalette() }
        }
        composeRule.waitForIdle()

        assertEquals(day, night)
    }

    @Test
    fun the_night_configuration_still_yields_the_institutional_palette() {
        var palette: Palette? = null

        composeRule.setContent {
            WithNightMode(enabled = true) { palette = capturePalette() }
        }
        composeRule.waitForIdle()

        val captured = requireNotNull(palette)
        assertEquals(BocBackground, captured.background)
        assertEquals(BocSurface, captured.surface)
        assertEquals(BocPrimary, captured.primary)
        assertEquals(BocTextPrimary, captured.textPrimary)
        assertEquals(BocOnPrimaryAccent, captured.onPrimaryAccent)
    }
}

@Composable
private fun WithNightMode(enabled: Boolean, content: @Composable () -> Unit) {
    val configuration = Configuration(LocalConfiguration.current).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (enabled) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    }
    CompositionLocalProvider(LocalConfiguration provides configuration) {
        BOCantabriaTheme(content = content)
    }
}

@Composable
private fun capturePalette(): Palette = Palette(
    background = MaterialTheme.colorScheme.background,
    surface = MaterialTheme.colorScheme.surface,
    primary = MaterialTheme.colorScheme.primary,
    onPrimary = MaterialTheme.colorScheme.onPrimary,
    textPrimary = BocTheme.colors.textPrimary,
    textSecondary = BocTheme.colors.textSecondary,
    divider = BocTheme.colors.divider,
    onPrimaryAccent = BocTheme.colors.onPrimaryAccent,
)

private data class Palette(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val onPrimary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val onPrimaryAccent: Color,
)
