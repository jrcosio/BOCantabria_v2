package com.jrblanco.boccantabria.integration

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jrblanco.boccantabria.core.telemetry.NoOpAnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.NoOpCrashReporter
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.repository.AiSummaryRepositoryImpl
import com.jrblanco.boccantabria.data.source.local.AiPreferences
import com.jrblanco.boccantabria.data.source.local.BocDatabase
import com.jrblanco.boccantabria.data.source.local.PdfTextNormalizer
import com.jrblanco.boccantabria.data.source.remote.SummaryPromptFactory
import com.jrblanco.boccantabria.data.source.remote.SummaryValidator
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.domain.model.AiSummaryStatus
import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.domain.repository.AiSummaryRepository
import com.jrblanco.boccantabria.domain.usecase.GenerateAiSummaryUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAiSummaryUseCase
import com.jrblanco.boccantabria.fake.FakeDocumentRepository
import com.jrblanco.boccantabria.fake.FakeGeminiSummaryDataSource
import com.jrblanco.boccantabria.fake.FakePdfTextExtractor
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import com.jrblanco.boccantabria.fake.officialDocument
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The whole chain, with doubles only at the outer boundary: a real database, real use cases, a real
 * validator and a real normaliser. What is faked is what would reach the network or a PDF library.
 *
 * The point of this one is FR-033 and SC-002: the **second** opening costs nothing. With a shared
 * daily allowance, regenerating what has already been generated would empty it in an afternoon.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class AiSummaryFlowIntegrationTest {

    private val dispatcher = StandardTestDispatcher()
    private val documents = FakeDocumentRepository(DocumentStatus.Available(officialDocument()))
    private val extractor = FakePdfTextExtractor()
    private val service = FakeGeminiSummaryDataSource()

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

    @Test
    fun `generating once is enough forever`() = runTest(dispatcher) {
        val first = repository()
        GenerateAiSummaryUseCase(first)(publication("boc:439765"))
        advanceUntilIdle()

        assertEquals(1, service.calls)

        // A second repository over the same database: what a fresh launch of the application sees.
        val second = repository()
        val status = ObserveAiSummaryUseCase(second)("boc:439765").first()
        advanceUntilIdle()

        assertTrue("el resumen guardado debe salir tal cual", status is AiSummaryStatus.Ready)
        assertEquals(
            "Se aprueba definitivamente la modificacion de la ordenanza.",
            (status as AiSummaryStatus.Ready).summary.plainLanguageSummary,
        )
        assertEquals("y no puede haber costado otra petición", 1, service.calls)
    }

    @Test
    fun `the stored summary survives without the document being available`() = runTest(dispatcher) {
        GenerateAiSummaryUseCase(repository())(publication("boc:439765"))
        advanceUntilIdle()

        // The cache evicted the PDF, which it is allowed to do: it is a cache, not a library.
        documents.emit(DocumentStatus.Absent)
        advanceUntilIdle()

        val status = ObserveAiSummaryUseCase(repository())("boc:439765").first()

        assertTrue(status is AiSummaryStatus.Ready)
        assertEquals(1, service.calls)
    }

    /** What the summary shows is the **corrected** answer, not what the service originally claimed. */
    @Test
    fun `what is stored is the corrected coverage, not the one the service claimed`() = runTest(dispatcher) {
        service.result = com.jrblanco.boccantabria.data.source.remote.GeminiSummaryResult.Success(
            payload = com.jrblanco.boccantabria.fake.summaryPayload(
                coverage = com.jrblanco.boccantabria.data.source.remote.CoverageDto(
                    pagesAnalyzed = listOf(1, 2, 3, 4, 5),
                    totalPages = 5,
                    complete = true,
                ),
            ),
            usage = com.jrblanco.boccantabria.data.source.remote.GeminiUsage(),
            systemFingerprint = null,
        )

        GenerateAiSummaryUseCase(repository())(publication("boc:439765"))
        advanceUntilIdle()

        val status = ObserveAiSummaryUseCase(repository())("boc:439765").first()
        val coverage = (status as AiSummaryStatus.Ready).summary.coverage

        // The corpus the extractor produced has one page, and one page is what went out.
        assertEquals(listOf(1), coverage.pagesAnalyzed)
        assertEquals(1, coverage.totalPages)
        assertTrue(coverage.complete)
    }

    private fun repository(): AiSummaryRepository = AiSummaryRepositoryImpl(
        documents = documents,
        extractor = extractor,
        normalizer = PdfTextNormalizer(),
        prompts = SummaryPromptFactory(),
        summaries = service,
        validator = SummaryValidator(),
        dao = database.aiSummaryDao(),
        preferences = FakePreferences(),
        time = FixedClock,
        dispatchers = TestDispatcherProvider(dispatcher),
        analytics = NoOpAnalyticsTracker(),
        crashReporter = NoOpCrashReporter(),
    )

    private object FixedClock : TimeProvider {
        override fun nowMillis(): Long = 1_700_000_000_000L
    }

    private class FakePreferences : AiPreferences {
        private val accepted = MutableStateFlow(true)
        override fun observeNoticeAccepted() = accepted
        override suspend fun acceptNotice() { accepted.value = true }
    }
}
