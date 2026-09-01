package com.jrblanco.boccantabria.ui.search

import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.model.SearchQuery
import com.jrblanco.boccantabria.ui.share.ShareState

/**
 * Everything the search screen draws.
 *
 * [share], [savedKeys] and [saveFailed] sit **outside** [content] for the same reason they do on the
 * bulletin and on the saved list: they are independent axes. A document can be being prepared for
 * sharing with results on screen, and a write that failed is not a kind of content.
 */
data class SearchUiState(
    val query: SearchQuery = SearchQuery(),
    val content: SearchContentState = SearchContentState.Initial,
    /** The organisations the filter sheet can offer. Only those with something behind them. */
    val issuers: List<String> = emptyList(),
    val share: ShareState = ShareState.Idle,
    /** The keys of everything saved. A result asks whether its own is in here. */
    val savedKeys: Set<String> = emptySet(),
    /** One-shot: the screen says so and clears it. */
    val saveFailed: Boolean = false,
)

/**
 * What the results area holds.
 *
 * **There is no loading state**, and that is a decision rather than an omission — the same one
 * `SavedContentState` made. What is read is local and immediate, so there is no wait to cushion, and
 * a case nobody ever emits is a dead branch of the screen's `when`.
 */
sealed interface SearchContentState {

    /** Not enough typed yet. Neither an empty result nor a failure: nothing has been asked. */
    data object Initial : SearchContentState

    data class Results(val items: List<Publication>, val isTruncated: Boolean) : SearchContentState

    /** Asked, and nothing matched. A normal state. */
    data object Empty : SearchContentState
}
