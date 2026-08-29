package com.jrblanco.boccantabria.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Every colour role has to hold an institutional colour.
 *
 * A role left unset does not stay neutral: Material fills it with its baseline purple, and any
 * component that happens to reach for it paints the application in a colour the design document
 * does not contain. It reached a screen once — a lilac tint on the sections panel, whose sheet
 * uses `surfaceContainerLow` — and this is what stops it happening again.
 *
 * Phrased as "inside the palette" rather than "different from Material's default", because some
 * institutional values legitimately coincide with the baseline: white is white.
 *
 * Lives in `androidTest` because reading a colour means naming the type, and an architecture rule
 * keeps `androidx.compose.ui.graphics.Color` out of every `main` file outside this package.
 */
class InstitutionalPaletteTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun every_role_holds_a_colour_from_the_design_document() {
        lateinit var offenders: List<String>

        composeRule.setContent {
            BOCantabriaTheme {
                offenders = MaterialTheme.colorScheme.roles()
                    .filterNot { (_, colour) -> colour in PALETTE }
                    .map { (name, colour) -> "$name = $colour" }
            }
        }

        assertTrue(
            "roles fuera de la paleta institucional:\n  " + offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }

    @Test
    fun only_the_ai_accent_is_violet() {
        lateinit var violet: List<String>

        composeRule.setContent {
            BOCantabriaTheme {
                violet = MaterialTheme.colorScheme.roles()
                    .filter { (_, colour) -> colour == BocAiAccent || colour == BocAiContainer }
                    .map { it.first }
            }
        }

        // The violet of the palette belongs to AI content. A Material role wearing it would make
        // ordinary content look generated.
        assertTrue("roles que se confundirían con contenido de IA: $violet", violet.isEmpty())
    }

    private companion object {

        /** Everything the design document declares, plus the neutrals a scheme needs. */
        val PALETTE: Set<Color> = setOf(
            BocPrimary, BocOnPrimary, BocPrimaryPressed, BocPrimaryContainer, BocOnPrimaryContainer,
            BocSecondary, BocSecondaryPressed, BocSecondaryContainer,
            BocAccentOfficial, BocAiAccent, BocAiContainer,
            BocBackground, BocSurface, BocSurfaceSoft, BocSurfaceStrong,
            BocTextPrimary, BocTextSecondary, BocTextMuted,
            BocOutline, BocDivider,
            BocSuccess, BocWarning, BocError,
            BocOnPrimaryAccent, BocOnPrimaryMuted,
            BocSectionGeneral, BocSectionPersonnel, BocSectionContracting,
            BocSectionEconomy, BocSectionAnnouncements,
            Color.White, Color.Black, Color.Transparent,
        )

        fun ColorScheme.roles(): List<Pair<String, Color>> = listOf(
            "primary" to primary, "onPrimary" to onPrimary,
            "primaryContainer" to primaryContainer, "onPrimaryContainer" to onPrimaryContainer,
            "inversePrimary" to inversePrimary,
            "secondary" to secondary, "onSecondary" to onSecondary,
            "secondaryContainer" to secondaryContainer, "onSecondaryContainer" to onSecondaryContainer,
            "tertiary" to tertiary, "onTertiary" to onTertiary,
            "tertiaryContainer" to tertiaryContainer, "onTertiaryContainer" to onTertiaryContainer,
            "background" to background, "onBackground" to onBackground,
            "surface" to surface, "onSurface" to onSurface,
            "surfaceVariant" to surfaceVariant, "onSurfaceVariant" to onSurfaceVariant,
            "surfaceTint" to surfaceTint,
            "inverseSurface" to inverseSurface, "inverseOnSurface" to inverseOnSurface,
            "error" to error, "onError" to onError,
            "errorContainer" to errorContainer, "onErrorContainer" to onErrorContainer,
            "outline" to outline, "outlineVariant" to outlineVariant,
            "scrim" to scrim,
            "surfaceBright" to surfaceBright, "surfaceDim" to surfaceDim,
            "surfaceContainer" to surfaceContainer,
            "surfaceContainerHigh" to surfaceContainerHigh,
            "surfaceContainerHighest" to surfaceContainerHighest,
            "surfaceContainerLow" to surfaceContainerLow,
            "surfaceContainerLowest" to surfaceContainerLowest,
        )
    }
}
