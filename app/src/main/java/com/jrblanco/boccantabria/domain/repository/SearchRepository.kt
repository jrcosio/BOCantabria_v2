package com.jrblanco.boccantabria.domain.repository

import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.model.SearchQuery
import kotlinx.coroutines.flow.Flow

/**
 * Searching what the device already holds.
 *
 * Nothing here talks to the network: the archive is local, and so is every answer. Being offline
 * changes nothing about what this returns.
 *
 * **An empty list is a success**, never a failure. "Nothing matched" and "the read went wrong" are
 * told apart in the presentation layer, as everywhere else in this project; a local read failure is
 * recorded as non-fatal and emits an empty list rather than terminating the flow, because a screen
 * left with no state at all reads as a frozen application.
 */
interface SearchRepository {

    /** Re-emits whenever the stored bulletin changes, so a synchronisation refreshes what is shown. */
    fun search(query: SearchQuery, limit: Int): Flow<List<Publication>>

    /** The issuers that actually have something stored behind them, alphabetically. */
    fun observeIssuers(): Flow<List<String>>
}
