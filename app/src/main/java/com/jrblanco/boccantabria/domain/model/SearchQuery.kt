package com.jrblanco.boccantabria.domain.model

import com.jrblanco.boccantabria.core.util.SearchText
import java.time.LocalDate

/**
 * Everything that defines one search of the whole archive: the text, the filters and the order.
 *
 * This is what survives opening a result and coming back, and what travels in and out of the saved
 * state. Keeping it in one immutable value means the screen has a single thing to persist and the
 * rules about filters have somewhere to live that is not a composable.
 *
 * **There is deliberately no `require` on the date range.** An inverted range is prevented in the
 * interface —the apply action stays disabled and the "to" picker offers nothing before "from"— and
 * turning a handling mistake into a crash would be a poor trade.
 */
data class SearchQuery(
    val text: String = "",
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val sectionCode: String? = null,
    val subsectionCode: String? = null,
    val issuer: String? = null,
    val sort: SearchSort = SearchSort.DEFAULT,
) {

    /** The text as everything stored is stored: lower case, no accents, no stray spaces. */
    val normalisedText: String get() = SearchText.normalise(text)

    /**
     * Whether there is enough to go to the archive with.
     *
     * One character would come back with a large slice of everything ever downloaded and help
     * nobody. The bulletin's in-place search has no such floor, because it only trims a list that
     * is already on screen.
     */
    val isRunnable: Boolean get() = normalisedText.length >= MIN_TEXT_LENGTH

    /** How many filters are on. A date range counts as one, because it reads as one. */
    val activeFilterCount: Int
        get() = listOf(
            from != null || to != null,
            sectionCode != null,
            subsectionCode != null,
            issuer != null,
        ).count { it }

    val hasFilters: Boolean get() = activeFilterCount > 0

    /** True while the range says something impossible. The interface refuses to apply it. */
    val hasInvalidDateRange: Boolean
        get() = from != null && to != null && from.isAfter(to)

    /** Drops every filter and **keeps the text and the order**. */
    fun clearedFilters(): SearchQuery =
        copy(from = null, to = null, sectionCode = null, subsectionCode = null, issuer = null)

    fun withoutDateRange(): SearchQuery = copy(from = null, to = null)

    /** Dropping the section drops its subsection with it: a subsection alone means nothing here. */
    fun withoutSection(): SearchQuery = copy(sectionCode = null, subsectionCode = null)

    fun withoutSubsection(): SearchQuery = copy(subsectionCode = null)

    fun withoutIssuer(): SearchQuery = copy(issuer = null)

    /**
     * Choosing a section clears a subsection that does not belong to it.
     *
     * The rule lives here rather than in the filter sheet so that it has a test of its own, and so
     * that no future caller can forget it. Subsection codes are written as `"<section>.<n>"`.
     */
    fun withSection(code: String?): SearchQuery {
        val keepsSubsection = code != null && subsectionCode?.startsWith("$code.") == true
        return copy(sectionCode = code, subsectionCode = if (keepsSubsection) subsectionCode else null)
    }

    companion object {
        const val MIN_TEXT_LENGTH: Int = 2
    }
}
