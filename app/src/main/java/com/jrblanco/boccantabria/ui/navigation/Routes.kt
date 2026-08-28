package com.jrblanco.boccantabria.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations.
 *
 * Serializable objects rather than route strings: a typo or a wrong argument becomes a
 * compilation error instead of a crash the first time someone taps the button.
 */
sealed interface Route {

    @Serializable
    data object Home : Route
}
