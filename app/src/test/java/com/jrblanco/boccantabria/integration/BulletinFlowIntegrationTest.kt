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
import com.jrblanco.boccantabria.data.source.local.BocDatabase
import com.jrblanco.boccantabria.data.source.remote.BocFeedCatalog
import com.jrblanco.boccantabria.data.source.remote.BocFeedDefinition
import com.jrblanco.boccantabria.data.source.remote.BocRssParser
import com.jrblanco.boccantabria.data.source.remote.FeedFetchResult
import com.jrblanco.boccantabria.data.source.remote.PublicationNormalizer
import com.jrblanco.boccantabria.data.source.remote.PublicationRemoteDataSource
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.domain.repository.PublicationRepository
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveBulletinHeaderUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.RefreshPublicationsUseCase
import com.jrblanco.boccantabria.domain.repository.ConnectivityRepository
import com.jrblanco.boccantabria.domain.usecase.ShareOfficialDocumentUseCase
import com.jrblanco.boccantabria.fake.FakeDocumentRepository
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import com.jrblanco.boccantabria.ui.home.HomeContentState
import com.jrblanco.boccantabria.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The real chain from the screen down to the database, with only the transport replaced by the
 * bytes of a source.
 *
 * The samples are real BOC responses, including the 4.3 feed whose old entries carry permuted
 * categories. So this is also the proof of the promise that matters most: an untidy source does
 * not lose a single announcement on its way to the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BulletinFlowIntegrationTest {

    private val dispatcher = StandardTestDispatcher()
    private val analytics = RecordingAnalyticsTracker()
    private lateinit var database: BocDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BocDatabase::class.java)
            // Room runs its queries on an executor of its own, outside the test scheduler, so
            // `advanceUntilIdle()` would return before the database had finished. Handing it the
            // test dispatcher puts the whole chain under one clock, which is what makes the
            // assertions deterministic instead of racy.
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun `the bytes of a real source reach the screen as cards`() = runTest(dispatcher) {
        val viewModel = viewModel(repository(FixtureRemoteDataSource()))

        viewModel.uiState.test {
            assertEquals(HomeContentState.Skeleton, awaitItem().content)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            val content = state.content as HomeContentState.Publications
            // The day's bulletin is one date across every section: in these samples the most
            // recent is 2026-08-28, which only the 2.2 source published.
            assertTrue(content.items.isNotEmpty())
            assertEquals(LocalDate.of(2026, 8, 28), state.header.date)
            assertTrue(content.items.all { it.publicationDate == LocalDate.of(2026, 8, 28) })
            assertEquals(content.items.size, state.header.publicationCount)
            // The samples are real responses, so this also says the chain produced usable
            // records rather than merely non-empty ones.
            assertTrue(content.items.all { it.title.isNotBlank() })
            assertTrue(content.items.all { it.documentUrl.startsWith("https://boc.cantabria.es/") })
            assertTrue(content.items.all { it.issuer != null })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the anomalous 4_3 source loses nothing on the way to the screen`() = runTest(dispatcher) {
        val repository = repository(FixtureRemoteDataSource())
        repository.refresh()
        advanceUntilIdle()

        // Nine announcements in the sample, three of them with permuted categories.
        assertEquals(9, database.publicationDao().observeBySection("4").first().size)
    }

    @Test
    fun `a section with nothing published since 2021 is still reachable and is not an error`() = runTest(dispatcher) {
        val repository = repository(FixtureRemoteDataSource())
        repository.refresh()
        advanceUntilIdle()

        val viewModel = viewModel(repository, sectionCode = "4", subsectionCode = "4.3")

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.content is HomeContentState.Publications)
            assertEquals(LocalDate.of(2021, 3, 26), state.header.date)
            assertEquals("Actuaciones en materia de Seguridad Social", state.header.sectionName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty source shows an empty state, never an error`() = runTest(dispatcher) {
        val repository = repository(FixtureRemoteDataSource())
        repository.refresh()
        advanceUntilIdle()

        val viewModel = viewModel(repository, sectionCode = "8", subsectionCode = "8.1")

        viewModel.uiState.test {
            advanceUntilIdle()
            assertEquals(HomeContentState.Empty, expectMostRecentItem().content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Wiring ----------

    private fun repository(remote: PublicationRemoteDataSource): PublicationRepository =
        PublicationRepositoryImpl(
            remoteDataSource = remote,
            publicationDao = database.publicationDao(),
            feedSyncStateDao = database.feedSyncStateDao(),
            normalizer = PublicationNormalizer(),
            sectionRepository = BocSectionRepositoryImpl(),
            feeds = BocFeedCatalog.definitions,
            time = object : TimeProvider {
                override fun nowMillis(): Long = 1_000_000
            },
            dispatchers = TestDispatcherProvider(dispatcher),
            analytics = analytics,
            crashReporter = NoOpCrashReporter(),
        )

    private fun viewModel(
        repository: PublicationRepository,
        sectionCode: String? = null,
        subsectionCode: String? = null,
    ) = HomeViewModel(
        savedStateHandle = SavedStateHandle(
            buildMap {
                sectionCode?.let { put(HomeViewModel.ARG_SECTION_CODE, it) }
                subsectionCode?.let { put(HomeViewModel.ARG_SUBSECTION_CODE, it) }
            },
        ),
        observePublications = ObservePublicationsUseCase(repository),
        observeHeader = ObserveBulletinHeaderUseCase(repository),
        refreshPublications = RefreshPublicationsUseCase(repository),
        getSections = GetBocSectionsUseCase(BocSectionRepositoryImpl()),
        shareDocument = ShareOfficialDocumentUseCase(
            // Sharing is exercised in its own tests; here it only has to exist so the
            // bulletin can be built.
            documents = FakeDocumentRepository(),
            connectivity = object : ConnectivityRepository { override fun isOnline() = true },
        ),
        analytics = analytics,
    )

    /** Serves the real samples, so everything above it is the production code. */
    private class FixtureRemoteDataSource : PublicationRemoteDataSource {

        private val parser = BocRssParser()

        private val fixtures = mapOf(
            "6802081" to "feed_1_disposiciones.xml",
            "6802085" to "feed_2_2_oposiciones.xml",
            "6802091" to "feed_4_3_anomalo.xml",
            "7479572" to "feed_8_1_vacio.xml",
        )

        override suspend fun fetchFeed(
            definition: BocFeedDefinition,
            knownBodyHash: String?,
        ): FeedFetchResult {
            val name = fixtures[definition.feedId]
                ?: return FeedFetchResult.Fetched(EMPTY_CHANNEL, "vacio-${definition.feedId}")

            val body = checkNotNull(
                javaClass.classLoader?.getResourceAsStream("fixtures/$name")
                    ?.bufferedReader()?.readText(),
            )
            return FeedFetchResult.Fetched(parser.parse(body), "hash-${definition.feedId}")
        }

        private companion object {
            val EMPTY_CHANNEL = com.jrblanco.boccantabria.data.source.remote.RssChannelDto(
                title = "Filtro BOC",
                link = null,
                description = null,
                declaredSize = 0,
                items = emptyList(),
            )
        }
    }
}
