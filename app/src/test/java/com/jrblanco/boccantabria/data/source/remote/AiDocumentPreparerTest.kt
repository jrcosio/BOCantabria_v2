package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.data.source.local.PageCountResult
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.fake.FakeAiDocumentUploader
import com.jrblanco.boccantabria.fake.FakeDocumentRepository
import com.jrblanco.boccantabria.fake.FakePdfPageCounter
import com.jrblanco.boccantabria.fake.RecordingCrashReporter
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four steps the summary and the conversation share.
 *
 * The test that matters most is the fourth: **a password-protected document does not reach the
 * uploader**. That invariant used to live inline in the summary's repository, and extracting it here
 * is the whole reason this class exists — a duplicated invariant holds until somebody fixes one of the
 * two copies (011 research.md D-315).
 */
class AiDocumentPreparerTest {

    private val documents = FakeDocumentRepository()
    private val pages = FakePdfPageCounter()
    private val uploader = FakeAiDocumentUploader()
    private val crashReporter = RecordingCrashReporter()

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = TestDispatcherProvider(dispatcher)

    private fun preparer() = AiDocumentPreparer(
        documents = documents,
        pages = pages,
        sessions = AiDocumentSessionStore(uploader, dispatchers, crashReporter),
        crashReporter = crashReporter,
    )

    private val phases = mutableListOf<AiDocumentPreparer.Phase>()

    @Test
    fun `a document that is there comes back with its reference and its page count`() = runTest(dispatcher) {
        pages.result = PageCountResult.Success(totalPages = 54)

        val result = preparer().prepare(publication(), phases::add)

        assertTrue(result is PreparationResult.Ready)
        assertEquals(54, (result as PreparationResult.Ready).totalPages)
        assertEquals("files/fake-1", result.document.remoteName)
    }

    @Test
    fun `the two phases arrive in order, fetching first and uploading second`() = runTest(dispatcher) {
        preparer().prepare(publication(), phases::add)

        assertEquals(
            listOf(
                AiDocumentPreparer.Phase.FETCHING_DOCUMENT,
                AiDocumentPreparer.Phase.UPLOADING_DOCUMENT,
            ),
            phases,
        )
    }

    @Test
    fun `a document that cannot be fetched carries its own error out`() = runTest(dispatcher) {
        documents.result = AppResult.Failure(DomainError.Network)

        val result = preparer().prepare(publication(), phases::add)

        assertEquals(PreparationResult.Unreachable(DomainError.Network), result)
        assertEquals(0, uploader.uploads)
    }

    @Test
    fun `a password-protected document never reaches the uploader`() = runTest(dispatcher) {
        pages.result = PageCountResult.Encrypted

        val result = preparer().prepare(publication(), phases::add)

        assertEquals(PreparationResult.Encrypted, result)
        // The point of the whole class: counting happens before uploading, so nothing left the device.
        assertEquals(0, uploader.uploads)
        assertEquals(listOf(AiDocumentPreparer.Phase.FETCHING_DOCUMENT), phases)
    }

    @Test
    fun `a document that cannot be read on the device comes back broken, and nothing is sent`() =
        runTest(dispatcher) {
            val cause = IllegalStateException("DeadObjectException")
            pages.result = PageCountResult.Failure(cause)

            val result = preparer().prepare(publication(), phases::add)

            assertEquals(cause, (result as PreparationResult.Broken).cause)
            assertEquals(0, uploader.uploads)
        }

    @Test
    fun `a service that will not take the document says so, and leaves no session behind`() =
        runTest(dispatcher) {
            uploader.rejection = GeminiRefusal.Malformed

            val result = preparer().prepare(publication(), phases::add)

            assertEquals(PreparationResult.Refused(GeminiRefusal.Malformed), result)
        }

    @Test
    fun `preparing the same publication twice uploads once`() = runTest(dispatcher) {
        val preparer = preparer()

        preparer.prepare(publication(key = "boc:1"), phases::add)
        preparer.prepare(publication(key = "boc:1"), phases::add)

        assertEquals(1, uploader.uploads)
    }

    @Test
    fun `the name the document travels under is public data of the publication and nothing else`() =
        runTest(dispatcher) {
            preparer().prepare(publication(key = "boc:440124"), phases::add)

            assertEquals(listOf("BOC boc:440124"), uploader.displayNames)
        }
}
