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

    /**
     * One publication in full.
     *
     * Carries the key and not the publication: a serialised copy in the route would age, and the
     * screen would keep showing a title a later synchronisation had already corrected. Observing
     * the stored copy also means "this is no longer stored" arrives as information rather than as
     * a blank screen.
     *
     * Lives in the outer graph, beside the cover: it has its own action bar and must not draw the
     * bottom navigation.
     */
    @Serializable
    data class Detail(val externalKey: String) : Route

    /** The official document, full screen. Reached from the detail screen. */
    @Serializable
    data class PdfViewer(val externalKey: String) : Route

    /**
     * Asking about the document.
     *
     * Carries the key although the placeholder does not read it: the conversation this becomes will
     * be about *this* document, and adding the argument later would mean changing a route that is
     * already out in the world.
     */
    @Serializable
    data class Ask(val externalKey: String) : Route
}
