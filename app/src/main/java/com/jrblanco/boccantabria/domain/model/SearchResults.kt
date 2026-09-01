package com.jrblanco.boccantabria.domain.model

/**
 * What a search found, and whether it found more than it is showing.
 *
 * [isTruncated] exists so the screen can say so out loud. A list quietly cut short reads as a
 * complete list, and somebody would conclude the archive holds nothing else.
 */
data class SearchResults(
    val items: List<Publication>,
    val isTruncated: Boolean = false,
) {
    val isEmpty: Boolean get() = items.isEmpty()

    companion object {
        val EMPTY: SearchResults = SearchResults(emptyList())
    }
}
