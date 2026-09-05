package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.fake.FakeAiDocumentUploader
import com.jrblanco.boccantabria.fake.RecordingCrashReporter
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seven invariants of the session store, one test each.
 *
 * This is where the feature's cost lives: without invariant 2 a reader who regenerates a summary
 * pays for the upload twice, and without invariant 3 the service ends up holding every document
 * anybody looked at (010 contracts §1.3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiDocumentSessionStoreTest {

    private val dispatcher = StandardTestDispatcher()
    private val uploader = FakeAiDocumentUploader()
    private val crashReporter = RecordingCrashReporter()

    private fun store() = AiDocumentSessionStore(
        uploader = uploader,
        dispatchers = TestDispatcherProvider(dispatcher),
        crashReporter = crashReporter,
    )

    /** Invariant 2. FR-008 and SC-005: regenerating within a visit costs no second upload. */
    @Test
    fun `the same key and the same checksum uploads nothing the second time`() = runTest(dispatcher) {
        val store = store()

        store.open("boc:1", CHECKSUM, PATH, NAME)
        val second = store.open("boc:1", CHECKSUM, PATH, NAME)

        assertEquals(1, uploader.uploads)
        assertTrue(second is SessionResult.Ready)
    }

    /** Invariant 3. FR-010: at most one document lives at a time. */
    @Test
    fun `opening another publication takes the previous one away first`() = runTest(dispatcher) {
        val store = store()

        store.open("boc:1", CHECKSUM, PATH, NAME)
        store.open("boc:2", OTHER_CHECKSUM, PATH, NAME)

        assertEquals(2, uploader.uploads)
        assertEquals(listOf("files/fake-1"), uploader.deleted)
    }

    /** Invariant 4. The bulletin does correct what it publishes. */
    @Test
    fun `the same key with another checksum is relieved`() = runTest(dispatcher) {
        val store = store()

        store.open("boc:1", CHECKSUM, PATH, NAME)
        store.open("boc:1", OTHER_CHECKSUM, PATH, NAME)

        assertEquals(2, uploader.uploads)
        assertEquals(listOf("files/fake-1"), uploader.deleted)
    }

    /**
     * Invariant 5. Not theoretical: in the next feature the summary and the first question can ask
     * for the document at the same moment, and without the mutex that is two uploads of one file.
     */
    @Test
    fun `two concurrent opens produce one upload`() = runTest(dispatcher) {
        val store = store()
        uploader.gate = CompletableDeferred()

        val first = async { store.open("boc:1", CHECKSUM, PATH, NAME) }
        val second = async { store.open("boc:1", CHECKSUM, PATH, NAME) }
        advanceUntilIdle()
        uploader.gate?.complete(Unit)
        advanceUntilIdle()

        first.await()
        second.await()
        assertEquals(1, uploader.uploads)
    }

    /** Invariant 6. A late `onCleared()` must not take away the publication just opened. */
    @Test
    fun `releasing a key that is not the current one does nothing`() = runTest(dispatcher) {
        val store = store()
        store.open("boc:2", CHECKSUM, PATH, NAME)

        store.release("boc:1")
        advanceUntilIdle()

        assertEquals(emptyList<String>(), uploader.deleted)
    }

    /** FR-009 and SC-006: leaving the publication lets the document go. */
    @Test
    fun `releasing the current key deletes it`() = runTest(dispatcher) {
        val store = store()
        store.open("boc:1", CHECKSUM, PATH, NAME)

        store.release("boc:1")
        advanceUntilIdle()

        assertEquals(listOf("files/fake-1"), uploader.deleted)
    }

    /** And releasing twice does not delete twice: invariant 1 again, from the other side. */
    @Test
    fun `releasing twice deletes once`() = runTest(dispatcher) {
        val store = store()
        store.open("boc:1", CHECKSUM, PATH, NAME)

        store.release("boc:1")
        store.release("boc:1")
        advanceUntilIdle()

        assertEquals(listOf("files/fake-1"), uploader.deleted)
    }

    /** Invariant 7. A half-open session would be reused as if it were ready. */
    @Test
    fun `a rejection leaves no session open`() = runTest(dispatcher) {
        val store = store()
        uploader.rejection = GeminiRefusal.Malformed

        val first = store.open("boc:1", CHECKSUM, PATH, NAME)
        uploader.rejection = null
        store.open("boc:1", CHECKSUM, PATH, NAME)

        assertTrue(first is SessionResult.Rejected)
        assertEquals("el segundo intento sí sube", 2, uploader.uploads)
    }

    /**
     * FR-006. The name the document travels under is built from public data of the publication, and
     * this is the only new thing that leaves the device with a name of our choosing — so it is worth
     * asserting rather than assuming.
     */
    @Test
    fun `the display name carries nothing of the reader`() = runTest(dispatcher) {
        val store = store()

        store.open("boc:439765", CHECKSUM, "/data/user/0/app/cache/documents/3f9a.pdf", "BOC boc:439765")

        val name = uploader.displayNames.single()
        assertTrue(name, name.contains("439765"))
        listOf("/data", "cache", "guardad", "favorit", "android_id", "advertising").forEach {
            assertTrue("«$it» no puede viajar en el nombre: $name", !name.lowercase().contains(it))
        }
    }

    private companion object {
        const val CHECKSUM = "aaaa"
        const val OTHER_CHECKSUM = "bbbb"
        const val PATH = "/tmp/doc.pdf"
        const val NAME = "BOC boc:1"
    }
}
