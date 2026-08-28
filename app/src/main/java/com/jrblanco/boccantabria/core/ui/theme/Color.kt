package com.jrblanco.boccantabria.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Institutional palette, from the design specification (§4.1, §4.4 and §5.1).
 *
 * Names are tokens, not colours: `BocPrimary`, never `BocBlue`. A token survives a change of hue;
 * a colour name becomes a lie the first time the palette is revised.
 *
 * Nothing outside this package may declare a colour. There is an architecture test asserting it.
 */

// ---------- Light mode (§4.1) ----------

val BocPrimary = Color(0xFF063B5C)
val BocOnPrimary = Color(0xFFFFFFFF)
val BocPrimaryPressed = Color(0xFF042C45)
val BocPrimaryContainer = Color(0xFFDCEEF6)
val BocOnPrimaryContainer = Color(0xFF082F45)

val BocSecondary = Color(0xFF087EA4)
val BocSecondaryPressed = Color(0xFF056686)
val BocSecondaryContainer = Color(0xFFDDF3FA)

val BocAccentOfficial = Color(0xFFC62828)
val BocAiAccent = Color(0xFF6650A4)
val BocAiContainer = Color(0xFFF1EDFA)

val BocBackground = Color(0xFFF6F8FA)
val BocSurface = Color(0xFFFFFFFF)
val BocSurfaceSoft = Color(0xFFF0F4F7)
val BocSurfaceStrong = Color(0xFFE6EDF1)

val BocTextPrimary = Color(0xFF122B3A)
val BocTextSecondary = Color(0xFF536873)
val BocTextMuted = Color(0xFF778993)

val BocOutline = Color(0xFFB8C4CB)
val BocDivider = Color(0xFFD9E0E4)

val BocSuccess = Color(0xFF2E7D32)
val BocWarning = Color(0xFFED6C02)
val BocError = Color(0xFFBA1A1A)

// ---------- Dark mode (§5.1) ----------

val BocPrimaryDark = Color(0xFF8FD3EE)
val BocOnPrimaryDark = Color(0xFF003549)
val BocPrimaryContainerDark = Color(0xFF064A6B)

val BocBackgroundDark = Color(0xFF0F171C)
val BocSurfaceDark = Color(0xFF182229)
val BocSurfaceSoftDark = Color(0xFF202D35)

val BocTextPrimaryDark = Color(0xFFE7F0F4)
val BocTextSecondaryDark = Color(0xFFB5C5CD)

val BocOutlineDark = Color(0xFF647680)
val BocAiContainerDark = Color(0xFF29243A)

// ---------- Section colours (§4.4) ----------

val BocSectionGeneral = Color(0xFF1565C0)
val BocSectionPersonnel = Color(0xFF6A4C93)
val BocSectionContracting = Color(0xFF00838F)
val BocSectionEconomy = Color(0xFF2E7D32)
val BocSectionAnnouncements = Color(0xFFAD5B00)

// ---------- Splash screen ----------

/** White at 70 %, for the authorship label over the institutional blue (§13.2). */
val BocOnPrimaryMuted = Color(0xB3FFFFFF)
