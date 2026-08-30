package com.jrblanco.boccantabria.ui.saved

import app.cash.turbine.test
import com.jrblanco.boccantabria.domain.repository.ConnectivityRepository
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.SetPublicationSavedUseCase
import com.jrblanco.boccantabria.domain.usecase.ShareOfficialDocumentUseCase
import com.jrblanco.boccantabria.fake.FakeDocumentRepository
import com.jrblanco.boccantabria.fake.FakeSavedPublicationRepository
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import com.jrblanco.boccantabria.fake.publication
import com.jrblanco.boccantabria.ui.share.ShareState
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
class SavedViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val analytics = RecordingAnalyticsTracker()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `with nothing saved the content is empty`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeSavedPublicationRepository())

        viewModel.uiState.test {
            advanceUntilIdle()
            assertEquals(SavedContentState.Empty, expectMostRecentItem().content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `what is saved is shown at once`() = runTest(dispatcher) {
        val repository = FakeSavedPublicationRepository(
            listOf(publication("boc:1"), publication("boc:2")),
        )
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            advanceUntilIdle()
            val content = expectMostRecentItem().content
            assertTrue(content is SavedContentState.Publications)
            assertEquals(2, (content as SavedContentState.Publications).items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** El orden lo pone el almacén. Una pantalla que reordenara sería un segundo sitio decidiéndolo. */
    @Test
    fun `the order arrives untouched, it is not sorted here`() = runTest(dispatcher) {
        val repository = FakeSavedPublicationRepository(
            listOf(publication("boc:3"), publication("boc:1"), publication("boc:2")),
        )
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            advanceUntilIdle()
            val content = expectMostRecentItem().content as SavedContentState.Publications
            assertEquals(listOf("boc:3", "boc:1", "boc:2"), content.items.map { it.externalKey })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unsaving from the list reaches the use case and the item goes away`() = runTest(dispatcher) {
        val repository = FakeSavedPublicationRepository(listOf(publication("boc:1")))
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onToggleSaved(publication("boc:1"))
            advanceUntilIdle()

            // Todo lo que la lista muestra está guardado, así que el gesto solo puede ser quitar.
            assertEquals(listOf("boc:1" to false), repository.writes)
            assertEquals(SavedContentState.Empty, expectMostRecentItem().content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed write is reported and clears once shown`() = runTest(dispatcher) {
        val repository = FakeSavedPublicationRepository(listOf(publication("boc:1")))
        repository.failWrites = true
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onToggleSaved(publication("boc:1"))
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().saveFailed)

            viewModel.onSaveFailureConsumed()
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().saveFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sharing goes through preparing and ends ready`() = runTest(dispatcher) {
        val repository = FakeSavedPublicationRepository(listOf(publication("boc:1")))
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onShare(publication("boc:1"))
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().share is ShareState.Ready)

            viewModel.onShareConsumed()
            advanceUntilIdle()
            assertEquals(ShareState.Idle, expectMostRecentItem().share)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `it reports its screen view`() = runTest(dispatcher) {
        viewModel(FakeSavedPublicationRepository())
        advanceUntilIdle()

        assertEquals(listOf(SavedViewModel.SCREEN_NAME), analytics.screenViews)
    }

    private fun viewModel(repository: FakeSavedPublicationRepository) = SavedViewModel(
        observeSaved = ObserveSavedPublicationsUseCase(repository),
        setPublicationSaved = SetPublicationSavedUseCase(repository),
        shareDocument = ShareOfficialDocumentUseCase(
            // Compartir se ejerce en sus propias pruebas; aquí solo tiene que existir.
            documents = FakeDocumentRepository(),
            connectivity = object : ConnectivityRepository { override fun isOnline() = true },
        ),
        analytics = analytics,
    )
}
