package com.jrblanco.boccantabria.ui.search

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.jrblanco.boccantabria.domain.model.SearchQuery
import com.jrblanco.boccantabria.domain.model.SearchSort
import com.jrblanco.boccantabria.domain.repository.ConnectivityRepository
import com.jrblanco.boccantabria.domain.usecase.GetSearchIssuersUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedKeysUseCase
import com.jrblanco.boccantabria.domain.usecase.SearchPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.SetPublicationSavedUseCase
import com.jrblanco.boccantabria.domain.usecase.ShareOfficialDocumentUseCase
import com.jrblanco.boccantabria.fake.FakeDocumentRepository
import com.jrblanco.boccantabria.fake.FakeSavedPublicationRepository
import com.jrblanco.boccantabria.fake.FakeSearchRepository
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val analytics = RecordingAnalyticsTracker()
    private val searchRepository = FakeSearchRepository()
    private val savedRepository = FakeSavedPublicationRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ---------- Before there is anything to search for ----------

    @Test
    fun `it starts on the initial state, not on an empty result`() = runTest(dispatcher) {
        viewModel().uiState.test {
            advanceUntilIdle()
            assertEquals(SearchContentState.Initial, expectMostRecentItem().content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a single character never reaches the store`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onQueryChanged("a")
            advanceUntilIdle()

            assertEquals(SearchContentState.Initial, expectMostRecentItem().content)
            assertTrue(searchRepository.queries.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Typing is not one search per keystroke. */
    @Test
    fun `typing quickly searches once, for the last thing typed`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onQueryChanged("pi")
            viewModel.onQueryChanged("pie")
            viewModel.onQueryChanged("pielagos")
            advanceUntilIdle()

            assertEquals(1, searchRepository.queries.size)
            assertEquals("pielagos", searchRepository.queries.single().text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Results ----------

    @Test
    fun `what the store returns is what the screen shows`() = runTest(dispatcher) {
        searchRepository.emit(listOf(publication("boc:1"), publication("boc:2")))
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onQueryChanged("pielagos")
            advanceUntilIdle()

            val content = expectMostRecentItem().content
            assertTrue(content is SearchContentState.Results)
            assertEquals(listOf("boc:1", "boc:2"), (content as SearchContentState.Results).items.map { it.externalKey })
            assertFalse(content.isTruncated)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `more results than fit are shown capped and flagged`() = runTest(dispatcher) {
        searchRepository.emit(List(SearchPublicationsUseCase.MAX_RESULTS + 1) { publication("boc:$it") })
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onQueryChanged("de")
            advanceUntilIdle()

            val content = expectMostRecentItem().content as SearchContentState.Results
            assertEquals(SearchPublicationsUseCase.MAX_RESULTS, content.items.size)
            assertTrue(content.isTruncated)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nothing matching is an empty state and never an error`() = runTest(dispatcher) {
        searchRepository.emit(emptyList())
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onQueryChanged("expropiacion")
            advanceUntilIdle()

            assertEquals(SearchContentState.Empty, expectMostRecentItem().content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing the text goes back to the initial state`() = runTest(dispatcher) {
        searchRepository.emit(listOf(publication("boc:1")))
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onQueryChanged("pielagos")
            advanceUntilIdle()
            viewModel.onClearQuery()
            advanceUntilIdle()

            assertEquals(SearchContentState.Initial, expectMostRecentItem().content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Saving from a result ----------

    @Test
    fun `the saved keys reach the state`() = runTest(dispatcher) {
        savedRepository.emit(listOf(publication("boc:1")))
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            assertEquals(setOf("boc:1"), expectMostRecentItem().savedKeys)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `marking one that is not saved asks to save it`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onToggleSaved(publication("boc:1"))
            advanceUntilIdle()

            assertEquals(listOf("boc:1" to true), savedRepository.writes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `marking one that is saved asks to take it off`() = runTest(dispatcher) {
        savedRepository.emit(listOf(publication("boc:1")))
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onToggleSaved(publication("boc:1"))
            advanceUntilIdle()

            assertEquals(listOf("boc:1" to false), savedRepository.writes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a write that failed is said out loud and then cleared`() = runTest(dispatcher) {
        savedRepository.failWrites = true
        val viewModel = viewModel()

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

    // ---------- The hand-off from the bulletin ----------

    /**
     * The term arrives through the same key the typed route uses. Two different keys would break
     * this silently: no error, just a search screen that opened empty.
     */
    @Test
    fun `a term handed over by the route is searched without anybody typing`() = runTest(dispatcher) {
        searchRepository.emit(listOf(publication("boc:1")))
        val viewModel = viewModel(
            savedState = SavedStateHandle(mapOf(SearchViewModel.KEY_QUERY to "expropiacion")),
        )

        viewModel.uiState.test {
            advanceUntilIdle()

            assertEquals("expropiacion", expectMostRecentItem().query.text)
            assertEquals("expropiacion", searchRepository.queries.single().text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Filters and order ----------

    @Test
    fun `applying filters searches again with them`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onQueryChanged("subvenciones")
            advanceUntilIdle()
            viewModel.onFiltersApplied(
                SearchQuery(
                    sectionCode = "6",
                    from = LocalDate.of(2026, 1, 1),
                ),
            )
            advanceUntilIdle()

            val last = searchRepository.queries.last()
            assertEquals("6", last.sectionCode)
            assertEquals(LocalDate.of(2026, 1, 1), last.from)
            assertEquals("subvenciones", last.text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The requirement that breaks most easily. */
    @Test
    fun `clearing the filters keeps what was typed`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onQueryChanged("subvenciones")
            viewModel.onFiltersApplied(
                SearchQuery(sectionCode = "6"),
            )
            advanceUntilIdle()

            viewModel.onClearFilters()
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals("subvenciones", state.query.text)
            assertEquals(0, state.query.activeFilterCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing one filter keeps the rest and the text`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onQueryChanged("subvenciones")
            viewModel.onFiltersApplied(
                SearchQuery(
                    sectionCode = "6",
                    issuer = "Gobierno de Cantabria",
                ),
            )
            advanceUntilIdle()

            viewModel.onRemoveIssuer()
            advanceUntilIdle()

            val query = expectMostRecentItem().query
            assertEquals("subvenciones", query.text)
            assertEquals("6", query.sectionCode)
            assertEquals(null, query.issuer)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changing the order searches again with it`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onQueryChanged("pielagos")
            advanceUntilIdle()
            viewModel.onSortChanged(SearchSort.OLDEST_FIRST)
            advanceUntilIdle()

            assertEquals(SearchSort.OLDEST_FIRST, searchRepository.queries.last().sort)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the issuers the sheet can offer reach the state`() = runTest(dispatcher) {
        searchRepository.emitIssuers(listOf("Ayuntamiento de Piélagos", "Gobierno de Cantabria"))
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            assertEquals(2, expectMostRecentItem().issuers.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Coming back ----------

    @Test
    fun `a model rebuilt from saved state comes back with everything and searches at once`() = runTest(dispatcher) {
        searchRepository.emit(listOf(publication("boc:1")))
        val viewModel = viewModel(
            savedState = SavedStateHandle(
                mapOf(
                    SearchViewModel.KEY_QUERY to "subvenciones",
                    SearchViewModel.KEY_SECTION to "6",
                    SearchViewModel.KEY_FROM to "2026-01-01",
                    SearchViewModel.KEY_SORT to SearchSort.OLDEST_FIRST.name,
                ),
            ),
        )

        viewModel.uiState.test {
            advanceUntilIdle()

            val query = expectMostRecentItem().query
            assertEquals("subvenciones", query.text)
            assertEquals("6", query.sectionCode)
            assertEquals(LocalDate.of(2026, 1, 1), query.from)
            assertEquals(SearchSort.OLDEST_FIRST, query.sort)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `what is typed and filtered is written into the saved state`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val viewModel = viewModel(savedState = handle)

        viewModel.uiState.test {
            viewModel.onQueryChanged("subvenciones")
            viewModel.onSortChanged(SearchSort.OLDEST_FIRST)
            advanceUntilIdle()

            assertEquals("subvenciones", handle.get<String>(SearchViewModel.KEY_QUERY))
            assertEquals(SearchSort.OLDEST_FIRST.name, handle.get<String>(SearchViewModel.KEY_SORT))
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A saved name this version no longer knows must not bring the screen down. */
    @Test
    fun `an order this version does not know falls back to the default`() = runTest(dispatcher) {
        val viewModel = viewModel(
            savedState = SavedStateHandle(mapOf(SearchViewModel.KEY_SORT to "RELEVANCE")),
        )

        viewModel.uiState.test {
            advanceUntilIdle()
            assertEquals(SearchSort.DEFAULT, expectMostRecentItem().query.sort)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Telemetry ----------

    @Test
    fun `the screen reports itself once`() = runTest(dispatcher) {
        viewModel()
        advanceUntilIdle()

        assertEquals(listOf(SearchViewModel.SCREEN_NAME), analytics.screenViews)
    }

    /**
     * The constitution forbids logging personal data, and a query typed by hand can carry it.
     * What is reported is whether there were filters and a bucket of the result count.
     */
    @Test
    fun `the query text never reaches telemetry`() = runTest(dispatcher) {
        searchRepository.emit(listOf(publication("boc:1")))
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onQueryChanged("maria fernandez calle mayor 3")
            advanceUntilIdle()

            val events = analytics.events.filter { it.name == SearchViewModel.EVENT_SEARCH }
            assertTrue(events.isNotEmpty())
            assertTrue(events.none { event -> event.parameters.values.any { it.contains("maria") } })
            assertEquals("1-9", events.last().parameters["results"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) = SearchViewModel(
        savedStateHandle = savedState,
        searchPublications = SearchPublicationsUseCase(searchRepository),
        getSearchIssuers = GetSearchIssuersUseCase(searchRepository),
        observeSavedKeys = ObserveSavedKeysUseCase(savedRepository),
        setPublicationSaved = SetPublicationSavedUseCase(savedRepository),
        shareDocument = ShareOfficialDocumentUseCase(
            // Compartir se ejerce en sus propias pruebas; aquí solo tiene que existir.
            documents = FakeDocumentRepository(),
            connectivity = object : ConnectivityRepository { override fun isOnline() = true },
        ),
        analytics = analytics,
    )
}
