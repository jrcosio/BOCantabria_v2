package com.jrblanco.boccantabria.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.source.local.AiPreferences
import com.jrblanco.boccantabria.data.source.local.BocDatabase
import com.jrblanco.boccantabria.data.source.local.PdfExtractionResult
import com.jrblanco.boccantabria.data.source.local.PdfTextNormalizer
import com.jrblanco.boccantabria.data.source.remote.GroqRefusal
import com.jrblanco.boccantabria.data.source.remote.GroqSummaryResult
import com.jrblanco.boccantabria.data.source.remote.SummaryPromptFactory
import com.jrblanco.boccantabria.data.source.remote.SummaryValidator
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.domain.model.AiSummaryConstants
import com.jrblanco.boccantabria.domain.model.AiSummaryError
import com.jrblanco.boccantabria.domain.model.AiSummaryStatus
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.fake.FakeDocumentRepository
import com.jrblanco.boccantabria.fake.FakeGroqSummaryDataSource
import com.jrblanco.boccantabria.fake.FakePdfTextExtractor
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import com.jrblanco.boccantabria.fake.RecordingCrashReporter
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import com.jrblanco.boccantabria.fake.officialDocument
import com.jrblanco.boccantabria.fake.publication
import com.jrblanco.boccantabria.fake.scannedCorpus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The orchestrator. Several of these assert on requests that must **not** happen, which is the half
 * of this feature a free allowance depends on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class AiSummaryRepositoryImplTest {

    private val dispatcher = StandardTestDispatcher()
    private val documents = FakeDocumentRepository(DocumentStatus.Available(officialDocument()))
    private val extractor = FakePdfTextExtractor()
    private val service = FakeGroqSummaryDataSource()
    private val analytics = RecordingAnalyticsTracker()
    private val crashReporter = RecordingCrashReporter()
    private val preferences = FakePreferences()

    private lateinit var database: BocDatabase

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BocDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ---------- Nothing happens on its own ----------

    /** FR-002 and SC-004: watching the tab costs nothing. */
    @Test
    fun `observing never reaches the service`() = runTest(dispatcher) {
        val repository = repository()

        assertEquals(AiSummaryStatus.Idle, repository.observeSummary("boc:439765").first())
        advanceUntilIdle()

        assertEquals(0, service.calls)
        assertEquals(0, documents.calls)
    }

    // ---------- The happy path ----------

    @Test
    fun `generating produces a summary and stores it`() = runTest(dispatcher) {
        val repository = repository()

        val result = repository.generate(publication("boc:439765"), force = false)
        advanceUntilIdle()

        assertTrue(result is AppResult.Success)
        assertEquals(1, service.calls)
        assertNotNull(database.aiSummaryDao().byExternalKey("boc:439765"))
    }

    @Test
    fun `what is stored carries the provenance and the real cost`() = runTest(dispatcher) {
        repository().generate(publication("boc:439765"), force = false)
        advanceUntilIdle()

        val stored = database.aiSummaryDao().byExternalKey("boc:439765")!!

        // Contra las constantes, no contra un número escrito a mano: lo que esta prueba fija es que
        // la procedencia se guarda, no en qué versión vamos. Subirla es una operación legítima —marca
        // lo guardado como obsoleto— y no debería poner roja una prueba que va de otra cosa.
        assertEquals(AiSummaryConstants.MODEL_ID, stored.modelId)
        assertEquals(AiSummaryConstants.PROMPT_VERSION, stored.promptVersion)
        assertEquals(AiSummaryConstants.SCHEMA_VERSION, stored.schemaVersion)
        assertEquals(officialDocument().checksum, stored.pdfSha256)
        assertEquals(6_800, stored.totalTokens)
        assertEquals("fp_abc", stored.systemFingerprint)
    }

    // ---------- What must never cost a request ----------

    /** FR-012 and SC-005. Asking about an empty context spends quota and gets invention back. */
    @Test
    fun `a document without usable text never reaches the service`() = runTest(dispatcher) {
        extractor.result = PdfExtractionResult.NoExtractableText
        val repository = repository()

        val result = repository.generate(publication("boc:439765"), force = false)
        advanceUntilIdle()

        assertTrue(result is AppResult.Failure)
        assertEquals(0, service.calls)
        assertEquals(
            AiSummaryStatus.Failed(AiSummaryError.NoExtractableText),
            repository.observeSummary("boc:439765").first(),
        )
    }

    @Test
    fun `a protected document never reaches the service either`() = runTest(dispatcher) {
        extractor.result = PdfExtractionResult.EncryptedPdf
        val repository = repository()

        repository.generate(publication("boc:439765"), force = false)
        advanceUntilIdle()

        assertEquals(0, service.calls)
        assertEquals(
            AiSummaryStatus.Failed(AiSummaryError.EncryptedPdf),
            repository.observeSummary("boc:439765").first(),
        )
    }

    /** FR-005: two taps are one request, not two charges against the allowance. */
    @Test
    fun `two concurrent generations share a single request`() = runTest(dispatcher) {
        service.gate = kotlinx.coroutines.CompletableDeferred()
        val repository = repository()

        val first = async { repository.generate(publication("boc:439765"), force = false) }
        val second = async { repository.generate(publication("boc:439765"), force = false) }
        advanceUntilIdle()
        service.gate!!.complete(Unit)
        first.await()
        second.await()
        advanceUntilIdle()

        assertEquals(1, service.calls)
    }

    /** FR-033 and SC-002: the second opening is free. */
    @Test
    fun `a stored summary is returned without asking again`() = runTest(dispatcher) {
        val repository = repository()
        repository.generate(publication("boc:439765"), force = false)
        advanceUntilIdle()

        repository.generate(publication("boc:439765"), force = false)
        advanceUntilIdle()

        assertEquals(1, service.calls)
    }

    @Test
    fun `regenerating asks again on purpose`() = runTest(dispatcher) {
        val repository = repository()
        repository.generate(publication("boc:439765"), force = false)
        advanceUntilIdle()

        repository.generate(publication("boc:439765"), force = true)
        advanceUntilIdle()

        assertEquals(2, service.calls)
    }

    // ---------- Stale, not absent ----------

    /** FR-035: the summary is kept and shown, marked. It is never discarded here. */
    @Test
    fun `a summary made from a different document is marked stale and kept`() = runTest(dispatcher) {
        val repository = repository()
        repository.generate(publication("boc:439765"), force = false)
        advanceUntilIdle()

        documents.emit(DocumentStatus.Available(officialDocument(checksum = "b".repeat(64))))
        advanceUntilIdle()

        val status = repository.observeSummary("boc:439765").first()
        assertTrue(status is AiSummaryStatus.Ready)
        assertTrue("debe marcarse como obsoleto", (status as AiSummaryStatus.Ready).isStale)
        assertNotNull("y no debe borrarse", database.aiSummaryDao().byExternalKey("boc:439765"))
    }

    @Test
    fun `a summary made from the same document is not stale`() = runTest(dispatcher) {
        val repository = repository()
        repository.generate(publication("boc:439765"), force = false)
        advanceUntilIdle()

        val status = repository.observeSummary("boc:439765").first()

        assertTrue(status is AiSummaryStatus.Ready)
        assertTrue(!(status as AiSummaryStatus.Ready).isStale)
    }

    // ---------- Failures ----------

    @Test
    fun `each refusal becomes the error the screen can explain`() = runTest(dispatcher) {
        assertFailure(GroqRefusal.NotConfigured, AiSummaryError.NotConfigured)
        assertFailure(GroqRefusal.Network, AiSummaryError.Offline)
        assertFailure(GroqRefusal.Malformed, AiSummaryError.InvalidResponse)
        assertFailure(GroqRefusal.QuotaDay, AiSummaryError.QuotaDay)
        assertFailure(GroqRefusal.QuotaMinute(42), AiSummaryError.QuotaMinute(42))
        assertFailure(GroqRefusal.HttpError(500), AiSummaryError.Unknown)
    }

    /** FR-036: an unusable answer is neither shown nor stored. */
    @Test
    fun `an answer that does not survive validation is not stored`() = runTest(dispatcher) {
        service.result = GroqSummaryResult.Success(
            payload = com.jrblanco.boccantabria.fake.summaryPayload(plainLanguageSummary = "  "),
            usage = com.jrblanco.boccantabria.data.source.remote.GroqUsage(),
            systemFingerprint = null,
        )
        val repository = repository()

        repository.generate(publication("boc:439765"), force = false)
        advanceUntilIdle()

        assertEquals(
            AiSummaryStatus.Failed(AiSummaryError.InvalidResponse),
            repository.observeSummary("boc:439765").first(),
        )
        assertEquals(null, database.aiSummaryDao().byExternalKey("boc:439765"))
    }

    @Test
    fun `a document that cannot be fetched is reported as offline`() = runTest(dispatcher) {
        documents.result = AppResult.Failure(
            com.jrblanco.boccantabria.domain.model.DomainError.Network,
        )
        val repository = repository()

        repository.generate(publication("boc:439765"), force = false)
        advanceUntilIdle()

        assertEquals(0, service.calls)
        assertEquals(
            AiSummaryStatus.Failed(AiSummaryError.Offline),
            repository.observeSummary("boc:439765").first(),
        )
    }

    // ---------- The notice ----------

    @Test
    fun `the notice is remembered through the repository`() = runTest(dispatcher) {
        val repository = repository()

        assertEquals(false, repository.observeNoticeAccepted().first())
        repository.acceptNotice()

        assertEquals(true, repository.observeNoticeAccepted().first())
    }

    private suspend fun assertFailure(refusal: GroqRefusal, expected: AiSummaryError) {
        val service = FakeGroqSummaryDataSource(GroqSummaryResult.Rejected(refusal))
        val repository = repository(service = service)

        repository.generate(publication("boc:439765"), force = true)

        assertEquals(
            AiSummaryStatus.Failed(expected),
            repository.observeSummary("boc:439765").first(),
        )
    }

    private fun repository(service: FakeGroqSummaryDataSource = this.service) = AiSummaryRepositoryImpl(
        documents = documents,
        extractor = extractor,
        normalizer = PdfTextNormalizer(),
        prompts = SummaryPromptFactory(),
        summaries = service,
        validator = SummaryValidator(),
        dao = database.aiSummaryDao(),
        preferences = preferences,
        time = FixedClock,
        dispatchers = TestDispatcherProvider(dispatcher),
        analytics = analytics,
        crashReporter = crashReporter,
    )

    private object FixedClock : TimeProvider {
        override fun nowMillis(): Long = 1_700_000_000_000L
    }

    private class FakePreferences : AiPreferences {
        private val accepted = MutableStateFlow(false)
        override fun observeNoticeAccepted() = accepted
        override suspend fun acceptNotice() { accepted.value = true }
    }
}
