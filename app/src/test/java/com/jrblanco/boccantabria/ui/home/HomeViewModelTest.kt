package com.jrblanco.boccantabria.ui.home

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.HomeSelection
import com.jrblanco.boccantabria.domain.model.SyncSummary
import com.jrblanco.boccantabria.domain.usecase.FilterPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveBulletinHeaderUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedKeysUseCase
import com.jrblanco.boccantabria.domain.repository.ConnectivityRepository
import com.jrblanco.boccantabria.domain.usecase.SetPublicationSavedUseCase
import com.jrblanco.boccantabria.domain.usecase.ShareOfficialDocumentUseCase
import com.jrblanco.boccantabria.fake.FakeDocumentRepository
import com.jrblanco.boccantabria.fake.testSyncCycle
import com.jrblanco.boccantabria.fake.FakePublicationRepository
import com.jrblanco.boccantabria.fake.FakeSavedPublicationRepository
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

    // ---------- The second row: subsections (feature 013) ----------

    @Test
    fun `the day's bulletin offers no subsections`() = runTest(dispatcher) {
        val viewModel = viewModel(FakePublicationRepository())

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.subsections.isEmpty())
            assertFalse(state.isWholeSectionSelected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a section without subsections offers none, but is whole-section selected`() = runTest(dispatcher) {
        val viewModel = viewModel(FakePublicationRepository(), sectionCode = "1")

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.subsections.isEmpty())
            assertTrue(state.isWholeSectionSelected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a section with subsections offers them in official order, none marked`() = runTest(dispatcher) {
        val viewModel = viewModel(FakePublicationRepository(), sectionCode = "2")

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(listOf("2.1", "2.2", "2.3"), state.subsections.map { it.code })
            assertTrue(state.subsections.none { it.isSelected })
            assertTrue(state.isWholeSectionSelected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `only the four sections with subsections offer a second row`() = runTest(dispatcher) {
        // The whole tree in one assertion: five sections offer nothing and four offer 3, 4, 5 and 2.
        // A `value` read is enough here — the list is derived in the constructor, so it is already
        // in the very first state and no emission has to be awaited.
        val counts = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9").associateWith { code ->
            viewModel(FakePublicationRepository(), sectionCode = code).uiState.value.subsections.size
        }

        assertEquals(mapOf("2" to 3, "4" to 4, "7" to 5, "8" to 2), counts.filterValues { it > 0 })
    }

    @Test
    fun `the selected subsection is marked and the whole-section entry is not`() = runTest(dispatcher) {
        val viewModel = viewModel(FakePublicationRepository(), sectionCode = "2", subsectionCode = "2.2")

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(listOf("2.2"), state.subsections.filter { it.isSelected }.map { it.code })
            assertFalse(state.isWholeSectionSelected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `with a subsection selected its parent chip stays marked in the first row`() = runTest(dispatcher) {
        // FR-012. Being in 2.2 is being in 2, and the row above has to keep saying so — otherwise
        // the second row would look like it belonged to nothing.
        val viewModel = viewModel(FakePublicationRepository(), sectionCode = "2", subsectionCode = "2.2")

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(listOf("2"), state.chips.filter { it.isSelected }.map { it.code })
            assertEquals(listOf("2.2"), state.subsections.filter { it.isSelected }.map { it.code })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the subsections carry their short names, not the official ones`() = runTest(dispatcher) {
        // The official names do not fit in a chip; that is what shortName exists for.
        val viewModel = viewModel(FakePublicationRepository(), sectionCode = "8")

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(listOf("Subastas", "Otros judiciales"), state.subsections.map { it.label })
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

    // ---------- Lo guardado (feature 005) ----------

    @Test
    fun `the saved keys reach the state, so a card knows how to draw itself`() = runTest(dispatcher) {
        savedRepository.emit(listOf(publication("boc:1")))
        val viewModel = viewModel(FakePublicationRepository(listOf(publication("boc:1"))))

        viewModel.uiState.test {
            advanceUntilIdle()
            assertEquals(setOf("boc:1"), expectMostRecentItem().savedKeys)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling an unsaved publication asks for it to be saved`() = runTest(dispatcher) {
        val viewModel = viewModel(FakePublicationRepository(listOf(publication("boc:1"))))

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onToggleSaved(publication("boc:1"))
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf("boc:1" to true), savedRepository.writes)
    }

    @Test
    fun `toggling a saved publication asks for it to be taken off`() = runTest(dispatcher) {
        savedRepository.emit(listOf(publication("boc:1")))
        val viewModel = viewModel(FakePublicationRepository(listOf(publication("boc:1"))))

        viewModel.uiState.test {
            advanceUntilIdle()
            // El valor se deduce de lo que el estado muestra: no hay una lectura extra al almacén.
            viewModel.onToggleSaved(publication("boc:1"))
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf("boc:1" to false), savedRepository.writes)
    }

    @Test
    fun `a failed write is reported and the publication is never shown as saved`() = runTest(dispatcher) {
        savedRepository.failWrites = true
        val viewModel = viewModel(FakePublicationRepository(listOf(publication("boc:1"))))

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onToggleSaved(publication("boc:1"))
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.saveFailed)
            // La otra mitad de FR-009 sale gratis: el estado viene de lo almacenado.
            assertTrue(state.savedKeys.isEmpty())

            viewModel.onSaveFailureConsumed()
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().saveFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private val savedRepository = FakeSavedPublicationRepository()

    // ---------- The in-place search ----------

    @Test
    fun `opening the magnifier changes nothing but the bar`() = runTest(dispatcher) {
        val repository = FakePublicationRepository(listOf(publication("boc:1")))
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            advanceUntilIdle()
            val before = expectMostRecentItem()
            viewModel.onSearchOpened()
            advanceUntilIdle()
            val after = expectMostRecentItem()

            assertTrue(after.search.isOpen)
            assertEquals("", after.search.query)
            assertEquals(before.content, after.content)
            // Abrir la lupa no habla con la red: sigue habiendo una única sincronización, la del
            // arranque.
            assertEquals(1, repository.refreshCount + repository.staleChecks - 1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `typing narrows the list to what matches`() = runTest(dispatcher) {
        val repository = FakePublicationRepository(
            listOf(
                publication("boc:1", title = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación."),
                publication("boc:2", title = "AYUNTAMIENTO DE SANTOÑA: Bases.", issuer = "Ayuntamiento de Santoña"),
            ),
        )
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onSearchOpened()
            viewModel.onSearchQueryChanged("pielagos")
            advanceUntilIdle()

            val content = expectMostRecentItem().content
            assertTrue(content is HomeContentState.Publications)
            assertEquals(listOf("boc:1"), (content as HomeContentState.Publications).items.map { it.externalKey })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The whole point of the in-place search: it narrows what is on screen and can never reach
     * outside it. What is on screen is decided by the selection, which the store already applied.
     */
    @Test
    fun `the search never reaches outside the selection on screen`() = runTest(dispatcher) {
        val repository = FakePublicationRepository(
            listOf(publication("boc:1", title = "Contratación de obra en Piélagos")),
        )
        val viewModel = viewModel(repository, sectionCode = "3")

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onSearchOpened()
            viewModel.onSearchQueryChanged("pielagos")
            advanceUntilIdle()

            // Lo que se filtra es exactamente lo que el almacén devolvió para esta selección.
            assertEquals(listOf(HomeSelection.Section("3")), repository.observedSelections)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing the text brings the whole list back`() = runTest(dispatcher) {
        val repository = FakePublicationRepository(
            listOf(publication("boc:1", title = "Piélagos"),
                publication("boc:2", title = "Santoña", issuer = "Ayuntamiento de Santoña")),
        )
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onSearchOpened()
            viewModel.onSearchQueryChanged("pielagos")
            advanceUntilIdle()
            viewModel.onSearchQueryChanged("")
            advanceUntilIdle()

            val content = expectMostRecentItem().content as HomeContentState.Publications
            assertEquals(2, content.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A filter still applied but no longer visible is worse than no filter at all. */
    @Test
    fun `closing the search clears the text, so reopening starts blank`() = runTest(dispatcher) {
        val viewModel = viewModel(FakePublicationRepository(listOf(publication("boc:1"))))

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onSearchOpened()
            viewModel.onSearchQueryChanged("pielagos")
            advanceUntilIdle()
            viewModel.onSearchClosed()
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertFalse(state.search.isOpen)
            assertEquals("", state.search.query)
            assertTrue(state.content is HomeContentState.Publications)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * "Nothing here matches" and "nothing has been published here" say opposite things, and only
     * the first one has a way out to offer.
     */
    @Test
    fun `nothing matching is its own state and not the empty one`() = runTest(dispatcher) {
        val viewModel = viewModel(
            FakePublicationRepository(listOf(publication("boc:1", title = "Piélagos"))),
        )

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onSearchOpened()
            viewModel.onSearchQueryChanged("expropiacion")
            advanceUntilIdle()

            val content = expectMostRecentItem().content
            assertTrue(content is HomeContentState.NoSearchResults)
            assertEquals("expropiacion", (content as HomeContentState.NoSearchResults).query)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The header describes the edition, not the result. Rewriting it would make it untrustworthy. */
    @Test
    fun `the header keeps counting the edition while a search is on`() = runTest(dispatcher) {
        val viewModel = viewModel(
            FakePublicationRepository(
                listOf(publication("boc:1", title = "Piélagos"),
                publication("boc:2", title = "Santoña", issuer = "Ayuntamiento de Santoña")),
            ),
        )

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onSearchOpened()
            viewModel.onSearchQueryChanged("pielagos")
            advanceUntilIdle()

            assertEquals(2, expectMostRecentItem().header.publicationCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The state lives in the view model, which is what a rotation keeps. Rebuilding the flow is the
     * closest a unit test gets to turning the phone.
     */
    @Test
    fun `the typed text and its result survive the screen being rebuilt`() = runTest(dispatcher) {
        val viewModel = viewModel(
            FakePublicationRepository(
                listOf(publication("boc:1", title = "Piélagos"),
                publication("boc:2", title = "Santoña", issuer = "Ayuntamiento de Santoña")),
            ),
        )

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onSearchOpened()
            viewModel.onSearchQueryChanged("pielagos")
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertEquals("pielagos", state.search.query)
            assertTrue(state.search.isOpen)
            assertEquals(1, (state.content as HomeContentState.Publications).items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a blank query is not a filter`() = runTest(dispatcher) {
        val viewModel = viewModel(
            FakePublicationRepository(listOf(publication("boc:1"), publication("boc:2"))),
        )

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onSearchOpened()
            viewModel.onSearchQueryChanged("   ")
            advanceUntilIdle()

            assertEquals(2, (expectMostRecentItem().content as HomeContentState.Publications).items.size)
            cancelAndIgnoreRemainingEvents()
        }
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
            runSyncCycle = testSyncCycle(repository),
            getSections = GetBocSectionsUseCase(BocSectionRepositoryImpl()),
            filterPublications = FilterPublicationsUseCase(),
            observeSavedKeys = ObserveSavedKeysUseCase(savedRepository),
            setPublicationSaved = SetPublicationSavedUseCase(savedRepository),
            shareDocument = ShareOfficialDocumentUseCase(
            // Sharing is exercised in its own tests; here it only has to exist so the
            // bulletin can be built.
            documents = FakeDocumentRepository(),
            connectivity = object : ConnectivityRepository { override fun isOnline() = true },
        ),
            analytics = analytics,
        )
    }
}
