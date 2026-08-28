package com.jrblanco.boccantabria.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Route {

    @Serializable
    data object Splash : Route

    /**
     * The bulletin.
     *
     * The selection travels as an argument rather than as shared state because the sections
     * panel wraps the navigation host and could not otherwise reach the screen's view model. It
     * also means the selection survives process death without a line of code.
     */
    @Serializable
    data class Home(
        val sectionCode: String? = null,
        val subsectionCode: String? = null,
    ) : Route

    @Serializable
    data object Search : Route

    @Serializable
    data object Saved : Route
}
