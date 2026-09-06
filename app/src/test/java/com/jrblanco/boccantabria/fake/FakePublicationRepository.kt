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

    /** What `observePublication` returns. `null` means the publication is no longer stored. */
    var publicationsByKey: Map<String, Publication> = emptyMap()

    override fun observePublication(externalKey: String): Flow<Publication?> =
        publications.map { stored ->
            publicationsByKey[externalKey] ?: stored.firstOrNull { it.externalKey == externalKey }
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
        onRefresh?.invoke()
        return refreshResult
    }

    /** Runs inside [refresh], so a test can change the world mid-synchronisation. */
    var onRefresh: (suspend () -> Unit)? = null

    val keysAsked: MutableList<Set<String>> = mutableListOf()

    override suspend fun byKeys(keys: Set<String>): List<Publication> {
        keysAsked += keys
        val known = publications.value + publicationsByKey.values
        return known.filter { it.externalKey in keys }.distinctBy { it.externalKey }
    }

    override suspend fun newest(limit: Int): List<Publication> = publications.value.take(limit)

    var lastSuccessAt: Long? = null

    override suspend fun lastSuccessfulSyncAt(): Long? = lastSuccessAt
}
