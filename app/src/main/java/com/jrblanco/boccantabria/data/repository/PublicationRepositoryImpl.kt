package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.core.telemetry.AnalyticsEvent
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.source.local.FeedSyncStateDao
import com.jrblanco.boccantabria.data.source.local.FeedSyncStateEntity
import com.jrblanco.boccantabria.data.source.local.PublicationDao
import com.jrblanco.boccantabria.data.source.local.toDomain
import com.jrblanco.boccantabria.data.source.local.toEntity
import com.jrblanco.boccantabria.data.source.remote.BocFeedDefinition
import com.jrblanco.boccantabria.data.source.remote.FeedFetchResult
import com.jrblanco.boccantabria.data.source.remote.NormalizationResult
import com.jrblanco.boccantabria.data.source.remote.PublicationNormalizer
import com.jrblanco.boccantabria.data.source.remote.PublicationRemoteDataSource
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.BulletinHeaderData
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.HomeSelection
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.model.SyncSummary
import com.jrblanco.boccantabria.domain.repository.BocSectionRepository
import com.jrblanco.boccantabria.domain.repository.PublicationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * The stored bulletin, and the only thing that writes to it.
 *
 * Two decisions shape everything here:
 *
 * - **Reading and writing are separate.** The flows come straight from the database and
 *   [refresh] returns a summary, not content. That is what lets announcements appear as each
 *   source lands rather than after all nineteen finish, and what makes the offline case free.
 * - **Nothing is ever deleted.** A source only publishes its last hundred announcements; treating
 *   its response as the truth would erase the application's own history every day.
 */
@Suppress("LongParameterList")
class PublicationRepositoryImpl(
    private val remoteDataSource: PublicationRemoteDataSource,
    private val publicationDao: PublicationDao,
    private val feedSyncStateDao: FeedSyncStateDao,
    private val normalizer: PublicationNormalizer,
    private val sectionRepository: BocSectionRepository,
    private val feeds: List<BocFeedDefinition>,
    private val time: TimeProvider,
    private val dispatchers: DispatcherProvider,
    private val analytics: AnalyticsTracker,
    private val crashReporter: CrashReporter,
) : PublicationRepository {

    override fun observePublications(selection: HomeSelection): Flow<List<Publication>> =
        selection.query()
            .map { entities -> entities.map { it.toDomain() } }
            // A local read failure must not kill the flow: the screen would be left with no state
            // at all, which reads as a frozen application rather than as an empty one.
            .catch { cause ->
                if (cause is CancellationException) throw cause
                crashReporter.recordNonFatal(cause)
                emit(emptyList())
            }
            .flowOn(dispatchers.io)

    override fun observePublication(externalKey: String): Flow<Publication?> =
        publicationDao.observePublication(externalKey)
            .map { entity -> entity?.toDomain() }
            .catch { cause ->
                if (cause is CancellationException) throw cause
                crashReporter.recordNonFatal(cause)
                emit(null)
            }
            .flowOn(dispatchers.io)

    override fun observeHeader(selection: HomeSelection): Flow<BulletinHeaderData> =
        observePublications(selection).map { publications ->
            BulletinHeaderData(
                date = publications.firstOrNull()?.publicationDate,
                publicationCount = publications.size,
                sectionName = selection.sectionName(),
            )
        }

    override suspend fun isCacheStale(): Boolean = withContext(dispatchers.io) {
        val lastSuccess = runCatching { feedSyncStateDao.lastSuccessAt() }.getOrNull()
            ?: return@withContext true
        time.nowMillis() - lastSuccess >= CACHE_TTL_MILLIS
    }

    override suspend fun refresh(): AppResult<SyncSummary> = withContext(dispatchers.io) {
        try {
            val permits = Semaphore(MAX_CONCURRENT_FEEDS)
            val summary = coroutineScope {
                feeds.filter { it.enabled }
                    .map { definition -> async { permits.withPermit { syncFeed(definition) } } }
                    .awaitAll()
            }.fold(SyncSummary(), SyncSummary::plus)

            analytics.track(summary.toEvent())

            if (summary.allFailed && publicationDao.count() == 0) {
                AppResult.Failure(DomainError.Network)
            } else {
                AppResult.Success(summary)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (unexpected: Throwable) {
            crashReporter.recordNonFatal(unexpected)
            AppResult.Failure(DomainError.Unknown)
        }
    }

    /**
     * One source, start to finish. Writes as soon as it has something, so the screen does not
     * wait for the slowest of the nineteen.
     */
    private suspend fun syncFeed(definition: BocFeedDefinition): SyncSummary = try {
        val state = feedSyncStateDao.byFeedId(definition.feedId)

        when (val result = remoteDataSource.fetchFeed(definition, state?.bodyHash)) {
            FeedFetchResult.NotModified -> {
                markSuccess(definition, state?.bodyHash)
                SyncSummary(succeededFeeds = 1, unchangedFeeds = 1)
            }

            is FeedFetchResult.Failed -> {
                markFailure(definition, state)
                SyncSummary(failedFeeds = 1)
            }

            is FeedFetchResult.Fetched -> {
                val normalized = result.channel.items.map { normalizer.normalize(it, definition) }
                val accepted = normalized.filterIsInstance<NormalizationResult.Accepted>()
                val now = time.nowMillis()

                val counts = publicationDao.upsertAll(accepted.map { it.publication.toEntity(now) })
                markSuccess(definition, result.bodyHash)

                SyncSummary(
                    succeededFeeds = 1,
                    insertedItems = counts.inserted,
                    updatedItems = counts.updated,
                    rejectedItems = normalized.size - accepted.size,
                )
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (unexpected: Throwable) {
        // One source blowing up must not take the other eighteen with it.
        crashReporter.recordNonFatal(unexpected)
        SyncSummary(failedFeeds = 1)
    }

    private suspend fun markSuccess(definition: BocFeedDefinition, bodyHash: String?) {
        feedSyncStateDao.upsert(
            FeedSyncStateEntity(
                feedId = definition.feedId,
                bodyHash = bodyHash,
                lastSuccessAt = time.nowMillis(),
                consecutiveFailures = 0,
            ),
        )
    }

    private suspend fun markFailure(definition: BocFeedDefinition, state: FeedSyncStateEntity?) {
        feedSyncStateDao.upsert(
            FeedSyncStateEntity(
                feedId = definition.feedId,
                bodyHash = state?.bodyHash,
                etag = state?.etag,
                lastModified = state?.lastModified,
                lastSuccessAt = state?.lastSuccessAt,
                consecutiveFailures = (state?.consecutiveFailures ?: 0) + 1,
            ),
        )
    }

    private fun HomeSelection.query() = when (this) {
        HomeSelection.TodaysBulletin -> publicationDao.observeTodaysBulletin()
        is HomeSelection.Section ->
            if (subsectionCode == null) {
                publicationDao.observeBySection(sectionCode)
            } else {
                publicationDao.observeBySubsection(subsectionCode)
            }
    }

    private fun HomeSelection.sectionName(): String? = when (this) {
        HomeSelection.TodaysBulletin -> null
        is HomeSelection.Section -> sectionRepository.sections()
            .firstOrNull { it.code == code }
            ?.name
    }

    /** Counts only. The feeds carry no personal data and none is derived here. */
    private fun SyncSummary.toEvent() = AnalyticsEvent(
        name = EVENT_SYNC,
        parameters = mapOf(
            "succeeded" to succeededFeeds.toString(),
            "failed" to failedFeeds.toString(),
            "unchanged" to unchangedFeeds.toString(),
            "inserted" to insertedItems.toString(),
            "updated" to updatedItems.toString(),
            "rejected" to rejectedItems.toString(),
        ),
    )

    companion object {
        /** The bulletin publishes once a day; half an hour is polite and still feels live. */
        const val CACHE_TTL_MILLIS: Long = 30 * 60 * 1_000

        /** Never nineteen connections at once against a service with no availability promise. */
        const val MAX_CONCURRENT_FEEDS = 4

        const val EVENT_SYNC = "boc_sync"
    }
}
