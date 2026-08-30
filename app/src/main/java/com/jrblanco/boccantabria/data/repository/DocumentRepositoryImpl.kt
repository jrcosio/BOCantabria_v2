package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.core.telemetry.AnalyticsEvent
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.data.source.local.DocumentCache
import com.jrblanco.boccantabria.data.source.remote.DocumentDownloader
import com.jrblanco.boccantabria.data.source.remote.DownloadRefusal
import com.jrblanco.boccantabria.data.source.remote.DownloadResult
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.OfficialDocument
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.DocumentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The local copies of the official documents.
 *
 * Three promises hold this together, and each has a test of its own:
 *
 * - **Nothing is stored that has not been verified.** The downloader refuses anything that is not a
 *   document, and a refusal never reaches the cache.
 * - **Nothing is left behind.** Every path out — refusal, error, cancellation — clears the
 *   temporary, so an interrupted attempt cannot be mistaken for a document next time.
 * - **One download per document.** Two callers asking at once share one attempt: two downloads
 *   writing the same file is corruption, not just wasted traffic.
 */
class DocumentRepositoryImpl(
    private val downloader: DocumentDownloader,
    private val cache: DocumentCache,
    private val dispatchers: DispatcherProvider,
    private val analytics: AnalyticsTracker,
    private val crashReporter: CrashReporter,
) : DocumentRepository {

    private val statuses = MutableStateFlow<Map<String, DocumentStatus>>(emptyMap())
    private val inFlight = mutableMapOf<String, CompletableDeferred<AppResult<OfficialDocument>>>()
    private val lock = Mutex()

    override fun observeDocument(externalKey: String): Flow<DocumentStatus> =
        statuses.map { it[externalKey] ?: DocumentStatus.Absent }

    override suspend fun ensureLocalCopy(
        publication: Publication,
    ): AppResult<OfficialDocument> = withContext(dispatchers.io) {
        val key = publication.externalKey

        cache.get(key)?.let { stored ->
            publish(key, DocumentStatus.Available(stored))
            analytics.track(openedEvent(cached = true))
            return@withContext AppResult.Success(stored)
        }

        // Either this call owns the download or it waits for the one already running. Deciding that
        // under the lock is what makes "one download per document" true rather than likely.
        val (owned, pending) = lock.withLock {
            inFlight[key]?.let { return@withLock false to it }
            val fresh = CompletableDeferred<AppResult<OfficialDocument>>()
            inFlight[key] = fresh
            true to fresh
        }
        if (!owned) return@withContext pending.await()

        val result = try {
            fetch(publication)
        } catch (cancellation: CancellationException) {
            cache.discardTemporary(key)
            lock.withLock { inFlight.remove(key) }
            pending.cancel(cancellation)
            throw cancellation
        } catch (unexpected: Throwable) {
            crashReporter.recordNonFatal(unexpected)
            cache.discardTemporary(key)
            AppResult.Failure(DomainError.Unknown)
        }

        lock.withLock { inFlight.remove(key) }
        pending.complete(result)
        result
    }

    private suspend fun fetch(publication: Publication): AppResult<OfficialDocument> {
        val key = publication.externalKey
        publish(key, DocumentStatus.Downloading(bytesRead = 0, totalBytes = null))

        val temporary = cache.temporaryFor(key).apply { parentFile?.mkdirs() }
        return when (val outcome = downloader.download(publication.documentUrl, temporary)) {
            is DownloadResult.Downloaded -> {
                val stored = cache.put(key, temporary, outcome.byteCount, outcome.checksum)
                publish(key, DocumentStatus.Available(stored))
                analytics.track(openedEvent(cached = false))
                AppResult.Success(stored)
            }

            is DownloadResult.Rejected -> {
                cache.discardTemporary(key)
                val error = outcome.reason.toDomainError()
                publish(key, DocumentStatus.Failed(error))
                AppResult.Failure(error)
            }
        }
    }

    override suspend fun releaseUnused() {
        withContext(dispatchers.io) {
            val inUse = statuses.value
                .filterValues { it is DocumentStatus.Available }
                .keys
            runCatching { cache.evict(MAX_CACHE_BYTES, inUse) }
                .onFailure { crashReporter.recordNonFatal(it) }
        }
    }

    private fun publish(externalKey: String, status: DocumentStatus) {
        statuses.value = statuses.value + (externalKey to status)
    }

    /**
     * A refusal that could clear up on its own is a network problem; one about the content itself
     * is not. The screen says something different for each, and nothing else depends on the
     * distinction — which is why `DomainError` did not need a third case.
     */
    private fun DownloadRefusal.toDomainError(): DomainError = when (this) {
        DownloadRefusal.Network, is DownloadRefusal.HttpError -> DomainError.Network
        DownloadRefusal.NotAPdf,
        DownloadRefusal.UnexpectedType,
        DownloadRefusal.TooLarge,
        DownloadRefusal.InsecureScheme,
        DownloadRefusal.UnexpectedHost,
        -> DomainError.Unknown
    }

    /** A single flag. Nothing that says which announcement anyone is reading. */
    private fun openedEvent(cached: Boolean) =
        AnalyticsEvent(name = EVENT_OPENED, parameters = mapOf("cached" to cached.toString()))

    companion object {
        const val EVENT_OPENED = "document_opened"

        /** Cache budget. Generous for announcements, small enough not to squat on the device. */
        const val MAX_CACHE_BYTES = 100L * 1024 * 1024
    }
}
