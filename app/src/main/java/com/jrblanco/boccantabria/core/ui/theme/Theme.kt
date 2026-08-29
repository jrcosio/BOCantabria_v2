package com.jrblanco.boccantabria.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Design tokens with no equivalent role in Material 3.
 *
 * Forcing them into roles that mean something else —putting the AI accent into `tertiary`, say—
 * would make the code lie at the point of use: nobody reading `colorScheme.tertiary` would guess it
 * paints AI content. A separate container keeps both vocabularies intact (research.md, D-001).
 */
@Immutable
data class BocExtendedColors(
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val surfaceSoft: Color,
    val surfaceStrong: Color,
    val divider: Color,
    val accentOfficial: Color,
    val aiAccent: Color,
    val aiContainer: Color,
    val success: Color,
    val warning: Color,
    /** Light blue used on the institutional background: divider line and authorship name. */
    val onPrimaryAccent: Color,
    /** White at 70 %, for secondary text over the institutional background. */
    val onPrimaryMuted: Color,
    val sectionGeneral: Color,
    val sectionPersonnel: Color,
    val sectionContracting: Color,
    val sectionEconomy: Color,
    val sectionAnnouncements: Color,
)

private val ExtendedColors = BocExtendedColors(
    textPrimary = BocTextPrimary,
    textSecondary = BocTextSecondary,
    textMuted = BocTextMuted,
    surfaceSoft = BocSurfaceSoft,
    surfaceStrong = BocSurfaceStrong,
    divider = BocDivider,
    accentOfficial = BocAccentOfficial,
    aiAccent = BocAiAccent,
    aiContainer = BocAiContainer,
    success = BocSuccess,
    warning = BocWarning,
    onPrimaryAccent = BocOnPrimaryAccent,
    onPrimaryMuted = BocOnPrimaryMuted,
    sectionGeneral = BocSectionGeneral,
    sectionPersonnel = BocSectionPersonnel,
    sectionContracting = BocSectionContracting,
    sectionEconomy = BocSectionEconomy,
    sectionAnnouncements = BocSectionAnnouncements,
)

private val ColorScheme = lightColorScheme(
    primary = BocPrimary,
    onPrimary = BocOnPrimary,
    primaryContainer = BocPrimaryContainer,
    onPrimaryContainer = BocOnPrimaryContainer,
    secondary = BocSecondary,
    onSecondary = BocOnPrimary,
    secondaryContainer = BocSecondaryContainer,
    onSecondaryContainer = BocOnPrimaryContainer,
    background = BocBackground,
    onBackground = BocTextPrimary,
    surface = BocSurface,
    onSurface = BocTextPrimary,
    surfaceVariant = BocSurfaceSoft,
    onSurfaceVariant = BocTextSecondary,
    outline = BocOutline,
    outlineVariant = BocDivider,
    error = BocError,
    onError = BocOnPrimary,
    errorContainer = BocSurfaceStrong,
    onErrorContainer = BocError,

    // Every remaining role, filled on purpose. A role left unset does not stay neutral: Material
    // fills it with its baseline purple, and any component that happens to use it paints the
    // application in a colour the design document does not contain. It showed up as a lilac tint
    // on the sections panel, whose sheet uses `surfaceContainerLow`.
    surfaceContainerLowest = BocSurface,
    surfaceContainerLow = BocSurface,
    surfaceContainer = BocSurfaceSoft,
    surfaceContainerHigh = BocSurfaceSoft,
    surfaceContainerHighest = BocSurfaceStrong,
    surfaceBright = BocSurface,
    surfaceDim = BocSurfaceStrong,
    surfaceTint = BocPrimary,
    inverseSurface = BocTextPrimary,
    inverseOnSurface = BocSurface,
    inversePrimary = BocOnPrimaryAccent,

    // The palette has no third family. Pointing it at the secondary one keeps a component that
    // reaches for `tertiary` inside the institutional range — and, just as important, stops it
    // looking like AI content, which is the one thing that is legitimately violet here.
    tertiary = BocSecondary,
    onTertiary = BocOnPrimary,
    tertiaryContainer = BocSecondaryContainer,
    onTertiaryContainer = BocOnPrimaryContainer,
)

private val LocalBocExtendedColors = staticCompositionLocalOf<BocExtendedColors> {
    error("BocExtendedColors not provided. Wrap the composable in BOCantabriaTheme.")
}

/**
 * The application theme. There is exactly one, and it does not vary.
 *
 * No dark-mode parameter and no dynamic-colour parameter, on purpose. An official publication has
 * to look the same on every device, so the appearance must not depend on the phone's wallpaper or
 * on its light/dark setting. Both mechanisms are **removed** rather than defaulted to a safe value:
 * a switch with a safe default is still a switch, and sooner or later someone flips it. With
 * `isSystemInDarkTheme()` never called and no dark scheme to select, the appearance cannot vary by
 * accident (research.md, D-002 and D-013).
 *
 * There is an architecture test that fails the build if either mechanism comes back.
 */
@Composable
fun BOCantabriaTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBocExtendedColors provides ExtendedColors,
        LocalBocSpacing provides BocSpacing(),
        LocalBocElevation provides BocElevation(),
    ) {
        MaterialTheme(
            colorScheme = ColorScheme,
            typography = BocTypography,
            shapes = BocShapes,
            content = content,
        )
    }
}

/**
 * Access to the tokens Material 3 does not cover.
 *
 * Reading any of these outside [BOCantabriaTheme] fails loudly rather than returning a silent
 * default: a component without a theme is a programming error, not a case to tolerate.
 */
object BocTheme {

    val colors: BocExtendedColors
        @Composable @ReadOnlyComposable get() = LocalBocExtendedColors.current

    val spacing: BocSpacing
        @Composable @ReadOnlyComposable get() = LocalBocSpacing.current

    val elevation: BocElevation
        @Composable @ReadOnlyComposable get() = LocalBocElevation.current
}
