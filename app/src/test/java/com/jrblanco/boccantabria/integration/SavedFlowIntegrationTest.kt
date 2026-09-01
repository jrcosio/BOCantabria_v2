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
import com.jrblanco.boccantabria.data.source.local.BocDatabase
import com.jrblanco.boccantabria.data.source.local.toDomain
import com.jrblanco.boccantabria.data.source.remote.BocFeedCatalog
import com.jrblanco.boccantabria.data.source.remote.PublicationNormalizer
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.ConnectivityRepository
import com.jrblanco.boccantabria.domain.repository.PublicationRepository
import com.jrblanco.boccantabria.domain.repository.SavedPublicationRepository
import com.jrblanco.boccantabria.domain.usecase.FilterPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveBulletinHeaderUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedKeysUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.RefreshPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.ReleaseUnusedDocumentsUseCase
import com.jrblanco.boccantabria.domain.usecase.SetPublicationSavedUseCase
import com.jrblanco.boccantabria.domain.usecase.ShareOfficialDocumentUseCase
import com.jrblanco.boccantabria.fake.FakeDocumentRepository
import com.jrblanco.boccantabria.fake.FakePublicationRemoteDataSource
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import com.jrblanco.boccantabria.fake.rssItem
import com.jrblanco.boccantabria.ui.home.HomeViewModel
import com.jrblanco.boccantabria.ui.saved.SavedContentState
import com.jrblanco.boccantabria.ui.saved.SavedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
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
 * The whole saved chain, from one screen down to the database and back up to another.
 *
 * The most valuable test of the feature, because the promise it checks —a mark survives a
 * synchronisation— is a property of two files that never mention each other: the update statement
 * that omits the column, and the query that reads it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SavedFlowIntegrationTest {

    private val dispatcher = StandardTestDispatcher()
    private val analytics = RecordingAnalyticsTracker()
    private var now = 5_000L
    private lateinit var database: BocDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BocDatabase::class.java)
            // Room usa un ejecutor propio, fuera del planificador de la prueba, así que
            // `advanceUntilIdle()` volvería antes de que la base de datos hubiera terminado.
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
    fun `saving from the bulletin puts the publication on the saved list`() = runTest(dispatcher) {
        val home = homeViewModel(remoteWithOneAnnouncement())
        val saved = savedViewModel()

        home.uiState.test {
            advanceUntilIdle()
            val publication = firstStoredPublication()
            home.onToggleSaved(publication)
            advanceUntilIdle()

            saved.uiState.test {
                advanceUntilIdle()
                val content = expectMostRecentItem().content
                assertTrue(content is SavedContentState.Publications)
                assertEquals(
                    listOf(publication.externalKey),
                    (content as SavedContentState.Publications).items.map { it.externalKey },
                )
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The regression this whole feature rests on (FR-020, SC-004). */
    @Test
    fun `a later synchronisation that brings the publication again does not lose the mark`() =
        runTest(dispatcher) {
            val remote = remoteWithOneAnnouncement()
            val home = homeViewModel(remote)
            val saved = savedViewModel()

            home.uiState.test {
                advanceUntilIdle()
                home.onToggleSaved(firstStoredPublication())
                advanceUntilIdle()

                // La fuente vuelve a publicar el mismo anuncio, con el título corregido, como hace
                // cada día: solo publica sus últimos cien.
                remote.respondWithItems(
                    feedId = FEED_ID,
                    bodyHash = "hash-2",
                    rssItem(blobId = BLOB_ID, title = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación corregida."),
                )
                home.onRefresh()
                advanceUntilIdle()

                saved.uiState.test {
                    advanceUntilIdle()
                    val content = expectMostRecentItem().content as SavedContentState.Publications
                    assertEquals(1, content.items.size)
                    // Sigue guardada, y además con el título que la fuente acaba de corregir.
                    assertEquals(
                        "AYUNTAMIENTO DE PIÉLAGOS: Aprobación corregida.",
                        content.items.single().title,
                    )
                    cancelAndIgnoreRemainingEvents()
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `unsaving from the list retires the mark and leaves the publication stored`() =
        runTest(dispatcher) {
            val home = homeViewModel(remoteWithOneAnnouncement())
            val saved = savedViewModel()

            home.uiState.test {
                advanceUntilIdle()
                val publication = firstStoredPublication()
                home.onToggleSaved(publication)
                advanceUntilIdle()

                saved.onToggleSaved(publication)
                advanceUntilIdle()

                saved.uiState.test {
                    advanceUntilIdle()
                    assertEquals(SavedContentState.Empty, expectMostRecentItem().content)
                    cancelAndIgnoreRemainingEvents()
                }
                cancelAndIgnoreRemainingEvents()
            }
            // FR-021: lo que se retira es la marca. La publicación sigue donde estaba.
            assertEquals(1, database.publicationDao().count())
        }

    @Test
    fun `the mark shows up on the bulletin card without anybody reloading anything`() =
        runTest(dispatcher) {
            val home = homeViewModel(remoteWithOneAnnouncement())
            val saved = savedViewModel()

            home.uiState.test {
                advanceUntilIdle()
                val publication = firstStoredPublication()

                home.onToggleSaved(publication)
                advanceUntilIdle()
                assertEquals(setOf(publication.externalKey), expectMostRecentItem().savedKeys)

                // Y a la inversa: quitarla desde Guardados apaga el marcador del boletín (SC-003).
                saved.onToggleSaved(publication)
                advanceUntilIdle()
                assertTrue(expectMostRecentItem().savedKeys.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------- Wiring ----------

    /**
     * The first stored publication, read straight from the store.
     *
     * Not from the view model's state: it is exposed with `WhileSubscribed`, so without a collector
     * it never leaves its initial value. The store is the single source of truth anyway, which is
     * the whole point of the architecture being tested here.
     */
    private suspend fun firstStoredPublication(): Publication =
        database.publicationDao().observeTodaysBulletin().first().first().toDomain()

    private fun remoteWithOneAnnouncement() = FakePublicationRemoteDataSource().apply {
        respondWithItems(feedId = FEED_ID, bodyHash = "hash-1", rssItem(blobId = BLOB_ID))
    }

    private val savedRepository: SavedPublicationRepository by lazy {
        SavedPublicationRepositoryImpl(
            savedPublicationDao = database.savedPublicationDao(),
            time = object : TimeProvider {
                override fun nowMillis(): Long = now++
            },
            dispatchers = TestDispatcherProvider(dispatcher),
            analytics = analytics,
            crashReporter = NoOpCrashReporter(),
        )
    }

    private fun publicationRepository(
        remote: FakePublicationRemoteDataSource,
    ): PublicationRepository = PublicationRepositoryImpl(
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

    private fun homeViewModel(remote: FakePublicationRemoteDataSource): HomeViewModel {
        val publications = publicationRepository(remote)
        return HomeViewModel(
            savedStateHandle = SavedStateHandle(emptyMap()),
            observePublications = ObservePublicationsUseCase(publications),
            observeHeader = ObserveBulletinHeaderUseCase(publications),
            refreshPublications = RefreshPublicationsUseCase(publications),
            filterPublications = FilterPublicationsUseCase(),
            getSections = GetBocSectionsUseCase(BocSectionRepositoryImpl()),
            observeSavedKeys = ObserveSavedKeysUseCase(savedRepository),
            setPublicationSaved = SetPublicationSavedUseCase(savedRepository),
            shareDocument = shareUseCase(),
            releaseUnusedDocuments = ReleaseUnusedDocumentsUseCase(FakeDocumentRepository()),
            analytics = analytics,
        )
    }

    private fun savedViewModel() = SavedViewModel(
        observeSaved = ObserveSavedPublicationsUseCase(savedRepository),
        setPublicationSaved = SetPublicationSavedUseCase(savedRepository),
        shareDocument = shareUseCase(),
        analytics = analytics,
    )

    private fun shareUseCase() = ShareOfficialDocumentUseCase(
        documents = FakeDocumentRepository(),
        connectivity = object : ConnectivityRepository { override fun isOnline() = true },
    )

    private companion object {
        const val FEED_ID = "6802081"
        const val BLOB_ID = "439765"
    }
}
