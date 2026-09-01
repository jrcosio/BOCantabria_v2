package com.jrblanco.boccantabria.domain.model

/**
 * The order the search results come back in.
 *
 * Two values and not three. Relevance was considered and left out by the repository owner: a score
 * has to be explainable to whoever reads the list, and for the volume this application holds,
 * chronological order answers the question people actually ask — "what is the latest on this".
 */
enum class SearchSort {
    /** The default. */
    NEWEST_FIRST,
    OLDEST_FIRST,
    ;

    companion object {
        val DEFAULT: SearchSort = NEWEST_FIRST

        /**
         * Restored **by name**, never with `valueOf`, and never by ordinal.
         *
         * A saved name that no longer exists would otherwise bring the screen down on the one path
         * nobody walks by hand: coming back from process death. Same rule the detail screen's tabs
         * already follow.
         */
        fun byNameOrDefault(name: String?): SearchSort =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
