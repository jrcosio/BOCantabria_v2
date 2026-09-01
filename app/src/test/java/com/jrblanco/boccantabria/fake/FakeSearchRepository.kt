package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.model.SearchQuery
import com.jrblanco.boccantabria.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A store the test programmes, which records what it was asked for. */
class FakeSearchRepository : SearchRepository {

    private val results = MutableStateFlow<List<Publication>>(emptyList())
    private val issuers = MutableStateFlow<List<String>>(emptyList())

    val queries: MutableList<SearchQuery> = mutableListOf()
    val limits: MutableList<Int> = mutableListOf()

    fun emit(publications: List<Publication>) {
        results.value = publications
    }

    fun emitIssuers(names: List<String>) {
        issuers.value = names
    }

    override fun search(query: SearchQuery, limit: Int): Flow<List<Publication>> {
        queries += query
        limits += limit
        return results
    }

    override fun observeIssuers(): Flow<List<String>> = issuers
}
