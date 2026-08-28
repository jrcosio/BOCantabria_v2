package com.jrblanco.boccantabria.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale on a 4 dp base grid (§7.1).
 *
 * Named values rather than literals so that a layout reads as intent —"section separation"— and so
 * that revising the grid is one edit here instead of a hunt through every screen.
 */
@Immutable
data class BocSpacing(
    val space1: Dp = 4.dp,
    val space2: Dp = 8.dp,
    val space3: Dp = 12.dp,
    val space4: Dp = 16.dp,
    val space5: Dp = 20.dp,
    val space6: Dp = 24.dp,
    val space8: Dp = 32.dp,
    val space10: Dp = 40.dp,
    val space12: Dp = 48.dp,
) {
    /** Standard screen margin on a phone (§7.2). */
    val screenMargin: Dp get() = space4
}

internal val LocalBocSpacing = staticCompositionLocalOf<BocSpacing> {
    error("BocSpacing not provided. Wrap the composable in BOCantabriaTheme.")
}
