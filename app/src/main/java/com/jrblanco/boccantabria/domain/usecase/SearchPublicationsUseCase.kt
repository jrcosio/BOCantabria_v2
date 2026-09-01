package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.SearchQuery
import com.jrblanco.boccantabria.domain.model.SearchResults
import com.jrblanco.boccantabria.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Searching the whole archive, capped at what a screen can honestly show.
 *
 * It asks the store for **one more row than it will show**. That extra row is the only thing that
 * tells "there are exactly three hundred" apart from "there are more than three hundred", and it
 * costs nothing next to a second counting query. When it comes back, the screen says so out loud
 * rather than presenting a list that was quietly cut short.
 *
 * A query with nothing to search for never reaches the store: one character would come back with a
 * large slice of everything ever downloaded and help nobody.
 */
class SearchPublicationsUseCase(private val repository: SearchRepository) {

    operator fun invoke(query: SearchQuery): Flow<SearchResults> {
        if (!query.isRunnable) return flowOf(SearchResults.EMPTY)

        return repository.search(query, limit = MAX_RESULTS + 1).map { found ->
            SearchResults(
                items = found.take(MAX_RESULTS),
                isTruncated = found.size > MAX_RESULTS,
            )
        }
    }

    companion object {
        /**
         * As many as a list can show without lying about it.
         *
         * Not paging: this project has no paging library, and adding one for a single screen would
         * be a dependency and a second data model. With filters, three hundred is plenty.
         */
        const val MAX_RESULTS: Int = 300
    }
}
