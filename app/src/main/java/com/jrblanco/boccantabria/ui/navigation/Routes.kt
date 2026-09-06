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

    /**
     * The archive-wide search.
     *
     * [query] is how the bulletin's in-place search hands over what somebody had already typed when
     * it found nothing in the edition on screen. Same mechanism [Home] uses for the section, and for
     * the same reason: the route is the single source of truth of where the application is, and an
     * argument survives process death without a line of code.
     *
     * The property is called `query` on purpose: that is the key the search screen reads and writes
     * in its saved state. Two different names would break the hand-off **silently** — no error, just
     * a search screen that opened empty.
     */
    @Serializable
    data class Search(val query: String? = null) : Route

    @Serializable
    data object Saved : Route

    /**
     * Alerts: the fourth destination of the bottom bar (feature 012).
     *
     * [tab] is the name of the tab to open —`NEWS` or `RULES`— or `null` for the default. A tapped
     * group summary and the in-app «VER» both land on the news tab through it. Navigated **without**
     * `restoreState` when it carries a value: saved state wins over a route argument, and in
     * feature 006 that swallowed a handed-over search term without any error at all.
     *
     * The property is called `tab` on purpose: it is the key the alerts screen reads from its saved
     * state, and the name is restored tolerantly — never with `valueOf`.
     */
    @Serializable
    data class Alerts(val tab: String? = null) : Route

    /**
     * Creating or editing an alert rule.
     *
     * Lives in the **outer** graph, beside Info: it has its own blue bar with a back arrow and must not
     * draw the bottom navigation. `ruleId` means edit; `duplicateOf` means start from a paused copy of
     * that rule; neither means create. Both at once is a caller's mistake and the screen treats it as
     * an edit.
     */
    @Serializable
    data class AlertForm(val ruleId: String? = null, val duplicateOf: String? = null) : Route

    /** Static application and author information, reached from the bulletin's top bar. */
    @Serializable
    data object Info : Route

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

    /**
     * The official document, full screen. Reached from the detail screen.
     *
     * @param page where to open, 0-based. Defaults to the first so the existing callers — the
     *   detail action bar — keep working untouched. It exists so a page reference in the AI summary
     *   can be **followed**: a reference nobody can check is not a reference (FR-021).
     */
    @Serializable
    data class PdfViewer(val externalKey: String, val page: Int = 0) : Route

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
