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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The local copies of the official documents.
 *
 * Five promises hold this together, and each has a test of its own:
 *
 * - **Nothing is stored that has not been verified.** The downloader refuses anything that is not a
 *   document, and a refusal never reaches the cache.
 * - **Nothing is left behind.** Every path out — refusal, error, cancellation — clears the
 *   temporary, so an interrupted attempt cannot be mistaken for a document next time.
 * - **One download per document.** Two callers asking at once share one attempt: two downloads
 *   writing the same file is corruption, not just wasted traffic.
 * - **Nothing here throws, and every failure is published.** Since feature 014: a copy that cannot
 *   be read is treated as absent and downloaded again, and an unexpected failure while downloading
 *   or storing is observed as [DocumentStatus.Failed], never left as `Downloading` (audit findings
 *   STAB-001 and STAB-002).
 * - **Cancelling leaves `Absent`, and a waiter outlives its owner's cancellation.** See [acquire]
 *   and [settle].
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

        // Inside the error boundary since feature 014. A copy that cannot be read is a copy that is
        // not there, and downloading it again is what repairs it. Until then this lookup sat before
        // the `try`, and a damaged checksum sidecar threw past six `viewModelScope.launch` without a
        // `try` of their own — closing the application on every reopen (STAB-001; D-601).
        val stored = try {
            cache.get(key)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (unreadable: Throwable) {
            crashReporter.log("document: cache read failed: ${unreadable.javaClass.simpleName}")
            crashReporter.recordNonFatal(unreadable)
            null
        }
        stored?.let {
            if (it.checksum == OfficialDocument.UNKNOWN_CHECKSUM) {
                // The document is fine — its bytes were verified on arrival — but its fingerprint was
                // lost. Recorded so a phone can be diagnosed; never with the key, which can be a URL.
                crashReporter.log("document: checksum sidecar unreadable, served without checksum")
            }
            publish(key, DocumentStatus.Available(it))
            analytics.track(openedEvent(cached = true))
            return@withContext AppResult.Success(it)
        }

        acquire(publication)
    }

    /**
     * Either this call owns the download or it waits for the one already running. Deciding that under
     * the lock is what makes "one download per document" true rather than likely.
     *
     * And a waiter does not inherit the owner's cancellation. The retry gestures of the viewer and the
     * detail cancel and relaunch, and the relaunched call can register as a waiter on a Deferred the
     * old owner is about to cancel: it would die silently — `viewModelScope` swallows cancellations —
     * and the screen would sit on Loading with no retry button. So a waiter whose wait is cancelled
     * asks whether the cancellation is its own; if it is not, it goes round again and becomes the
     * owner (014 research.md D-606).
     */
    private suspend fun acquire(publication: Publication): AppResult<OfficialDocument> {
        val key = publication.externalKey
        while (true) {
            val (owned, pending) = lock.withLock {
                inFlight[key]?.let { return@withLock false to it }
                val fresh = CompletableDeferred<AppResult<OfficialDocument>>()
                inFlight[key] = fresh
                true to fresh
            }
            if (owned) return download(publication, pending)
            try {
                return pending.await()
            } catch (cancellation: CancellationException) {
                currentCoroutineContext().ensureActive()
            }
        }
    }

    /**
     * The owner's path. Every way out — success, refusal, unexpected failure, cancellation — goes
     * through [settle], and every failure the screen can see is published before it is returned: the
     * screens only observe the status, so a result that is returned but not published is a spinner
     * that never stops (audit finding STAB-002; D-604).
     */
    private suspend fun download(
        publication: Publication,
        pending: CompletableDeferred<AppResult<OfficialDocument>>,
    ): AppResult<OfficialDocument> {
        val key = publication.externalKey
        val result = try {
            fetch(publication)
        } catch (cancellation: CancellationException) {
            settle(key, pending, outcome = null, cancellation = cancellation)
            throw cancellation
        } catch (unexpected: Throwable) {
            crashReporter.log("document: fetch threw: ${unexpected.javaClass.simpleName}: ${unexpected.message}")
            crashReporter.recordNonFatal(unexpected)
            publish(key, DocumentStatus.Failed(DomainError.Unknown))
            AppResult.Failure(DomainError.Unknown)
        }
        settle(key, pending, outcome = result, cancellation = null)
        return result
    }

    /**
     * The one way out of an owned download, whatever happened: the temporary is dropped, the entry is
     * removed, the waiters are released — in that order, so a waiter that goes round again finds the
     * entry gone.
     *
     * Under [NonCancellable] because the cancellation path runs in a coroutine that is already
     * cancelled: if `lock.withLock` had to suspend there it would throw, the entry would stay, and
     * every later call for this key would wait on a Deferred nobody completes — for the life of the
     * process. And the cleanup is guarded: a delete that throws must not replace the failure it was
     * cleaning up after, nor leave the waiters hanging (D-604).
     */
    private suspend fun settle(
        key: String,
        pending: CompletableDeferred<AppResult<OfficialDocument>>,
        outcome: AppResult<OfficialDocument>?,
        cancellation: CancellationException?,
    ) = withContext(NonCancellable) {
        if (outcome !is AppResult.Success) {
            runCatching { cache.discardTemporary(key) }
                .onFailure { crashReporter.log("document: cleanup failed: ${it.javaClass.simpleName}") }
        }
        lock.withLock {
            if (inFlight[key] === pending) {
                inFlight.remove(key)
                // No download in flight any more, so `Downloading` would be a lie the next visit
                // paints as a spinner. `Absent`, not `Failed`: cancelling is not a failure. Guarded by
                // the identity check above so a newer owner's status is never overwritten (D-605).
                if (cancellation != null && statuses.value[key] is DocumentStatus.Downloading) {
                    publish(key, DocumentStatus.Absent)
                }
            }
        }
        when {
            cancellation != null -> pending.cancel(cancellation)
            outcome != null -> pending.complete(outcome)
        }
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
