package com.jrblanco.boccantabria.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.jrblanco.boccantabria.core.telemetry.NoOpCrashReporter
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.source.local.BocDatabase
import com.jrblanco.boccantabria.data.source.local.PublicationDao
import com.jrblanco.boccantabria.data.source.remote.BocFeedCatalog
import com.jrblanco.boccantabria.data.source.remote.BocFeedDefinition
import com.jrblanco.boccantabria.data.source.remote.FeedFailure
import com.jrblanco.boccantabria.data.source.remote.FeedFetchResult
import com.jrblanco.boccantabria.data.source.remote.PublicationNormalizer
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.HomeSelection
import com.jrblanco.boccantabria.fake.FakePublicationRemoteDataSource
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import com.jrblanco.boccantabria.data.source.local.toEntity
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import com.jrblanco.boccantabria.fake.publication
import com.jrblanco.boccantabria.fake.rssItem
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The synchronisation policy, exercised against a real in-memory database.
 *
 * Faking the database would mean reimplementing upsert semantics in the test, and the two copies
 * would drift. This way the rules that matter most — nothing is lost, nothing is duplicated —
 * are checked against the storage that actually enforces them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class PublicationRepositoryImplTest {

    private lateinit var database: BocDatabase
    private val remote = FakePublicationRemoteDataSource()
    private val analytics = RecordingAnalyticsTracker()
    private var now: Long = 1_000_000

    private val time = object : TimeProvider {
        override fun nowMillis(): Long = now
    }

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BocDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ---------- The happy path ----------

    @Test
    fun `the first synchronisation stores what every source published`() = runTest {
        remote.respondWithItems("6802081", "h1", rssItem("1"), rssItem("2"))
        remote.respondWithItems("6802085", "h2", rssItem("3"))

        val result = repository().refresh()

        val summary = (result as AppResult.Success).data
        assertEquals(19, summary.succeededFeeds)
        assertEquals(0, summary.failedFeeds)
        assertEquals(3, summary.insertedItems)
        assertEquals(3, database.publicationDao().count())
    }

    @Test
    fun `a second synchronisation with the same bytes parses nothing again`() = runTest {
        remote.respondWithItems("6802081", "h1", rssItem("1"))
        val repository = repository()
        repository.refresh()

        val summary = (repository.refresh() as AppResult.Success).data

        assertEquals("h1", remote.hashesSeen["6802081"])
        assertEquals(19, summary.unchangedFeeds)
        assertEquals(0, summary.insertedItems)
        assertEquals(1, database.publicationDao().count())
    }

    @Test
    fun `a new announcement is inserted and a changed one is updated, never duplicated`() = runTest {
        remote.respondWithItems("6802081", "h1", rssItem("1", title = "Título original"))
        val repository = repository()
        repository.refresh()

        remote.respondWithItems(
            "6802081", "h2",
            rssItem("1", title = "Título corregido"),
            rssItem("2"),
        )
        val summary = (repository.refresh() as AppResult.Success).data

        assertEquals(1, summary.insertedItems)
        assertEquals(1, summary.updatedItems)
        assertEquals(2, database.publicationDao().count())
        assertTrue(
            repository.observePublications(HomeSelection.TodaysBulletin).first()
                .any { it.title == "Título corregido" },
        )
    }

    @Test
    fun `an announcement that falls out of a source's window stays stored`() = runTest {
        remote.respondWithItems("6802081", "h1", rssItem("1"), rssItem("2"))
        val repository = repository()
        repository.refresh()

        // The source only ever publishes its last hundred; the older one simply stops appearing.
        remote.respondWithItems("6802081", "h2", rssItem("2"))
        repository.refresh()

        assertEquals(2, database.publicationDao().count())
    }

    @Test
    fun `an announcement rejected for being unusable is counted, and the rest survive`() = runTest {
        remote.respondWithItems(
            "6802081", "h1",
            rssItem("1"),
            rssItem("2", date = "26/08/2026"),
            rssItem("3", title = "  "),
        )

        val summary = (repository().refresh() as AppResult.Success).data

        assertEquals(1, summary.insertedItems)
        assertEquals(2, summary.rejectedItems)
    }

    @Test
    fun `the same announcement reaching two sources is stored once`() = runTest {
        remote.respondWithItems("6802081", "h1", rssItem("1"))
        remote.respondWithItems("6802085", "h2", rssItem("1"))

        repository().refresh()

        assertEquals(1, database.publicationDao().count())
    }

    // ---------- Feature 012: what was new, and the baseline ----------

    @Test
    fun `the first successful synchronisation reports no new keys and marks the baseline`() = runTest {
        remote.respondWithItems("6802081", "h1", rssItem("1"), rssItem("2"))

        val summary = (repository().refresh() as AppResult.Success).data

        assertTrue(summary.isBaseline)
        assertTrue(summary.newKeys.isEmpty())
        assertEquals(2, summary.insertedItems)
        assertEquals("true", analytics.events.last { it.name == PublicationRepositoryImpl.EVENT_SYNC }.parameters["baseline"])
    }

    @Test
    fun `a later synchronisation reports exactly the inserted keys`() = runTest {
        remote.respondWithItems("6802081", "h1", rssItem("1"))
        val repository = repository()
        repository.refresh()

        remote.respondWithItems("6802081", "h2", rssItem("1", title = "Corregido"), rssItem("2"))
        remote.respondWithItems("6802085", "h3", rssItem("3"))
        val summary = (repository.refresh() as AppResult.Success).data

        assertFalse(summary.isBaseline)
        assertEquals(setOf("boc:2", "boc:3"), summary.newKeys)
    }

    // ---------- Feature 014: the pending mark lives in the store ----------

    /** History is not news, and it is decided at insert time: the baseline marks nothing (D-608). */
    @Test
    fun `the baseline stores nothing as pending`() = runTest {
        remote.respondWithItems("6802081", "h1", rssItem("1"), rssItem("2"))
        val repository = repository()

        repository.refresh()

        assertEquals(AppResult.Success(emptyList<Any>()), repository.pendingAlertCandidates())
        assertTrue(database.publicationDao().pendingAlertEvaluation().isEmpty())
    }

    @Test
    fun `a later synchronisation leaves exactly the inserted keys pending, stamped with when they were stored`() =
        runTest {
            remote.respondWithItems("6802081", "h1", rssItem("1"))
            val repository = repository()
            repository.refresh()

            now = 2_000_000
            remote.respondWithItems("6802081", "h2", rssItem("1", title = "Corregido"), rssItem("2"))
            remote.respondWithItems("6802085", "h3", rssItem("3"))
            repository.refresh()

            val pending = (repository.pendingAlertCandidates() as AppResult.Success).data
            assertEquals(setOf("boc:2", "boc:3"), pending.map { it.publication.externalKey }.toSet())
            assertTrue(pending.all { it.storedAt == 2_000_000L })

            assertEquals(AppResult.Success(Unit), repository.markAlertsEvaluated(setOf("boc:2")))
            assertEquals(
                listOf("boc:3"),
                (repository.pendingAlertCandidates() as AppResult.Success).data.map { it.publication.externalKey },
            )
        }

    /** The backfill fills in searchable text; the pending mark is somebody else's and it stays put. */
    @Test
    fun `the backfill leaves the pending flag alone`() = runTest {
        givenStoredRowsWithoutSearchText(count = 1)
        database.publicationDao().insert(
            listOf(publication(key = "old:2", title = "AYUNTAMIENTO DE SANTOÑA: Bases").toEntity(seenAt = 1L, searchText = "", pendingAlertEvaluation = true)),
        )

        repository().refresh()

        assertEquals(0, database.publicationDao().withoutSearchText(limit = 100).size)
        assertEquals(listOf("old:2"), database.publicationDao().pendingAlertEvaluation().map { it.externalKey })
    }

    @Test
    fun `a feed that fails contributes no keys`() = runTest {
        remote.respondWithItems("6802081", "h1", rssItem("1"))
        val repository = repository()
        repository.refresh()

        remote.respondWith("6802081", FeedFetchResult.Failed(FeedFailure.SERVER_ERROR))
        remote.respondWithItems("6802085", "h2", rssItem("2"))
        val summary = (repository.refresh() as AppResult.Success).data

        assertEquals(setOf("boc:2"), summary.newKeys)
    }

    @Test
    fun `the sync event never carries keys`() = runTest {
        remote.respondWithItems("6802081", "h1", rssItem("1"))
        val repository = repository()
        repository.refresh()
        remote.respondWithItems("6802081", "h2", rssItem("1"), rssItem("2"))
        repository.refresh()

        val event = analytics.events.last { it.name == PublicationRepositoryImpl.EVENT_SYNC }
        assertFalse(event.parameters.values.any { it.contains("boc:") })
        assertEquals("false", event.parameters["baseline"])
    }

    @Test
    fun `the newest rows and the last success are readable`() = runTest {
        remote.respondWithItems("6802081", "h1", rssItem("1", date = "2026-08-01"), rssItem("2", date = "2026-08-27"))
        val repository = repository()
        assertEquals(null, repository.lastSuccessfulSyncAt())

        repository.refresh()

        assertEquals(listOf("boc:2"), repository.newest(1).map { it.externalKey })
        assertEquals(java.lang.Long.valueOf(now), repository.lastSuccessfulSyncAt())
    }

    // ---------- Failure ----------

    @Test
    fun `one source failing does not disturb the other eighteen`() = runTest {
        remote.respondWith("6802081", FeedFetchResult.Failed(FeedFailure.SERVER_ERROR))
        remote.respondWithItems("6802085", "h2", rssItem("2"), rssItem("3"))

        val summary = (repository().refresh() as AppResult.Success).data

        assertEquals(1, summary.failedFeeds)
        assertEquals(18, summary.succeededFeeds)
        assertFalse(summary.isComplete)
        assertFalse(summary.allFailed)
        assertEquals(2, database.publicationDao().count())
    }

    @Test
    fun `every source failing with content stored is a success carrying the news`() = runTest {
        remote.respondWithItems("6802081", "h1", rssItem("1"))
        val repository = repository()
        repository.refresh()

        remote.failEveryFeed()
        val summary = (repository.refresh() as AppResult.Success).data

        assertTrue(summary.allFailed)
        assertEquals(1, database.publicationDao().count())
    }

    @Test
    fun `every source failing with nothing stored is the one real failure`() = runTest {
        remote.failEveryFeed()

        assertEquals(AppResult.Failure(DomainError.Network), repository().refresh())
    }

    @Test
    fun `a source that blows up unexpectedly is counted as failed, not propagated`() = runTest {
        val exploding = object : com.jrblanco.boccantabria.data.source.remote.PublicationRemoteDataSource {
            override suspend fun fetchFeed(definition: BocFeedDefinition, knownBodyHash: String?) =
                error("boom")
        }

        val result = repository(remoteDataSource = exploding).refresh()

        assertEquals(AppResult.Failure(DomainError.Network), result)
    }

    // ---------- Politeness towards the service ----------

    @Test
    fun `never more than four sources are read at once`() = runTest {
        repository().refresh()

        assertEquals(19, remote.calls.size)
        assertTrue(
            "se leyeron ${remote.maxConcurrent} fuentes a la vez",
            remote.maxConcurrent <= 4,
        )
    }

    @Test
    fun `a source that is switched off is not read`() = runTest {
        val feeds = BocFeedCatalog.definitions.map {
            if (it.feedId == "7479572") it.copy(enabled = false) else it
        }

        repository(feeds = feeds).refresh()

        assertEquals(18, remote.calls.size)
        assertFalse("7479572" in remote.calls)
    }

    // ---------- Staleness ----------

    @Test
    fun `with nothing ever synchronised the cache is stale`() = runTest {
        assertTrue(repository().isCacheStale())
    }

    @Test
    fun `just after a synchronisation the cache is fresh`() = runTest {
        val repository = repository()
        repository.refresh()

        now += 5 * 60 * 1_000

        assertFalse(repository.isCacheStale())
    }

    @Test
    fun `after half an hour the cache is stale again`() = runTest {
        val repository = repository()
        repository.refresh()

        now += PublicationRepositoryImpl.CACHE_TTL_MILLIS

        assertTrue(repository.isCacheStale())
    }

    // ---------- Reading ----------

    @Test
    fun `the day's bulletin is the most recent date across every section`() = runTest {
        remote.respondWithItems("6802081", "h1", rssItem("1", date = "2026-08-27"))
        remote.respondWithItems("6802085", "h2", rssItem("2", date = "2026-08-20"))
        val repository = repository()
        repository.refresh()

        val today = repository.observePublications(HomeSelection.TodaysBulletin).first()

        assertEquals(listOf("boc:1"), today.map { it.externalKey })
    }

    @Test
    fun `a section shows its whole history, not only the latest date`() = runTest {
        remote.respondWithItems("6802091", "h1", rssItem("9", date = "2021-03-26"))
        remote.respondWithItems("6802081", "h2", rssItem("1", date = "2026-08-27"))
        val repository = repository()
        repository.refresh()

        // 4.3 has published nothing since 2021. Filtering sections by the latest date would make
        // it permanently empty, which is exactly what the specification forbids.
        val section = repository.observePublications(HomeSelection.Section("4", "4.3")).first()

        assertEquals(1, section.size)
    }

    @Test
    fun `the header of a section names it and counts what it holds`() = runTest {
        remote.respondWithItems("6802085", "h1", rssItem("1"), rssItem("2"))
        val repository = repository()
        repository.refresh()

        val header = repository.observeHeader(HomeSelection.Section("2", "2.2")).first()

        assertEquals("Cursos, oposiciones y concursos", header.sectionName)
        assertEquals(2, header.publicationCount)
    }

    @Test
    fun `an empty database reads as an empty list, never as a failure`() = runTest {
        val repository = repository()

        assertTrue(repository.observePublications(HomeSelection.TodaysBulletin).first().isEmpty())
        assertEquals(0, repository.observeHeader(HomeSelection.TodaysBulletin).first().publicationCount)
    }

    /**
     * Feature 014 (STAB-004): a read that fails once used to leave Inicio on an empty list for as long
     * as the screen lived — refreshing wrote new rows nobody observed any more.
     */
    @Test
    fun `observing the bulletin survives a read failure`() = runTest {
        remote.respondWithItems("6802081", "h1", rssItem("1"))
        repository().refresh()
        val real = database.publicationDao()
        var attempts = 0
        val flaky = mockk<PublicationDao>()
        every { flaky.observeTodaysBulletin() } returns flow {
            if (attempts++ == 0) throw IllegalStateException("base ocupada")
            emitAll(real.observeTodaysBulletin())
        }

        repository(publicationDao = flaky).observePublications(HomeSelection.TodaysBulletin).test {
            assertEquals(emptyList<Any>(), awaitItem())
            assertEquals(listOf("boc:1"), awaitItem().map { it.externalKey })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Telemetry ----------

    @Test
    fun `the synchronisation reports counts and nothing else`() = runTest {
        remote.respondWithItems("6802081", "h1", rssItem("1"))

        repository().refresh()

        val event = analytics.events.single { it.name == PublicationRepositoryImpl.EVENT_SYNC }
        assertEquals("19", event.parameters["succeeded"])
        assertEquals("1", event.parameters["inserted"])
        // Counts, plus the one flag feature 012 added. Never a key, never a title.
        assertTrue(event.parameters.filterKeys { it != "baseline" }.values.all { it.toIntOrNull() != null })
        assertEquals("true", event.parameters["baseline"])
    }

    // ---------- The searchable text, and the rows that predate it ----------

    @Test
    fun `what a synchronisation stores arrives with its searchable text`() = runTest {
        remote.respondWithItems(
            feedId = "6802081",
            bodyHash = "hash-1",
            rssItem(blobId = "900001", title = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación"),
        )

        repository().refresh()

        val stored = database.publicationDao().observePublication("boc:900001").first()!!
        assertTrue(stored.searchText.contains("ayuntamiento de pielagos"))
        // The section name is in there although the table only stores the code, which is what makes
        // typing `disposiciones` find this announcement at all.
        assertTrue(stored.searchText.contains("disposiciones generales"))
    }

    /**
     * The failure a clean install cannot reveal.
     *
     * After the migration to version 3 every stored row has an empty `search_text`, and a
     * synchronisation only refreshes each source's last hundred announcements — so without this,
     * everything downloaded by an earlier version of the application would be unfindable forever.
     */
    @Test
    fun `rows written before the column existed are filled in by a synchronisation`() = runTest {
        givenStoredRowsWithoutSearchText(count = 3)

        repository().refresh()

        assertEquals(0, database.publicationDao().withoutSearchText(limit = 100).size)
        val filled = database.publicationDao().observePublication("old:1").first()!!
        assertTrue(filled.searchText.contains("ayuntamiento de santona"))
    }

    /**
     * The loop comes back for what did not fit in the first pass.
     *
     * Proved with a batch of two rather than with an archive of thousands: the property is "it goes
     * round again", and storing volume to demonstrate it would only make the suite slow. The batch
     * size is injected for exactly this.
     */
    @Test
    fun `the backfill goes round again for what did not fit in one batch`() = runTest {
        givenStoredRowsWithoutSearchText(count = 5)

        repository(backfillBatchSize = 2).refresh()

        assertEquals(0, database.publicationDao().withoutSearchText(limit = 100).size)
    }

    /** Idempotent: a second run finds nothing to do and rewrites nothing. */
    @Test
    fun `a second synchronisation does not rewrite what is already filled in`() = runTest {
        givenStoredRowsWithoutSearchText(count = 2)
        repository().refresh()
        val afterFirst = database.publicationDao().observePublication("old:1").first()!!.searchText

        repository().refresh()

        assertEquals(afterFirst, database.publicationDao().observePublication("old:1").first()!!.searchText)
        assertEquals(0, database.publicationDao().withoutSearchText(limit = 100).size)
    }

    /** The saved mark is not the source's, and the backfill has no business touching it. */
    @Test
    fun `the backfill leaves the saved mark alone`() = runTest {
        givenStoredRowsWithoutSearchText(count = 1)
        database.savedPublicationDao().setSavedAt("old:1", 7_000L)

        repository().refresh()

        assertEquals(7_000L, database.publicationDao().observePublication("old:1").first()!!.savedAt)
    }

    /** Writes rows exactly as the automatic migration leaves them: content, but no searchable text. */
    private suspend fun givenStoredRowsWithoutSearchText(count: Int) {
        database.publicationDao().insert(
            (1..count).map { index ->
                publication(
                    key = "old:$index",
                    title = "AYUNTAMIENTO DE SANTOÑA: Bases reguladoras $index",
                    issuer = "Ayuntamiento de Santoña",
                ).toEntity(seenAt = 1L, searchText = "")
            },
        )
    }

    private fun repository(
        remoteDataSource: com.jrblanco.boccantabria.data.source.remote.PublicationRemoteDataSource = remote,
        feeds: List<BocFeedDefinition> = BocFeedCatalog.definitions,
        backfillBatchSize: Int = PublicationRepositoryImpl.BACKFILL_BATCH_SIZE,
        publicationDao: PublicationDao = database.publicationDao(),
    ) = PublicationRepositoryImpl(
        remoteDataSource = remoteDataSource,
        publicationDao = publicationDao,
        feedSyncStateDao = database.feedSyncStateDao(),
        normalizer = PublicationNormalizer(),
        sectionRepository = BocSectionRepositoryImpl(),
        feeds = feeds,
        time = time,
        dispatchers = TestDispatcherProvider(),
        analytics = analytics,
        crashReporter = NoOpCrashReporter(),
        backfillBatchSize = backfillBatchSize,
    )
}
