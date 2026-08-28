package com.jrblanco.boccantabria.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevation levels (§8.2).
 *
 * The specification prefers separating surfaces by background contrast over stacking shadows, so
 * these stay deliberately low.
 */
@Immutable
data class BocElevation(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level2: Dp = 3.dp,
    val level3: Dp = 6.dp,
    val level4: Dp = 8.dp,
)

internal val LocalBocElevation = staticCompositionLocalOf<BocElevation> {
    error("BocElevation not provided. Wrap the composable in BOCantabriaTheme.")
}
