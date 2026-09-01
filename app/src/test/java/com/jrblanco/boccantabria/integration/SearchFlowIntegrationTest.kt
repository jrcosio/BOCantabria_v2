package com.jrblanco.boccantabria.integration

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.jrblanco.boccantabria.core.telemetry.NoOpCrashReporter
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.data.repository.PublicationRepositoryImpl
import com.jrblanco.boccantabria.data.repository.SavedPublicationRepositoryImpl
import com.jrblanco.boccantabria.data.repository.SearchRepositoryImpl
import com.jrblanco.boccantabria.data.source.local.BocDatabase
import com.jrblanco.boccantabria.data.source.remote.BocFeedCatalog
import com.jrblanco.boccantabria.data.source.remote.PublicationNormalizer
import com.jrblanco.boccantabria.data.source.remote.RssItemDto
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.domain.model.SearchQuery
import com.jrblanco.boccantabria.domain.model.SearchSort
import com.jrblanco.boccantabria.domain.repository.ConnectivityRepository
import com.jrblanco.boccantabria.domain.repository.SearchRepository
import com.jrblanco.boccantabria.domain.usecase.GetSearchIssuersUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedKeysUseCase
import com.jrblanco.boccantabria.domain.usecase.SearchPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.SetPublicationSavedUseCase
import com.jrblanco.boccantabria.domain.usecase.ShareOfficialDocumentUseCase
import com.jrblanco.boccantabria.fake.FakeDocumentRepository
import com.jrblanco.boccantabria.fake.FakePublicationRemoteDataSource
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import com.jrblanco.boccantabria.fake.rssItem
import com.jrblanco.boccantabria.ui.search.SearchContentState
import com.jrblanco.boccantabria.ui.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The whole search chain: bytes as a source publishes them, through the normaliser and the store,
 * out to the screen.
 *
 * This is the one that would catch the mistake nothing else can. The searchable text is written on
 * the way in, by one file, and read on the way out, by another, and neither mentions the other. If
 * they ever disagree about what normalisation means, every test above still passes and nothing is
 * ever found again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SearchFlowIntegrationTest {

    private val dispatcher = StandardTestDispatcher()
    private val analytics = RecordingAnalyticsTracker()
    private lateinit var database: BocDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BocDatabase::class.java)
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    /** Accents on one side, none on the other, and they still meet. */
    @Test
    fun `an announcement a source published is found by typing its issuer without accents`() =
        runTest(dispatcher) {
            synchronise()
            val search = searchViewModel()

            search.uiState.test {
                search.onQueryChanged("pielagos")
                advanceUntilIdle()

                val content = expectMostRecentItem().content
                assertTrue("no se encontró nada", content is SearchContentState.Results)
                assertEquals(
                    listOf("boc:439765"),
                    (content as SearchContentState.Results).items.map { it.externalKey },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * The section's **name** never touches the table, which stores a code. It is only findable
     * because the name went into the searchable text as the row was written.
     */
    @Test
    fun `an announcement is found by the name of its section, which the table never stores`() =
        runTest(dispatcher) {
            synchronise()
            val search = searchViewModel()

            search.uiState.test {
                search.onQueryChanged("disposiciones")
                advanceUntilIdle()

                assertTrue(expectMostRecentItem().content is SearchContentState.Results)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the reference of the announcement finds it too`() = runTest(dispatcher) {
        synchronise()
        val search = searchViewModel()

        search.uiState.test {
            search.onQueryChanged("439765")
            advanceUntilIdle()

            assertTrue(expectMostRecentItem().content is SearchContentState.Results)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a term nothing carries is an empty state, never an error`() = runTest(dispatcher) {
        synchronise()
        val search = searchViewModel()

        search.uiState.test {
            search.onQueryChanged("expropiacion")
            advanceUntilIdle()

            assertEquals(SearchContentState.Empty, expectMostRecentItem().content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filtering by a section that has nothing narrows the result to nothing`() = runTest(dispatcher) {
        synchronise()
        val search = searchViewModel()

        search.uiState.test {
            search.onQueryChanged("pielagos")
            advanceUntilIdle()
            search.onFiltersApplied(
                SearchQuery(sectionCode = "9"),
            )
            advanceUntilIdle()

            assertEquals(SearchContentState.Empty, expectMostRecentItem().content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the order can be turned around`() = runTest(dispatcher) {
        synchronise(
            rssItem(blobId = "1", title = "AYUNTAMIENTO DE PIÉLAGOS: Antiguo.", date = "2026-08-01"),
            rssItem(blobId = "2", title = "AYUNTAMIENTO DE PIÉLAGOS: Reciente.", date = "2026-08-27"),
        )
        val search = searchViewModel()

        search.uiState.test {
            search.onQueryChanged("pielagos")
            advanceUntilIdle()
            val newest = (expectMostRecentItem().content as SearchContentState.Results).items

            search.onSortChanged(SearchSort.OLDEST_FIRST)
            advanceUntilIdle()
            val oldest = (expectMostRecentItem().content as SearchContentState.Results).items

            assertEquals(newest.map { it.externalKey }.reversed(), oldest.map { it.externalKey })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Saving from a result writes the same mark the bulletin writes. */
    @Test
    fun `a result can be saved, and the mark comes back on the next search`() = runTest(dispatcher) {
        synchronise()
        val search = searchViewModel()

        search.uiState.test {
            search.onQueryChanged("pielagos")
            advanceUntilIdle()
            val found = (expectMostRecentItem().content as SearchContentState.Results).items.single()

            search.onToggleSaved(found)
            advanceUntilIdle()

            assertEquals(setOf(found.externalKey), expectMostRecentItem().savedKeys)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- The graph, wired by hand ----------

    private suspend fun synchronise(vararg items: RssItemDto) {
        val remote = FakePublicationRemoteDataSource()
        val published = if (items.isEmpty()) {
            arrayOf(rssItem(blobId = "439765", title = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva."))
        } else {
            items
        }
        remote.respondWithItems("6802081", "hash-1", *published)

        PublicationRepositoryImpl(
            remoteDataSource = remote,
            publicationDao = database.publicationDao(),
            feedSyncStateDao = database.feedSyncStateDao(),
            normalizer = PublicationNormalizer(),
            sectionRepository = BocSectionRepositoryImpl(),
            feeds = BocFeedCatalog.definitions,
            time = object : TimeProvider { override fun nowMillis() = 5_000L },
            dispatchers = TestDispatcherProvider(dispatcher),
            analytics = analytics,
            crashReporter = NoOpCrashReporter(),
        ).refresh()
    }

    private fun searchViewModel(): SearchViewModel {
        val searchRepository: SearchRepository = SearchRepositoryImpl(
            searchDao = database.publicationSearchDao(),
            dispatchers = TestDispatcherProvider(dispatcher),
            crashReporter = NoOpCrashReporter(),
        )
        val savedRepository = SavedPublicationRepositoryImpl(
            savedPublicationDao = database.savedPublicationDao(),
            time = object : TimeProvider { override fun nowMillis() = 6_000L },
            dispatchers = TestDispatcherProvider(dispatcher),
            analytics = analytics,
            crashReporter = NoOpCrashReporter(),
        )
        return SearchViewModel(
            savedStateHandle = SavedStateHandle(),
            searchPublications = SearchPublicationsUseCase(searchRepository),
            getSearchIssuers = GetSearchIssuersUseCase(searchRepository),
            observeSavedKeys = ObserveSavedKeysUseCase(savedRepository),
            setPublicationSaved = SetPublicationSavedUseCase(savedRepository),
            shareDocument = ShareOfficialDocumentUseCase(
                documents = FakeDocumentRepository(),
                connectivity = object : ConnectivityRepository { override fun isOnline() = true },
            ),
            analytics = analytics,
        )
    }
}
