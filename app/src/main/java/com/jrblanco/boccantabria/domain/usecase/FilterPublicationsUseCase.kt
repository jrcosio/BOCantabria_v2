package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.core.util.SearchText
import com.jrblanco.boccantabria.domain.model.Publication

/**
 * Trimming a list that is already on screen.
 *
 * This is the bulletin's in-place search, and it is a different operation from the archive one: it
 * touches no store, makes no request and has no minimum length, because all it does is narrow what
 * the person is already looking at.
 *
 * A use case rather than a private method of the view model, so that the matching rule has a test
 * of its own — the constitution keeps logic out of composables, and a rule with no test is a rule
 * nobody is holding.
 *
 * **The order of the input is kept.** Re-sorting here would quietly contradict the order the store
 * decided, which is the one thing a list of announcements has to be trusted about.
 */
class FilterPublicationsUseCase {

    operator fun invoke(items: List<Publication>, text: String): List<Publication> {
        val needle = SearchText.normalise(text)
        if (needle.isEmpty()) return items

        return items.filter { publication ->
            SearchText.normalise(publication.title).contains(needle) ||
                publication.issuer?.let { SearchText.normalise(it).contains(needle) } == true
        }
    }
}
