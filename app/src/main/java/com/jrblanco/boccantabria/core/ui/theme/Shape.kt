package com.jrblanco.boccantabria.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii (§8.1).
 *
 * The ones with a Material 3 equivalent live in [BocShapes]; those without —bottom sheets, dialogs,
 * banners— are exposed as named shapes, because Material 3 has no slot that means them.
 */
val BocShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),      // Button
    medium = RoundedCornerShape(14.dp),     // Standard card, text field
    large = RoundedCornerShape(18.dp),      // Featured card
    extraLarge = RoundedCornerShape(28.dp),
)

/** Bottom sheet: 28 dp on the top corners only (§8.1). */
val BocBottomSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** Dialog (§8.1). */
val BocDialogShape = RoundedCornerShape(24.dp)

/** Banner (§8.1). */
val BocBannerShape = RoundedCornerShape(12.dp)
