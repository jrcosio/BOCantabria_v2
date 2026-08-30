package com.jrblanco.boccantabria.ui.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DetailTab
import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.ShareTarget
import com.jrblanco.boccantabria.domain.repository.ConnectivityRepository
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveOfficialDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedKeysUseCase
import com.jrblanco.boccantabria.domain.usecase.OpenOfficialDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.SetPublicationSavedUseCase
import com.jrblanco.boccantabria.domain.usecase.ShareOfficialDocumentUseCase
import com.jrblanco.boccantabria.fake.FakeDocumentRepository
import com.jrblanco.boccantabria.ui.share.ShareState
import com.jrblanco.boccantabria.fake.FakePublicationRepository
import com.jrblanco.boccantabria.fake.FakeSavedPublicationRepository
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import com.jrblanco.boccantabria.fake.officialDocument
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PublicationDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val analytics = RecordingAnalyticsTracker()
    private val documents = FakeDocumentRepository()
    private var online = true
    private val savedRepository = FakeSavedPublicationRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ---------- Reading the publication ----------

    @Test
    fun `it shows the publication its key points at`() = runTest(dispatcher) {
        val viewModel = viewModel(stored = listOf(publication("boc:1"), publication("boc:2")), key = "boc:2")

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertEquals("boc:2", state.publication?.externalKey)
            assertFalse(state.isMissing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `it resolves the section so the header can name it`() = runTest(dispatcher) {
        val viewModel = viewModel(
            stored = listOf(publication("boc:1", sectionCode = "2", subsectionCode = "2.2")),
            key = "boc:1",
        )

        viewModel.uiState.test {
            advanceUntilIdle()
            assertEquals("Cursos, oposiciones y concursos", expectMostRecentItem().section?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a publication that is no longer stored is explained, not shown blank`() = runTest(dispatcher) {
        val viewModel = viewModel(stored = emptyList(), key = "boc:retirada")

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.isMissing)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the first frame is loading, not missing`() = runTest(dispatcher) {
        // Without this distinction the screen would flash «ya no está» before it had even read.
        val viewModel = viewModel(stored = listOf(publication("boc:1")), key = "boc:1")

        viewModel.uiState.test {
            val first = awaitItem()
            assertTrue(first.isLoading)
            assertFalse(first.isMissing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Tabs ----------

    @Test
    fun `it opens on the document tab`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            assertEquals(DetailTab.DOCUMENT, awaitItem().selectedTab)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the chosen tab is kept`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onTabSelected(DetailTab.AI_SUMMARY)
            advanceUntilIdle()
            assertEquals(DetailTab.AI_SUMMARY, expectMostRecentItem().selectedTab)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Fetching the document ----------

    @Test
    fun `opening the screen does not fetch the document`() = runTest(dispatcher) {
        viewModel()
        advanceUntilIdle()

        // Someone who only wanted to see what the announcement is about should not pay for a PDF.
        assertEquals(0, documents.calls)
    }

    @Test
    fun `showing the document tab fetches it`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onDocumentTabShown()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, documents.calls)
    }

    @Test
    fun `asking twice while one fetch is running does not start a second`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onDocumentTabShown()
            viewModel.onDocumentTabShown()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, documents.calls)
    }

    @Test
    fun `a failed fetch can be retried`() = runTest(dispatcher) {
        documents.result = AppResult.Failure(DomainError.Network)
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onDocumentTabShown()
            advanceUntilIdle()
            documents.emit(DocumentStatus.Failed(DomainError.Network))
            advanceUntilIdle()
            assertEquals(DocumentStatus.Failed(DomainError.Network), expectMostRecentItem().document)

            documents.result = AppResult.Success(officialDocument())
            viewModel.onRetry()
            advanceUntilIdle()
            documents.emit(DocumentStatus.Available(officialDocument()))
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().document is DocumentStatus.Available)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Sharing ----------

    @Test
    fun `sharing goes through preparing and offers the document`() = runTest(dispatcher) {
        // The gate keeps the fetch in flight so «preparing» is a state the screen really shows,
        // rather than one a StateFlow conflates away before anyone can see it.
        val gate = CompletableDeferred<Unit>()
        documents.gate = gate
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onShare()
            advanceUntilIdle()
            assertEquals(ShareState.Preparing, expectMostRecentItem().share)

            gate.complete(Unit)
            advanceUntilIdle()
            val ready = expectMostRecentItem().share as ShareState.Ready
            assertTrue(ready.target is ShareTarget.Document)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the share is consumed once, so a rotation does not open the sheet again`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onShare()
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().share is ShareState.Ready)

            viewModel.onShareConsumed()
            advanceUntilIdle()
            assertEquals(ShareState.Idle, expectMostRecentItem().share)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `without connection and without a copy, sharing offers the link`() = runTest(dispatcher) {
        documents.result = AppResult.Failure(DomainError.Network)
        online = false
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onShare()
            advanceUntilIdle()
            val ready = expectMostRecentItem().share as ShareState.Ready
            assertTrue(ready.target is ShareTarget.Link)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a share that fails outright leaves nothing pending`() = runTest(dispatcher) {
        documents.result = AppResult.Failure(DomainError.Unknown)
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onShare()
            advanceUntilIdle()
            assertEquals(ShareState.Idle, expectMostRecentItem().share)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Telemetry ----------

    @Test
    fun `the screen view is recorded once, and sharing reports only its destination`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onShare()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(PublicationDetailViewModel.SCREEN_NAME), analytics.screenViews)
        val event = analytics.events.single { it.name == PublicationDetailViewModel.EVENT_SHARE }
        assertEquals(mapOf("target" to "document"), event.parameters)
    }

    @Test
    fun `a saved tab that no longer exists falls back to the document`() = runTest(dispatcher) {
        // «Preguntar» was a tab and is a screen now. Someone who left the application on it, and
        // came back after the process died, would otherwise be met by a crash on the one path
        // nobody exercises by hand.
        val viewModel = viewModel(savedTab = "ASK")

        viewModel.uiState.test {
            advanceUntilIdle()
            assertEquals(DetailTab.DOCUMENT, expectMostRecentItem().selectedTab)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Lo guardado (feature 005) ----------

    @Test
    fun `the mark is read from what is stored, not held in the screen`() = runTest(dispatcher) {
        savedRepository.emit(listOf(publication("boc:439765")))
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().isSaved)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a publication nobody saved is not shown as saved`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().isSaved)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling asks for the opposite of what the screen is showing`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onToggleSaved()
            advanceUntilIdle()
            assertEquals(listOf("boc:439765" to true), savedRepository.writes)

            viewModel.onToggleSaved()
            advanceUntilIdle()
            assertEquals("boc:439765" to false, savedRepository.writes.last())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a publication that is no longer stored cannot be saved`() = runTest(dispatcher) {
        // FR-008: sin publicación no hay nada que guardar, y el gesto no puede inventarse una clave.
        val viewModel = viewModel(stored = emptyList())

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onToggleSaved()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(savedRepository.writes.isEmpty())
    }

    @Test
    fun `a failed write is reported and the publication is never shown as saved`() = runTest(dispatcher) {
        savedRepository.failWrites = true
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onToggleSaved()
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.saveFailed)
            // La otra mitad de FR-009 no necesita código: el icono viene de lo almacenado.
            assertFalse(state.isSaved)

            viewModel.onSaveFailureConsumed()
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().saveFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun viewModel(
        stored: List<com.jrblanco.boccantabria.domain.model.Publication> = listOf(publication("boc:439765")),
        key: String = "boc:439765",
        savedTab: String? = null,
    ): PublicationDetailViewModel {
        val publications = FakePublicationRepository(stored)
        val connectivity = object : ConnectivityRepository {
            override fun isOnline(): Boolean = online
        }
        return PublicationDetailViewModel(
            savedStateHandle = SavedStateHandle(
                buildMap {
                    put(PublicationDetailViewModel.ARG_EXTERNAL_KEY, key)
                    savedTab?.let { put(PublicationDetailViewModel.KEY_TAB, it) }
                },
            ),
            observePublication = ObservePublicationUseCase(publications),
            observeDocument = ObserveOfficialDocumentUseCase(documents),
            openDocument = OpenOfficialDocumentUseCase(documents),
            shareDocument = ShareOfficialDocumentUseCase(documents, connectivity),
            observeSavedKeys = ObserveSavedKeysUseCase(savedRepository),
            setPublicationSaved = SetPublicationSavedUseCase(savedRepository),
            getSections = GetBocSectionsUseCase(BocSectionRepositoryImpl()),
            analytics = analytics,
        )
    }
}
