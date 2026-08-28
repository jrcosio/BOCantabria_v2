package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.BulletinHeaderData
import com.jrblanco.boccantabria.domain.model.HomeSelection
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.model.SyncSummary
import com.jrblanco.boccantabria.domain.repository.PublicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * A repository the test drives by hand: what it stores, whether the cache is stale and what a
 * refresh returns.
 *
 * It also records the selections it was asked about, which is how the tests check the screen
 * really re-queries when the selection changes instead of filtering in memory.
 */
class FakePublicationRepository(
    initial: List<Publication> = emptyList(),
) : PublicationRepository {

    private val publications = MutableStateFlow(initial)

    var stale: Boolean = true
    var refreshResult: AppResult<SyncSummary> = AppResult.Success(SyncSummary(succeededFeeds = 19))
    var sectionNameFor: (HomeSelection) -> String? = { null }

    val observedSelections: MutableList<HomeSelection> = mutableListOf()
    var refreshCount: Int = 0
        private set
    var staleChecks: Int = 0
        private set

    fun emit(items: List<Publication>) {
        publications.value = items
    }

    override fun observePublications(selection: HomeSelection): Flow<List<Publication>> {
        observedSelections += selection
        return publications
    }

    override fun observeHeader(selection: HomeSelection): Flow<BulletinHeaderData> =
        publications.map { items ->
            BulletinHeaderData(
                date = items.firstOrNull()?.publicationDate,
                publicationCount = items.size,
                sectionName = sectionNameFor(selection),
            )
        }

    override suspend fun isCacheStale(): Boolean {
        staleChecks++
        return stale
    }

    override suspend fun refresh(): AppResult<SyncSummary> {
        refreshCount++
        return refreshResult
    }
}
