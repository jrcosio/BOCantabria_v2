package com.jrblanco.boccantabria.data.repository

import app.cash.turbine.test
import com.jrblanco.boccantabria.core.telemetry.NoOpCrashReporter
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.source.local.FileDocumentCache
import com.jrblanco.boccantabria.data.source.remote.DownloadRefusal
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.fake.FakeDocumentDownloader
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The rules that decide whether a document can be trusted and whether the cache stays clean.
 *
 * Uses the real cache on a temporary folder: faking it would mean reimplementing the temporary-file
 * dance in the test, and the two copies would drift on exactly the detail that matters.
 */
class DocumentRepositoryImplTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val downloader = FakeDocumentDownloader()
    private val analytics = RecordingAnalyticsTracker()
    private var now: Long = 1_000_000

    private fun repository() = DocumentRepositoryImpl(
        downloader = downloader,
        cache = FileDocumentCache(
            root = folder.root,
            time = object : TimeProvider { override fun nowMillis(): Long = now },
        ),
        dispatchers = TestDispatcherProvider(),
        analytics = analytics,
        crashReporter = NoOpCrashReporter(),
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
    fun `an exploding downloader is a failure, not a crash`() = runTest {
        val exploding = object : com.jrblanco.boccantabria.data.source.remote.DocumentDownloader {
            override suspend fun download(url: String, into: File) = error("boom")
        }
        val repository = DocumentRepositoryImpl(
            downloader = exploding,
            cache = FileDocumentCache(folder.root, object : TimeProvider {
                override fun nowMillis(): Long = now
            }),
            dispatchers = TestDispatcherProvider(),
            analytics = analytics,
            crashReporter = NoOpCrashReporter(),
        )

        assertEquals(AppResult.Failure(DomainError.Unknown), repository.ensureLocalCopy(publication()))
        assertFalse(leftoversExist())
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
}
