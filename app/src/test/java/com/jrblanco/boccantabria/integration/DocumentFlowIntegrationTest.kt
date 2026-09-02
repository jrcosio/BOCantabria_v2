package com.jrblanco.boccantabria.integration

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.jrblanco.boccantabria.core.telemetry.NoOpCrashReporter
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.repository.DocumentRepositoryImpl
import com.jrblanco.boccantabria.data.source.local.FileDocumentCache
import com.jrblanco.boccantabria.data.source.remote.OkHttpDocumentDownloader
import com.jrblanco.boccantabria.domain.model.DetailTab
import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.domain.repository.ConnectivityRepository
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.usecase.ObserveOfficialDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationUseCase
import com.jrblanco.boccantabria.domain.usecase.AcceptAiNoticeUseCase
import com.jrblanco.boccantabria.domain.usecase.GenerateAiSummaryUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAiNoticeAcceptedUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAiSummaryUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedKeysUseCase
import com.jrblanco.boccantabria.domain.usecase.OpenOfficialDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.SetPublicationSavedUseCase
import com.jrblanco.boccantabria.domain.usecase.ShareOfficialDocumentUseCase
import com.jrblanco.boccantabria.fake.FakePublicationRepository
import com.jrblanco.boccantabria.fake.FakeAiSummaryRepository
import com.jrblanco.boccantabria.fake.FakeSavedPublicationRepository
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import com.jrblanco.boccantabria.fake.publication
import com.jrblanco.boccantabria.ui.detail.PublicationDetailViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetAddress
import java.security.MessageDigest

/**
 * The whole chain from the screen's state down to the file on disk, with only the network faked.
 *
 * What it proves is the one thing no unit test can: that the bytes the service sent are the bytes
 * the viewer would open. Everything between —validation, hashing, the atomic rename, the status
 * flow, the view model— is the production code.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentFlowIntegrationTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val localhost = InetAddress.getByName("localhost").canonicalHostName
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName(localhost).build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()

        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory())
        server.start()

        client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .retryOnConnectionFailure(false)
            .build()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        runCatching { server.close() }
    }

    @Test
    fun `what reaches the viewer is byte for byte what the service sent`() = runTest(dispatcher) {
        val body = pdfBytes(4_096)
        server.enqueue(pdfResponse(body))
        val publication = publication(documentUrl = server.url("/boces/439765.pdf").toString())
        val viewModel = viewModel(publication)

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onTabSelected(DetailTab.DOCUMENT)
            viewModel.onDocumentTabShown()
            advanceUntilIdle()

            val available = expectMostRecentItem().document as DocumentStatus.Available
            assertArrayEquals(body, java.io.File(available.document.localPath).readBytes())
            assertEquals(sha256(body), available.document.checksum)
            assertEquals(body.size.toLong(), available.document.byteCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opening it a second time does not go back to the service`() = runTest(dispatcher) {
        server.enqueue(pdfResponse(pdfBytes(2_048)))
        val publication = publication(documentUrl = server.url("/boces/439765.pdf").toString())
        val documents = repository()

        val first = OpenOfficialDocumentUseCase(documents)(publication)
        val second = OpenOfficialDocumentUseCase(documents)(publication)

        assertEquals(first, second)
        // One request, not two: the second reader is served from the cache. A second enqueued
        // response was never provided, so a second call would have failed outright.
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `an error page served with a 200 never becomes an official document`() =
        runTest(dispatcher) {
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .setHeader("Content-Type", "text/html")
                    .body("<html><body>Servicio no disponible</body></html>")
                    .build(),
            )
            val publication = publication(documentUrl = server.url("/boces/439765.pdf").toString())
            val documents = repository()

            OpenOfficialDocumentUseCase(documents)(publication)

            // Nothing kept, and nothing half-written either.
            assertEquals(emptyList<String>(), folder.root.walkTopDown().filter { it.isFile }
                .map { it.name }.toList())
        }

    private fun repository() = DocumentRepositoryImpl(
        downloader = OkHttpDocumentDownloader(
            client = client,
            dispatchers = TestDispatcherProvider(),
            allowedHost = server.hostName,
        ),
        cache = FileDocumentCache(
            root = folder.root,
            time = object : TimeProvider { override fun nowMillis() = NOW },
        ),
        dispatchers = TestDispatcherProvider(),
        analytics = RecordingAnalyticsTracker(),
        crashReporter = NoOpCrashReporter(),
    )

    private fun viewModel(publication: com.jrblanco.boccantabria.domain.model.Publication):
        PublicationDetailViewModel {
        val publications = FakePublicationRepository(listOf(publication))
        val documents = repository()
        return PublicationDetailViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(PublicationDetailViewModel.ARG_EXTERNAL_KEY to publication.externalKey),
            ),
            observePublication = ObservePublicationUseCase(publications),
            observeDocument = ObserveOfficialDocumentUseCase(documents),
            openDocument = OpenOfficialDocumentUseCase(documents),
            shareDocument = ShareOfficialDocumentUseCase(
                documents = documents,
                connectivity = object : ConnectivityRepository {
                    override fun isOnline() = true
                },
            ),
            // Lo guardado tiene su propia prueba de integración; aquí solo hace falta que exista.
            observeSavedKeys = ObserveSavedKeysUseCase(FakeSavedPublicationRepository()),
            setPublicationSaved = SetPublicationSavedUseCase(FakeSavedPublicationRepository()),
            // El resumen tiene su propia prueba de integración; aquí solo hace falta que exista.
            observeAiSummary = ObserveAiSummaryUseCase(FakeAiSummaryRepository()),
            generateAiSummary = GenerateAiSummaryUseCase(FakeAiSummaryRepository()),
            observeAiNoticeAccepted = ObserveAiNoticeAcceptedUseCase(FakeAiSummaryRepository()),
            acceptAiNotice = AcceptAiNoticeUseCase(FakeAiSummaryRepository()),
            getSections = GetBocSectionsUseCase(BocSectionRepositoryImpl()),
            analytics = RecordingAnalyticsTracker(),
        )
    }

    private fun pdfResponse(body: ByteArray) = MockResponse.Builder()
        .code(200)
        .setHeader("Content-Type", "application/pdf")
        .body(Buffer().write(body))
        .build()

    private fun pdfBytes(size: Int): ByteArray {
        val header = "%PDF-1.7\n".toByteArray()
        return header + ByteArray(size - header.size) { (it % 251).toByte() }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
