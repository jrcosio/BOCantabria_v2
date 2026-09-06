package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.domain.model.AlertCandidate
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.BulletinHeaderData
import com.jrblanco.boccantabria.domain.model.DomainError
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
        val result = refreshResult
        // What the real repository does since feature 014: the rows a refresh inserts are marked
        // pending, stamped with when they were stored; the baseline marks nothing.
        if (result is AppResult.Success && !result.data.isBaseline) {
            result.data.newKeys.forEach { key -> pendingKeys.putIfAbsent(key, now) }
        }
        return result
    }

    /** Runs inside [refresh], so a test can change the world mid-synchronisation. */
    var onRefresh: (suspend () -> Unit)? = null

    // ---------- What the alerts evaluate (feature 014) ----------

    /** The clock that stamps `storedAt`. Mirrors `FakeAlertRepository.now`. */
    var now: Long = 1_000_000L

    /** Key → when it was stored. What a cycle reads, and what it clears when it is done. */
    val pendingKeys: MutableMap<String, Long> = mutableMapOf()

    /** How many times the pending set was read: the cycle must ask once, and only when it may. */
    var pendingReads: Int = 0
        private set

    /** Every `markAlertsEvaluated` call, in order. */
    val markCalls: MutableList<Set<String>> = mutableListOf()

    var failPendingRead: Boolean = false
    var failMarkEvaluated: Boolean = false

    /** Leaves a publication as an earlier cycle would have: stored, known, and still pending. */
    fun seedPending(publication: Publication, storedAt: Long) {
        publicationsByKey = publicationsByKey + (publication.externalKey to publication)
        pendingKeys[publication.externalKey] = storedAt
    }

    override suspend fun pendingAlertCandidates(): AppResult<List<AlertCandidate>> {
        pendingReads++
        if (failPendingRead) return AppResult.Failure(DomainError.Unknown)
        val known = (publications.value + publicationsByKey.values).associateBy { it.externalKey }
        return AppResult.Success(
            pendingKeys.mapNotNull { (key, storedAt) -> known[key]?.let { AlertCandidate(it, storedAt) } },
        )
    }

    override suspend fun markAlertsEvaluated(keys: Set<String>): AppResult<Unit> {
        markCalls += keys
        if (failMarkEvaluated) return AppResult.Failure(DomainError.Unknown)
        keys.forEach(pendingKeys::remove)
        return AppResult.Success(Unit)
    }

    override suspend fun newest(limit: Int): List<Publication> = publications.value.take(limit)

    var lastSuccessAt: Long? = null

    override suspend fun lastSuccessfulSyncAt(): Long? = lastSuccessAt
}
