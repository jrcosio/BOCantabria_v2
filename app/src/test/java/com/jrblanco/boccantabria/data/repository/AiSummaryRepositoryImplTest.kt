package com.jrblanco.boccantabria.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.source.local.AiPreferences
import com.jrblanco.boccantabria.data.source.local.AiSummaryEntity
import com.jrblanco.boccantabria.data.source.local.BocDatabase
import com.jrblanco.boccantabria.data.source.local.PdfExtractionResult
import com.jrblanco.boccantabria.data.source.local.PdfTextNormalizer
import com.jrblanco.boccantabria.data.source.remote.GeminiRefusal
import com.jrblanco.boccantabria.data.source.remote.GeminiSummaryResult
import com.jrblanco.boccantabria.data.source.remote.SummaryPromptFactory
import com.jrblanco.boccantabria.data.source.remote.SummaryValidator
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.domain.model.AiSummaryConstants
import com.jrblanco.boccantabria.domain.model.AiSummaryError
import com.jrblanco.boccantabria.domain.model.AiSummaryStatus
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.fake.FakeDocumentRepository
import com.jrblanco.boccantabria.fake.FakeGeminiSummaryDataSource
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
import org.junit.Assert.assertNull
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
    private val service = FakeGeminiSummaryDataSource()
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
        // Desde la feature 009 llega siempre nula: este servicio no tiene huella de configuración.
        // La columna se conserva porque ya era nullable y porque el próximo proveedor puede tenerla.
        assertNull(stored.systemFingerprint)
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

    /**
     * 009 FR-008, FR-009 and FR-010, and the whole point of D-114.
     *
     * A row written by the previous provider — a different `model_id` — must come back **shown and
     * marked**, not gone. Nothing regenerates it on its own: that would spend a shared allowance on
     * publications nobody asked about.
     */
    @Test
    fun `a summary made by the previous provider is marked stale and kept`() = runTest(dispatcher) {
        database.aiSummaryDao().upsert(rowFromPreviousProvider())

        val repository = repository()
        val status = repository.observeSummary("boc:439765").first()

        assertTrue(status is AiSummaryStatus.Ready)
        assertTrue("debe marcarse como obsoleto", (status as AiSummaryStatus.Ready).isStale)
        assertNotNull("y no debe borrarse", database.aiSummaryDao().byExternalKey("boc:439765"))
        assertEquals("y observar no consulta el servicio", 0, service.calls)
    }

    /**
     * **Regresión, 009 research.md D-111.** `SummaryPayload` es lo que se serializa en la columna
     * `summary_json`, y kotlinx serializa **por nombre de propiedad**. Renombrar la clase es inocuo;
     * renombrar un campo dejaría ilegible todo lo guardado por versiones anteriores, y la pantalla lo
     * trataría como ausente sin decir por qué.
     *
     * El JSON de esta prueba está escrito a mano con los nombres exactos que escribió la versión
     * anterior. Si alguien toca uno, esto se pone rojo.
     */
    @Test
    fun `a summary json written by the previous version is still readable`() = runTest(dispatcher) {
        database.aiSummaryDao().upsert(rowFromPreviousProvider())

        val status = repository().observeSummary("boc:439765").first()

        val summary = (status as AiSummaryStatus.Ready).summary
        assertEquals("Se aprueba la modificacion de la ordenanza.", summary.plainLanguageSummary)
        assertEquals("Aprobacion definitiva", summary.documentTitle)
        assertEquals("Anuncio", summary.documentType)
        assertEquals("Ayuntamiento de Pielagos", summary.issuingBody)
        assertEquals(1, summary.keyPoints.size)
        assertEquals(listOf(1), summary.keyPoints.single().pages)
        assertEquals("15 dias habiles", summary.datesAndDeadlines.single().dateOrPeriod)
        assertEquals("1.000 euros", summary.amounts.single().amount)
        assertEquals("Presentar solicitud", summary.requiredActions.single().action)
        assertEquals(listOf(1), summary.coverage.pagesAnalyzed)
        assertTrue(summary.coverage.complete)
    }

    // ---------- Failures ----------

    @Test
    fun `each refusal becomes the error the screen can explain`() = runTest(dispatcher) {
        assertFailure(GeminiRefusal.NotConfigured, AiSummaryError.NotConfigured)
        assertFailure(GeminiRefusal.Network, AiSummaryError.Offline)
        assertFailure(GeminiRefusal.Malformed, AiSummaryError.InvalidResponse)
        assertFailure(GeminiRefusal.QuotaDay, AiSummaryError.QuotaDay)
        assertFailure(GeminiRefusal.QuotaMinute(42), AiSummaryError.QuotaMinute(42))
        assertFailure(GeminiRefusal.HttpError(500), AiSummaryError.Unknown)
    }

    /** FR-036: an unusable answer is neither shown nor stored. */
    @Test
    fun `an answer that does not survive validation is not stored`() = runTest(dispatcher) {
        service.result = GeminiSummaryResult.Success(
            payload = com.jrblanco.boccantabria.fake.summaryPayload(plainLanguageSummary = "  "),
            usage = com.jrblanco.boccantabria.data.source.remote.GeminiUsage(),
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

    private suspend fun assertFailure(refusal: GeminiRefusal, expected: AiSummaryError) {
        val service = FakeGeminiSummaryDataSource(GeminiSummaryResult.Rejected(refusal))
        val repository = repository(service = service)

        repository.generate(publication("boc:439765"), force = true)

        assertEquals(
            AiSummaryStatus.Failed(expected),
            repository.observeSummary("boc:439765").first(),
        )
    }

    /**
     * Una fila tal y como la escribió la versión anterior: otro modelo, otras versiones de prompt y
     * de esquema, y el `summary_json` con los nombres de campo literales.
     */
    private fun rowFromPreviousProvider() = AiSummaryEntity(
        externalKey = "boc:439765",
        pdfSha256 = officialDocument().checksum,
        modelId = "qwen/qwen3.8-27b",
        promptVersion = "boc-summary-es-v3",
        schemaVersion = "boc-summary-schema-v2",
        summaryJson = PREVIOUS_SUMMARY_JSON,
        createdAtEpochMillis = 1_700_000_000_000L,
        promptTokens = 5_600,
        completionTokens = 1_200,
        totalTokens = 6_800,
        systemFingerprint = "fp_abc",
    )

    private fun repository(service: FakeGeminiSummaryDataSource = this.service) = AiSummaryRepositoryImpl(
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

    private companion object {
        /**
         * Escrito a mano, con los nombres de campo exactos de la versión anterior. **No se genera
         * serializando `SummaryPayload`**: si se hiciera, renombrar un campo cambiaría a la vez el
         * dato y la expectativa, y la prueba no comprobaría nada.
         */
        val PREVIOUS_SUMMARY_JSON = """
            {"documentTitle":"Aprobacion definitiva","documentType":"Anuncio",
             "issuingBody":"Ayuntamiento de Pielagos",
             "plainLanguageSummary":"Se aprueba la modificacion de la ordenanza.",
             "keyPoints":[{"text":"Se aprueba la ordenanza","pages":[1]}],
             "affectedParties":[{"text":"Vecinos del municipio","pages":[1]}],
             "datesAndDeadlines":[{"dateOrPeriod":"15 dias habiles",
                                   "description":"Plazo de alegaciones","pages":[1]}],
             "amounts":[{"amount":"1.000 euros","concept":"Tasa","pages":[1]}],
             "requiredActions":[{"action":"Presentar solicitud",
                                 "deadline":"15 dias habiles","pages":[1]}],
             "appealsOrClaims":[{"text":"Recurso de reposicion","pages":[1]}],
             "warnings":[],
             "coverage":{"pagesAnalyzed":[1],"totalPages":1,"complete":true}}
        """.trimIndent().replace("\n", "")
    }
}
