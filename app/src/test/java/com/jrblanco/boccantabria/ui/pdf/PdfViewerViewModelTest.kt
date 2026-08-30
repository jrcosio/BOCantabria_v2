package com.jrblanco.boccantabria.ui.pdf

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.usecase.ObserveOfficialDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationUseCase
import com.jrblanco.boccantabria.domain.usecase.OpenOfficialDocumentUseCase
import com.jrblanco.boccantabria.fake.FakeDocumentRepository
import com.jrblanco.boccantabria.fake.FakePdfDocumentLoader
import com.jrblanco.boccantabria.fake.FakePublicationRepository
import com.jrblanco.boccantabria.fake.officialDocument
import com.jrblanco.boccantabria.fake.publication
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PdfViewerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val documents = FakeDocumentRepository()
    private val loader = FakePdfDocumentLoader()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `it starts loading`() = runTest(dispatcher) {
        viewModel().uiState.test {
            assertEquals(PdfViewerUiState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an available document is ready, titled without repeating the issuer`() = runTest(dispatcher) {
        documents.emit(DocumentStatus.Available(officialDocument()))
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            val ready = expectMostRecentItem() as PdfViewerUiState.Ready
            // The design document asks for an abbreviated title in the viewer's bar, and the
            // issuer is already implied by the document being open.
            assertEquals("Aprobación definitiva de la Ordenanza Fiscal.", ready.title)
            assertEquals(loader.document, ready.pdf)
            assertEquals(officialDocument(), ready.document)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `it fetches on its own rather than trusting the detail screen did`() = runTest(dispatcher) {
        viewModel().uiState.test {
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, documents.calls)
    }

    @Test
    fun `a download in progress is still loading`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            documents.emit(DocumentStatus.Downloading(bytesRead = 10, totalBytes = 100))
            advanceUntilIdle()
            assertEquals(PdfViewerUiState.Loading, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failure is shown as an error, never as an endless wait`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            documents.emit(DocumentStatus.Failed(DomainError.Network))
            advanceUntilIdle()
            assertEquals(PdfViewerUiState.Error(DomainError.Network), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retrying after a failure reaches the document`() = runTest(dispatcher) {
        documents.result = AppResult.Failure(DomainError.Network)
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            documents.emit(DocumentStatus.Failed(DomainError.Network))
            advanceUntilIdle()
            assertTrue(expectMostRecentItem() is PdfViewerUiState.Error)

            documents.result = AppResult.Success(officialDocument())
            viewModel.onRetry()
            advanceUntilIdle()
            documents.emit(DocumentStatus.Available(officialDocument()))
            advanceUntilIdle()
            assertTrue(expectMostRecentItem() is PdfViewerUiState.Ready)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a key with no publication behind it does not fetch anything`() = runTest(dispatcher) {
        viewModel(stored = emptyList()).uiState.test {
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(0, documents.calls)
    }

    @Test
    fun `a file that cannot be opened is an error, not a blank screen`() = runTest(dispatcher) {
        loader.failOnOpen = true
        documents.emit(DocumentStatus.Available(officialDocument()))

        viewModel().uiState.test {
            advanceUntilIdle()
            // Unknown and not Network: the bytes are ours and already verified, so telling the
            // reader to check their connection would send them looking in the wrong place.
            assertEquals(PdfViewerUiState.Error(DomainError.Unknown), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the open document is closed when the model is cleared`() = runTest(dispatcher) {
        documents.emit(DocumentStatus.Available(officialDocument()))
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            assertTrue(expectMostRecentItem() is PdfViewerUiState.Ready)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.callOnCleared()

        // The pages render in another process; a handle left open would keep it alive holding a
        // file the cache may want to evict.
        verify { loader.document.close() }
    }

    @Test
    fun `the document is opened once, however many times the status is re-emitted`() =
        runTest(dispatcher) {
            documents.emit(DocumentStatus.Available(officialDocument()))
            val viewModel = viewModel()

            viewModel.uiState.test {
                advanceUntilIdle()
                documents.emit(DocumentStatus.Available(officialDocument()))
                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, loader.opened.size)
        }

    /** `onCleared` is protected; the screen never calls it, the framework does. */
    private fun PdfViewerViewModel.callOnCleared() {
        PdfViewerViewModel::class.java.superclass
            .getDeclaredMethod("onCleared")
            .apply { isAccessible = true }
            .invoke(this)
    }

    private fun viewModel(
        stored: List<com.jrblanco.boccantabria.domain.model.Publication> = listOf(publication("boc:439765")),
    ): PdfViewerViewModel {
        val publications = FakePublicationRepository(stored)
        return PdfViewerViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(PdfViewerViewModel.ARG_EXTERNAL_KEY to "boc:439765"),
            ),
            observePublication = ObservePublicationUseCase(publications),
            observeDocument = ObserveOfficialDocumentUseCase(documents),
            openDocument = OpenOfficialDocumentUseCase(documents),
            loader = loader,
        )
    }
}
