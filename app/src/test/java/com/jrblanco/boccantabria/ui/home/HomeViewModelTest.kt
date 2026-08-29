package com.jrblanco.boccantabria.ui.home

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.HomeSelection
import com.jrblanco.boccantabria.domain.model.SyncSummary
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveBulletinHeaderUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.RefreshPublicationsUseCase
import com.jrblanco.boccantabria.fake.FakePublicationRepository
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import com.jrblanco.boccantabria.fake.publication
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
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val analytics = RecordingAnalyticsTracker()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ---------- Cold start ----------

    @Test
    fun `with nothing stored the placeholders hold until the first synchronisation ends`() = runTest(dispatcher) {
        val repository = FakePublicationRepository()
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            assertEquals(HomeContentState.Skeleton, awaitItem().content)
            advanceUntilIdle()
            // The first sync brought nothing, so it is empty — not still loading, and not an error.
            assertEquals(HomeContentState.Empty, expectMostRecentItem().content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `with content stored it is shown at once, without placeholders`() = runTest(dispatcher) {
        val repository = FakePublicationRepository(listOf(publication("boc:1")))
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            advanceUntilIdle()
            val content = expectMostRecentItem().content
            assertTrue(content is HomeContentState.Publications)
            assertEquals(1, (content as HomeContentState.Publications).items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `content arriving mid synchronisation replaces the placeholders`() = runTest(dispatcher) {
        val repository = FakePublicationRepository()
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            assertEquals(HomeContentState.Skeleton, awaitItem().content)
            repository.emit(listOf(publication("boc:1")))
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().content is HomeContentState.Publications)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Failure and recovery ----------

    @Test
    fun `a failure with nothing stored is an error, and it offers a retry`() = runTest(dispatcher) {
        val repository = FakePublicationRepository().apply {
            refreshResult = AppResult.Failure(DomainError.Network)
        }
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            advanceUntilIdle()
            assertEquals(
                HomeContentState.Error(DomainError.Network),
                expectMostRecentItem().content,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retrying after a failure reaches the content`() = runTest(dispatcher) {
        val repository = FakePublicationRepository().apply {
            refreshResult = AppResult.Failure(DomainError.Network)
        }
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().content is HomeContentState.Error)

            repository.refreshResult = AppResult.Success(SyncSummary(succeededFeeds = 19))
            repository.emit(listOf(publication("boc:1")))
            viewModel.onRetry()
            advanceUntilIdle()

            assertTrue(expectMostRecentItem().content is HomeContentState.Publications)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `every source failing with content stored shows the content and the offline notice`() = runTest(dispatcher) {
        val repository = FakePublicationRepository(listOf(publication("boc:1"))).apply {
            refreshResult = AppResult.Success(SyncSummary(failedFeeds = 19))
        }
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.isOffline)
            assertTrue(state.content is HomeContentState.Publications)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a clean synchronisation clears the offline notice`() = runTest(dispatcher) {
        val repository = FakePublicationRepository(listOf(publication("boc:1")))
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().isOffline)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Refreshing ----------

    @Test
    fun `opening the screen does not force a synchronisation`() = runTest(dispatcher) {
        val repository = FakePublicationRepository().apply { stale = false }
        viewModel(repository)
        advanceUntilIdle()

        assertEquals(1, repository.staleChecks)
        assertEquals(0, repository.refreshCount)
    }

    @Test
    fun `the refresh gesture always synchronises`() = runTest(dispatcher) {
        val repository = FakePublicationRepository().apply { stale = false }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onRefresh()
        advanceUntilIdle()

        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun `two gestures in a row do not start two synchronisations`() = runTest(dispatcher) {
        val repository = FakePublicationRepository()
        val viewModel = viewModel(repository)

        // The initial synchronisation is still in flight because the dispatcher has not run.
        viewModel.onRefresh()
        viewModel.onRefresh()
        advanceUntilIdle()

        assertEquals(1, repository.refreshCount)
    }

    // ---------- Selection ----------

    @Test
    fun `without arguments the selection is the day's bulletin`() = runTest(dispatcher) {
        val repository = FakePublicationRepository()
        viewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf(HomeSelection.TodaysBulletin), repository.observedSelections.distinct())
    }

    @Test
    fun `a section argument reaches the query, so the screen does not filter in memory`() = runTest(dispatcher) {
        val repository = FakePublicationRepository()
        viewModel(repository, sectionCode = "2", subsectionCode = "2.2")
        advanceUntilIdle()

        assertEquals(
            listOf(HomeSelection.Section("2", "2.2")),
            repository.observedSelections.distinct(),
        )
    }

    @Test
    fun `the chip of the selected section is marked, and so is its parent's`() = runTest(dispatcher) {
        val viewModel = viewModel(FakePublicationRepository(), sectionCode = "2", subsectionCode = "2.2")

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(9, state.chips.size)
            assertEquals(listOf("2"), state.chips.filter { it.isSelected }.map { it.code })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the chips carry the nine sections in official order`() = runTest(dispatcher) {
        val viewModel = viewModel(FakePublicationRepository())

        viewModel.uiState.test {
            val chips = awaitItem().chips
            assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9"), chips.map { it.code })
            assertTrue(chips.none { it.isSelected })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Telemetry ----------

    @Test
    fun `the screen view is recorded exactly once per instance`() = runTest(dispatcher) {
        viewModel(FakePublicationRepository())
        advanceUntilIdle()

        assertEquals(listOf(HomeViewModel.SCREEN_NAME), analytics.screenViews)
    }

    private fun viewModel(
        repository: FakePublicationRepository,
        sectionCode: String? = null,
        subsectionCode: String? = null,
    ): HomeViewModel {
        val handle = SavedStateHandle(
            buildMap {
                sectionCode?.let { put(HomeViewModel.ARG_SECTION_CODE, it) }
                subsectionCode?.let { put(HomeViewModel.ARG_SUBSECTION_CODE, it) }
            },
        )
        return HomeViewModel(
            savedStateHandle = handle,
            observePublications = ObservePublicationsUseCase(repository),
            observeHeader = ObserveBulletinHeaderUseCase(repository),
            refreshPublications = RefreshPublicationsUseCase(repository),
            getSections = GetBocSectionsUseCase(BocSectionRepositoryImpl()),
            analytics = analytics,
        )
    }
}
