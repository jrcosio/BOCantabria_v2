package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.data.source.local.PublicationSearchDao
import com.jrblanco.boccantabria.data.source.local.likeContains
import com.jrblanco.boccantabria.data.source.local.toDomain
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.model.SearchQuery
import com.jrblanco.boccantabria.domain.model.SearchSort
import com.jrblanco.boccantabria.domain.repository.SearchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Searching the stored bulletin.
 *
 * Everything it needs is already on the device, so there is no network path here at all and nothing
 * to do differently when the phone is offline.
 *
 * The pattern is built here rather than in the data-access object because it is two rules stitched
 * together —normalise the way the stored column was normalised, then escape what SQL would read as
 * a wildcard— and a statement is no place for either.
 */
class SearchRepositoryImpl(
    private val searchDao: PublicationSearchDao,
    private val dispatchers: DispatcherProvider,
    private val crashReporter: CrashReporter,
) : SearchRepository {

    override fun search(query: SearchQuery, limit: Int): Flow<List<Publication>> {
        val pattern = likeContains(query.normalisedText)
        val from = query.from?.toString()
        val to = query.to?.toString()

        val rows = when (query.sort) {
            SearchSort.NEWEST_FIRST -> searchDao.searchNewestFirst(
                pattern, query.sectionCode, query.subsectionCode, query.issuer, from, to, limit,
            )
            SearchSort.OLDEST_FIRST -> searchDao.searchOldestFirst(
                pattern, query.sectionCode, query.subsectionCode, query.issuer, from, to, limit,
            )
        }

        return rows
            .map { entities -> entities.map { it.toDomain() } }
            // A local read failure must not kill the flow: the screen would be left with no state
            // at all, which reads as a frozen application rather than as an empty result.
            .catch { cause -> emitEmptyAfterRecording(cause) }
            .flowOn(dispatchers.io)
    }

    override fun observeIssuers(): Flow<List<String>> = searchDao.observeIssuers()
        .catch { cause -> emitEmptyAfterRecording(cause) }
        .flowOn(dispatchers.io)

    private suspend fun <T> kotlinx.coroutines.flow.FlowCollector<List<T>>.emitEmptyAfterRecording(
        cause: Throwable,
    ) {
        if (cause is CancellationException) throw cause
        crashReporter.recordNonFatal(cause)
        emit(emptyList())
    }
}
