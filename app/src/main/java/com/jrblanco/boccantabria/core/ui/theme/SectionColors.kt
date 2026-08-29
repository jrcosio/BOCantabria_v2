package com.jrblanco.boccantabria.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.jrblanco.boccantabria.domain.model.SectionColorGroup

/**
 * Turns a section's colour group into the colour the design document assigns to it.
 *
 * It lives in the theme package because that is the only place allowed to name a colour — an
 * architecture rule enforces it. The domain says which *group* a section belongs to; what that
 * group looks like is a decision of the design system, and this function is the seam between the
 * two.
 */
@Composable
@ReadOnlyComposable
fun sectionColor(group: SectionColorGroup): Color = when (group) {
    SectionColorGroup.GENERAL -> BocTheme.colors.sectionGeneral
    SectionColorGroup.PERSONNEL -> BocTheme.colors.sectionPersonnel
    SectionColorGroup.CONTRACTING -> BocTheme.colors.sectionContracting
    SectionColorGroup.ECONOMY -> BocTheme.colors.sectionEconomy
    SectionColorGroup.ANNOUNCEMENTS -> BocTheme.colors.sectionAnnouncements
}
