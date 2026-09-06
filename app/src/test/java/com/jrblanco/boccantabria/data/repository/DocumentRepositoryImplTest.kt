package com.jrblanco.boccantabria.data.repository

import app.cash.turbine.test
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.source.local.DocumentCache
import com.jrblanco.boccantabria.data.source.local.FileDocumentCache
import com.jrblanco.boccantabria.data.source.remote.DocumentDownloader
import com.jrblanco.boccantabria.data.source.remote.DownloadRefusal
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.OfficialDocument
import com.jrblanco.boccantabria.fake.FakeDocumentDownloader
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import com.jrblanco.boccantabria.fake.RecordingCrashReporter
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.yield
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * The rules that decide whether a document can be trusted and whether the cache stays clean.
 *
 * Uses the real cache on a temporary folder: faking it would mean reimplementing the temporary-file
 * dance in the test, and the two copies would drift on exactly the detail that matters. The failures
 * a phone produces and a folder cannot — a read that throws, a disk that is full, a delete that
 * fails — are injected by [DelegatingCache], which wraps the real one rather than replacing it.
 */
class DocumentRepositoryImplTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val downloader = FakeDocumentDownloader()
    private val analytics = RecordingAnalyticsTracker()
    private val crashReporter = RecordingCrashReporter()
    private var now: Long = 1_000_000

    private fun realCache() = FileDocumentCache(
        root = folder.root,
        time = object : TimeProvider { override fun nowMillis(): Long = now },
    )

    private fun repository(cache: DocumentCache = realCache()) = DocumentRepositoryImpl(
        downloader = downloader,
        cache = cache,
        dispatchers = TestDispatcherProvider(),
        analytics = analytics,
        crashReporter = crashReporter,
    )

    // ---------- Fetching and reusing ----------

    @Test
    fun `a document that is not stored is fetched`() = runTest {
        val result = repository().ensureLocalCopy(publication())

        assertTrue(result is AppResult.Success)
        assertEquals(1, downloader.calls)
        assertTrue(File((result as AppResult.Success).data.localPath).exists())
    }

    @Test
    fun `a document already stored is not fetched again`() = runTest {
        val repository = repository()
        repository.ensureLocalCopy(publication())

        repository.ensureLocalCopy(publication())

        assertEquals(1, downloader.calls)
    }

    @Test
    fun `two callers at once share a single download`() = runTest {
        // The situation this really has to survive: the document tab asks for the preview and the
        // person taps share a moment later. Two downloads writing the same file is corruption.
        val gate = CompletableDeferred<Unit>()
        downloader.gate = gate
        val repository = repository()

        val first = async { repository.ensureLocalCopy(publication()) }
        val second = async { repository.ensureLocalCopy(publication()) }
        gate.complete(Unit)
        val results = listOf(first, second).awaitAll()

        assertEquals(1, downloader.calls)
        assertTrue(results.all { it is AppResult.Success })
    }

    // ---------- Refusals leave nothing ----------

    @Test
    fun `a refused response is a failure and leaves no document`() = runTest {
        downloader.refuse(DownloadRefusal.NotAPdf)

        val result = repository().ensureLocalCopy(publication())

        assertEquals(AppResult.Failure(DomainError.Unknown), result)
        assertFalse(documentsExist())
    }

    @Test
    fun `a network refusal is a network failure`() = runTest {
        downloader.refuse(DownloadRefusal.Network)

        assertEquals(AppResult.Failure(DomainError.Network), repository().ensureLocalCopy(publication()))
    }

    @Test
    fun `an http error is a network failure, not a broken document`() = runTest {
        downloader.refuse(DownloadRefusal.HttpError(500))

        assertEquals(AppResult.Failure(DomainError.Network), repository().ensureLocalCopy(publication()))
    }

    @Test
    fun `no refusal leaves a temporary behind`() = runTest {
        val refusals = listOf(
            DownloadRefusal.NotAPdf,
            DownloadRefusal.UnexpectedType,
            DownloadRefusal.TooLarge,
            DownloadRefusal.HttpError(404),
            DownloadRefusal.Network,
            DownloadRefusal.InsecureScheme,
            DownloadRefusal.UnexpectedHost,
        )

        refusals.forEach { refusal ->
            downloader.refuse(refusal)
            repository().ensureLocalCopy(publication())

            assertFalse("$refusal dejó restos", leftoversExist())
        }
    }

    @Test
    fun `a cancelled download leaves nothing behind, and cancellation is not swallowed`() =
        runTest {
            // The obvious way to reach this: someone opens the Documento tab and immediately goes
            // back. The download dies with the screen's scope, and what must not survive it is a
            // half-written file that a later reader would open as if it were the document.
            downloader.gate = CompletableDeferred()
            val repository = repository()

            val job = async { repository.ensureLocalCopy(publication()) }
            // Let it get as far as the gate, so there really is a download in flight to cancel.
            while (downloader.calls == 0) yield()
            job.cancelAndJoin()

            // Cancelled, not completed with a failure: `CancellationException` is rethrown rather
            // than translated into a `DomainError`, or the caller's scope would never learn that
            // its work stopped.
            assertTrue("la cancelación se tragó", job.isCancelled)
            assertFalse("una descarga cancelada dejó restos", leftoversExist())
        }

    @Test
    fun `an exploding downloader is observed as failed, not left downloading`() = runTest {
        val exploding = object : DocumentDownloader {
            override suspend fun download(url: String, into: File) = error("boom")
        }
        val repository = DocumentRepositoryImpl(
            downloader = exploding,
            cache = realCache(),
            dispatchers = TestDispatcherProvider(),
            analytics = analytics,
            crashReporter = crashReporter,
        )

        repository.observeDocument("boc:439765").test {
            assertEquals(DocumentStatus.Absent, awaitItem())

            assertEquals(AppResult.Failure(DomainError.Unknown), repository.ensureLocalCopy(publication()))

            // Until feature 014 this path returned the failure but never published it. The screens
            // only observe, so they sat on `Downloading` for ever, with no retry button (STAB-002).
            assertEquals(DocumentStatus.Failed(DomainError.Unknown), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(leftoversExist())
        assertTrue(crashReporter.messages.contains("document: fetch threw: IllegalStateException: boom"))
    }

    // ---------- Every path out publishes a terminal state (feature 014, STAB-002) ----------

    @Test
    fun `a cache that cannot store the document fails visibly and can be retried`() = runTest {
        // The audit's reproduction: the download is fine and moving the file into place throws — a
        // full disk. Result `Failure(Unknown)`, status `Downloading(0, null)`, for ever.
        val cache = DelegatingCache(realCache(), failPut = true)
        val repository = repository(cache)

        repository.observeDocument("boc:439765").test {
            assertEquals(DocumentStatus.Absent, awaitItem())

            assertEquals(AppResult.Failure(DomainError.Unknown), repository.ensureLocalCopy(publication()))
            assertEquals(DocumentStatus.Failed(DomainError.Unknown), expectMostRecentItem())
            assertFalse(leftoversExist())

            // The disk has room again: «Reintentar» has to work.
            cache.failPut = false
            assertTrue(repository.ensureLocalCopy(publication()) is AppResult.Success)
            assertTrue(expectMostRecentItem() is DocumentStatus.Available)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, downloader.calls)
    }

    @Test
    fun `cleanup that fails does not hide the failure nor hang the waiters`() = runTest {
        val gate = CompletableDeferred<Unit>()
        downloader.gate = gate
        val cache = DelegatingCache(realCache(), failPut = true, failDiscard = true)
        val repository = repository(cache)

        val owner = async { repository.ensureLocalCopy(publication()) }
        val waiter = async { repository.ensureLocalCopy(publication()) }
        gate.complete(Unit)

        // Both settle with the ORIGINAL failure. A delete that throws must neither replace it nor
        // leave the waiter parked on a Deferred that nobody completes.
        assertEquals(AppResult.Failure(DomainError.Unknown), owner.await())
        assertEquals(AppResult.Failure(DomainError.Unknown), waiter.await())
        assertTrue(crashReporter.messages.any { it.startsWith("document: cleanup failed: IOException") })

        cache.failPut = false
        cache.failDiscard = false
        assertTrue(repository.ensureLocalCopy(publication()) is AppResult.Success)
    }

    @Test
    fun `a cancelled download is observed as absent, not downloading`() = runTest {
        downloader.gate = CompletableDeferred()
        val repository = repository()

        val job = async { repository.ensureLocalCopy(publication()) }
        while (downloader.calls == 0) yield()
        job.cancelAndJoin()

        // `Downloading` means a download in flight. After a cancellation there is none, and a stale
        // `Downloading` made the next visit paint a spinner for work nobody was doing.
        assertEquals(DocumentStatus.Absent, repository.observeDocument("boc:439765").first())
    }

    @Test
    fun `a waiter whose owner is cancelled takes over the download`() = runTest {
        val gate = CompletableDeferred<Unit>()
        downloader.gate = gate
        val repository = repository()

        val owner = async { repository.ensureLocalCopy(publication()) }
        while (downloader.calls == 0) yield()
        val waiter = async { repository.ensureLocalCopy(publication()) }
        yield()
        owner.cancelAndJoin()
        gate.complete(Unit)

        // The waiter is still wanted. Until feature 014 it died with the owner's
        // CancellationException — silently, because viewModelScope swallows those — and the screen
        // behind it sat on Loading with no retry button. The retry gestures of the viewer and the
        // detail cancel and relaunch, which is exactly this race.
        val result: Any = runCatching { waiter.await() }.getOrElse { it }

        assertTrue("el que esperaba debía retomar la descarga, fue: $result", result is AppResult.Success<*>)
        assertEquals(2, downloader.calls)
    }

    // ---------- The copy on disk can be damaged (feature 014, STAB-001) ----------

    @Test
    fun `a stored document whose checksum sidecar was truncated opens without downloading again`() =
        runTest {
            // The audit's exact reproduction: a real entry, its sidecar left empty by an interrupted
            // write, and a second open. Until feature 014 the model's `require` threw out of
            // `ensureLocalCopy` — outside its error boundary — past six `viewModelScope.launch`
            // without a `try`, and the application closed. On every reopen.
            val cache = realCache()
            val repository = repository(cache)
            repository.ensureLocalCopy(publication())
            sidecarFor(cache, "boc:439765").writeText("")

            val result = repository.ensureLocalCopy(publication())

            assertTrue("debía abrirse, fue: $result", result is AppResult.Success)
            assertEquals(1, downloader.calls)
            // The document is fine — its bytes were verified when it arrived — but the incident is
            // recorded, and nothing in the record says which publication it was.
            assertTrue(crashReporter.messages.any { it.startsWith("document: checksum sidecar unreadable") })
            assertTrue(crashReporter.messages.none { it.contains("439765") })
        }

    @Test
    fun `a cache that cannot be read is reported and repaired by downloading again`() = runTest {
        val broken = DelegatingCache(realCache(), failGet = true)

        val result = repository(broken).ensureLocalCopy(publication())

        assertTrue("debía repararse descargando, fue: $result", result is AppResult.Success)
        assertEquals(1, downloader.calls)
        assertEquals(1, crashReporter.nonFatals.size)
        assertTrue(crashReporter.messages.contains("document: cache read failed: IllegalStateException"))
    }

    // ---------- Observing ----------

    @Test
    fun `an unfetched document is observed as absent`() = runTest {
        repository().observeDocument("boc:439765").test {
            assertEquals(DocumentStatus.Absent, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a fetched document is observed as available`() = runTest {
        val repository = repository()
        repository.ensureLocalCopy(publication())

        repository.observeDocument("boc:439765").test {
            assertTrue(awaitItem() is DocumentStatus.Available)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a fetch is observed from absent through downloading to available`() = runTest {
        val gate = CompletableDeferred<Unit>()
        downloader.gate = gate
        val repository = repository()

        repository.observeDocument("boc:439765").test {
            assertEquals(DocumentStatus.Absent, awaitItem())

            val fetch = async { repository.ensureLocalCopy(publication()) }
            assertTrue(awaitItem() is DocumentStatus.Downloading)

            gate.complete(Unit)
            fetch.await()
            assertTrue(awaitItem() is DocumentStatus.Available)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a refusal is observed as failed and can be retried`() = runTest {
        downloader.refuse(DownloadRefusal.Network)
        val repository = repository()

        repository.observeDocument("boc:439765").test {
            assertEquals(DocumentStatus.Absent, awaitItem())

            repository.ensureLocalCopy(publication())
            // Only the settled state is asserted. The status is a StateFlow and it conflates: with
            // nothing suspending the download, the intermediate `Downloading` is superseded before
            // the collector runs. That is the right behaviour for screen state — the screen only
            // ever needs the latest — and the test above uses a gate when it wants to see it.
            assertEquals(DocumentStatus.Failed(DomainError.Network), expectMostRecentItem())

            downloader.result = null
            repository.ensureLocalCopy(publication())
            assertTrue(expectMostRecentItem() is DocumentStatus.Available)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Telemetry ----------

    @Test
    fun `opening reports whether it came from the cache, and nothing else`() = runTest {
        val repository = repository()
        repository.ensureLocalCopy(publication())
        repository.ensureLocalCopy(publication())

        val events = analytics.events.filter { it.name == DocumentRepositoryImpl.EVENT_OPENED }
        assertEquals(listOf("false", "true"), events.map { it.parameters["cached"] })
        // No title, no URL, no key: nothing that identifies what someone is reading.
        assertTrue(events.all { it.parameters.keys == setOf("cached") })
    }

    private fun documentsExist(): Boolean =
        folder.root.resolve("documents").listFiles()?.any { it.name.endsWith(".pdf") } == true

    private fun leftoversExist(): Boolean =
        folder.root.resolve("documents").listFiles()?.any {
            it.name.endsWith(".part") || (it.name.endsWith(".pdf") && it.length() == 0L)
        } == true

    private fun sidecarFor(cache: DocumentCache, key: String): File =
        File(cache.fileFor(key).parentFile, cache.fileFor(key).nameWithoutExtension + ".sha256")

    /**
     * The real cache, with switches for what a phone does and a temporary folder will not: throw on
     * read, throw on store (disk full), throw on delete. Private to this file on purpose — see the
     * class KDoc: the cache under test stays the real one.
     */
    private class DelegatingCache(
        private val real: DocumentCache,
        var failGet: Boolean = false,
        var failPut: Boolean = false,
        var failDiscard: Boolean = false,
    ) : DocumentCache {

        override suspend fun get(externalKey: String): OfficialDocument? {
            check(!failGet) { "cache unreadable" }
            return real.get(externalKey)
        }

        override suspend fun put(
            externalKey: String,
            temporary: File,
            byteCount: Long,
            checksum: String,
        ): OfficialDocument {
            if (failPut) throw IOException("disk full")
            return real.put(externalKey, temporary, byteCount, checksum)
        }

        override suspend fun evict(maxBytes: Long, inUse: Set<String>) = real.evict(maxBytes, inUse)

        override fun fileFor(externalKey: String): File = real.fileFor(externalKey)

        override fun temporaryFor(externalKey: String): File = real.temporaryFor(externalKey)

        override suspend fun discardTemporary(externalKey: String) {
            if (failDiscard) throw IOException("cannot delete")
            real.discardTemporary(externalKey)
        }
    }
}
