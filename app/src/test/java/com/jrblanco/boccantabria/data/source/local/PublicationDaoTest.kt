package com.jrblanco.boccantabria.data.source.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.domain.model.EditionType
import com.jrblanco.boccantabria.domain.model.IdSource
import com.jrblanco.boccantabria.domain.model.ParserWarning
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The rules that keep the stored bulletin trustworthy: no duplicates, no losses, and an order
 * that does not depend on which of the nineteen sources answered first.
 *
 * Runs under Robolectric with an in-memory database: Room needs a real SQLite, and an in-memory
 * one means each test starts from nothing without touching the device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class PublicationDaoTest {

    private lateinit var database: BocDatabase
    private lateinit var dao: PublicationDao

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BocDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.publicationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `inserting reports how many rows were new`() = runTest {
        val counts = dao.upsertAll(listOf(entity("boc:1"), entity("boc:2")))

        assertEquals(UpsertCounts(inserted = 2, updated = 0), counts)
        assertEquals(2, dao.count())
    }

    @Test
    fun `seeing a publication again updates it instead of duplicating it`() = runTest {
        dao.upsertAll(listOf(entity("boc:1", title = "Título original")))

        val counts = dao.upsertAll(listOf(entity("boc:1", title = "Título corregido")))

        assertEquals(UpsertCounts(inserted = 0, updated = 1), counts)
        assertEquals(1, dao.count())
        assertEquals("Título corregido", dao.observeTodaysBulletin().first().single().title)
    }

    @Test
    fun `an update refreshes the last sighting and never rewrites the first`() = runTest {
        dao.upsertAll(listOf(entity("boc:1", seenAt = 1_000)))

        dao.upsertAll(listOf(entity("boc:1", title = "Otro", seenAt = 9_000)))

        val stored = dao.observeTodaysBulletin().first().single()
        assertEquals(1_000, stored.firstSeenAt)
        assertEquals(9_000, stored.lastSeenAt)
    }

    @Test
    fun `two publications cannot share a blob id`() = runTest {
        dao.upsertAll(listOf(entity("boc:1", blobId = "439765")))

        // Same announcement reached through a second source under a different key: the unique
        // index rejects the row instead of showing the same card twice.
        val counts = dao.upsertAll(listOf(entity("otra-clave", blobId = "439765")))

        assertEquals(UpsertCounts(inserted = 0, updated = 0), counts)
        assertEquals(1, dao.count())
    }

    @Test
    fun `publications without a blob id are not treated as duplicates of each other`() = runTest {
        val counts = dao.upsertAll(
            listOf(entity("hash:a", blobId = null), entity("hash:b", blobId = null)),
        )

        assertEquals(2, counts.inserted)
    }

    @Test
    fun `the day's bulletin is only the most recent date`() = runTest {
        dao.upsertAll(
            listOf(
                entity("boc:1", date = LocalDate.of(2026, 8, 27)),
                entity("boc:2", date = LocalDate.of(2026, 8, 27)),
                entity("boc:3", date = LocalDate.of(2026, 8, 26)),
            ),
        )

        assertEquals(
            listOf("boc:2", "boc:1"),
            dao.observeTodaysBulletin().first().map { it.externalKey },
        )
    }

    @Test
    fun `a section query reaches its subsections`() = runTest {
        dao.upsertAll(
            listOf(
                entity("boc:1", sectionCode = "2", subsectionCode = "2.1"),
                entity("boc:2", sectionCode = "2", subsectionCode = "2.2"),
                entity("boc:3", sectionCode = "7", subsectionCode = "7.1"),
            ),
        )

        assertEquals(2, dao.observeBySection("2").first().size)
        assertEquals(1, dao.observeBySubsection("2.2").first().size)
    }

    @Test
    fun `a section query is not limited to the most recent date`() = runTest {
        dao.upsertAll(
            listOf(
                entity("boc:1", sectionCode = "4", subsectionCode = "4.3", date = LocalDate.of(2021, 3, 26)),
                entity("boc:2", sectionCode = "1", date = LocalDate.of(2026, 8, 27)),
            ),
        )

        // The 4.3 subsection has published nothing since 2021. If sections were filtered by the
        // latest date it would always look empty, which is exactly what FR-035 forbids.
        assertEquals(1, dao.observeBySection("4").first().size)
    }

    @Test
    fun `the order is stable when the date repeats`() = runTest {
        val date = LocalDate.of(2026, 8, 27)
        val inserted = listOf(
            entity("boc:439700", blobId = "439700", date = date),
            entity("boc:439900", blobId = "439900", date = date),
            entity("boc:439800", blobId = "439800", date = date),
        )

        // Inserted in one order and then in the reverse one: both runs must read back the same.
        dao.upsertAll(inserted)
        val first = dao.observeBySection("1").first().map { it.externalKey }
        dao.upsertAll(inserted.reversed())
        val second = dao.observeBySection("1").first().map { it.externalKey }

        assertEquals(listOf("boc:439900", "boc:439800", "boc:439700"), first)
        assertEquals(first, second)
    }

    @Test
    fun `a publication with no numeric identifier still gets a deterministic position`() = runTest {
        val date = LocalDate.of(2026, 8, 27)
        dao.upsertAll(
            listOf(
                entity("hash:b", blobId = null, date = date),
                entity("boc:439900", blobId = "439900", date = date),
                entity("hash:a", blobId = null, date = date),
            ),
        )

        val order = dao.observeBySection("1").first().map { it.externalKey }
        assertEquals(3, order.size)
        assertEquals(order, dao.observeBySection("1").first().map { it.externalKey })
    }

    @Test
    fun `nothing removes a publication that stopped appearing in its source`() = runTest {
        dao.upsertAll(listOf(entity("boc:1"), entity("boc:2")))

        // A later sync only brings one of them back, as happens when the other falls out of the
        // hundred-item window. The stored count must not go down.
        dao.upsertAll(listOf(entity("boc:1")))

        assertEquals(2, dao.count())
    }

    @Test
    fun `an empty batch is a no-op rather than an error`() = runTest {
        assertEquals(UpsertCounts(), dao.upsertAll(emptyList()))
        assertEquals(0, dao.count())
        assertTrue(dao.observeTodaysBulletin().first().isEmpty())
    }

    @Test
    fun `one publication can be observed by its key`() = runTest {
        dao.upsertAll(listOf(entity("boc:1", title = "Aprobación definitiva")))

        assertEquals("Aprobación definitiva", dao.observePublication("boc:1").first()?.title)
    }

    @Test
    fun `a key that is not stored emits null, which is information and not a failure`() = runTest {
        dao.upsertAll(listOf(entity("boc:1")))

        assertNull(dao.observePublication("boc:desconocida").first())
    }

    @Test
    fun `the observed publication reflects a later correction`() = runTest {
        dao.upsertAll(listOf(entity("boc:1", title = "Título original")))

        // A synchronisation corrects the title while a detail screen is open.
        dao.upsertAll(listOf(entity("boc:1", title = "Título corregido")))

        assertEquals("Título corregido", dao.observePublication("boc:1").first()?.title)
    }

    @Test
    fun `the sync state remembers the last body hash and the last success`() = runTest {
        val stateDao = database.feedSyncStateDao()
        assertNull(stateDao.lastSuccessAt())

        stateDao.upsert(FeedSyncStateEntity("6802081", bodyHash = "abc", lastSuccessAt = 1_000))
        stateDao.upsert(FeedSyncStateEntity("6802085", bodyHash = "def", lastSuccessAt = 5_000))

        assertEquals("abc", stateDao.byFeedId("6802081")?.bodyHash)
        assertEquals(5_000L, stateDao.lastSuccessAt())
        assertEquals(2, stateDao.all().size)
    }

    // ---------- The searchable text ----------

    /**
     * The searchable text is derived from what the source publishes, so a corrected title has to
     * correct it too. Otherwise the announcement would stay findable only by its old wording.
     */
    @Test
    fun `a correction from the source updates the searchable text as well`() = runTest {
        dao.upsertAll(listOf(entity("boc:1", title = "Título viejo", searchText = "titulo viejo")))

        dao.upsertAll(listOf(entity("boc:1", title = "Título nuevo", searchText = "titulo nuevo")))

        assertEquals("titulo nuevo", dao.observePublication("boc:1").first()?.searchText)
    }

    /**
     * The other half of the same statement, and the one a review has to keep an eye on: the mark
     * belongs to the person and the synchronisation's allow-list does not name it.
     */
    @Test
    fun `updating the searchable text leaves the saved mark alone`() = runTest {
        dao.upsertAll(listOf(entity("boc:1", searchText = "viejo")))
        database.savedPublicationDao().setSavedAt("boc:1", 5_000L)

        dao.upsertAll(listOf(entity("boc:1", searchText = "nuevo")))

        val stored = dao.observePublication("boc:1").first()!!
        assertEquals("nuevo", stored.searchText)
        assertEquals(java.lang.Long.valueOf(5_000L), stored.savedAt)
    }

    @Test
    fun `only the rows with no searchable text are handed to the backfill`() = runTest {
        dao.upsertAll(
            listOf(
                entity("boc:1", searchText = ""),
                entity("boc:2", searchText = "ya tiene"),
                entity("boc:3", searchText = ""),
            ),
        )

        val pending = dao.withoutSearchText(limit = 10).map { it.externalKey }

        assertEquals(listOf("boc:1", "boc:3"), pending.sorted())
    }

    @Test
    fun `the backfill query respects its limit, which is what makes it a batch`() = runTest {
        dao.upsertAll((1..5).map { entity("boc:$it", searchText = "") })

        assertEquals(2, dao.withoutSearchText(limit = 2).size)
    }

    @Test
    fun `filling one row in takes it out of the pending set`() = runTest {
        dao.upsertAll(listOf(entity("boc:1", searchText = "")))

        dao.setSearchText("boc:1", "ayuntamiento de santona")

        assertTrue(dao.withoutSearchText(limit = 10).isEmpty())
        assertEquals("ayuntamiento de santona", dao.observePublication("boc:1").first()?.searchText)
    }

    private fun entity(
        externalKey: String,
        blobId: String? = externalKey.substringAfter(':'),
        title: String = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva.",
        sectionCode: String = "1",
        subsectionCode: String? = null,
        date: LocalDate = LocalDate.of(2026, 8, 27),
        seenAt: Long = 1_000,
        searchText: String = "ayuntamiento de pielagos aprobacion definitiva",
    ) = PublicationEntity(
        externalKey = externalKey,
        blobId = blobId,
        idSource = if (blobId == null) IdSource.CONTENT_HASH else IdSource.BLOB_ID,
        feedId = "6802081",
        sectionCode = sectionCode,
        subsectionCode = subsectionCode,
        title = title,
        issuer = "Ayuntamiento de Piélagos",
        organizationPath = listOf("Ayuntamiento de Piélagos"),
        editionType = EditionType.ORDINARY,
        publicationDate = date,
        documentUrl = "https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=${blobId ?: "0"}",
        rawCategories = "1.Disposiciones Generales|Ayuntamiento de Piélagos|ORD",
        warnings = setOf(ParserWarning.EDITION_TYPE_MISSING),
        firstSeenAt = seenAt,
        lastSeenAt = seenAt,
        searchText = searchText,
    )
}
