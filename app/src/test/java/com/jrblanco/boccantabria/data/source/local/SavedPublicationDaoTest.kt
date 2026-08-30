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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The rules that make a saved publication trustworthy: the order is the one the person created, the
 * mark survives every synchronisation, and taking it off does not remove anything.
 *
 * Runs under Robolectric against a real in-memory database, like every other data test here. Faking
 * the data-access object would mean reimplementing the very statements under test, and the two
 * copies would drift.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class SavedPublicationDaoTest {

    private lateinit var database: BocDatabase
    private lateinit var publications: PublicationDao
    private lateinit var saved: SavedPublicationDao

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BocDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        publications = database.publicationDao()
        saved = database.savedPublicationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `nothing is saved to begin with`() = runTest {
        publications.upsertAll(listOf(entity("boc:1"), entity("boc:2")))

        assertEquals(emptyList<PublicationEntity>(), saved.observeSaved().first())
        assertEquals(emptyList<String>(), saved.observeSavedKeys().first())
    }

    @Test
    fun `marking a publication makes it appear among the saved ones`() = runTest {
        publications.upsertAll(listOf(entity("boc:1"), entity("boc:2")))

        saved.setSavedAt("boc:1", 5_000L)

        assertEquals(listOf("boc:1"), saved.observeSavedKeys().first())
        assertEquals("boc:1", saved.observeSaved().first().single().externalKey)
    }

    /** FR-011: the list is ordered by when the person saved it, most recent first. */
    @Test
    fun `the most recently saved comes first, whatever the bulletin order was`() = runTest {
        publications.upsertAll(
            listOf(
                entity("boc:1", date = LocalDate.of(2026, 8, 27)),
                entity("boc:2", date = LocalDate.of(2026, 8, 20)),
                entity("boc:3", date = LocalDate.of(2026, 8, 25)),
            ),
        )

        saved.setSavedAt("boc:1", 1_000L)
        saved.setSavedAt("boc:2", 3_000L)
        saved.setSavedAt("boc:3", 2_000L)

        assertEquals(
            listOf("boc:2", "boc:3", "boc:1"),
            saved.observeSaved().first().map { it.externalKey },
        )
    }

    /**
     * Two marks in the same millisecond would tie, and without a tie-breaker the order could differ
     * between two reads. Same reason the three bulletin queries carry three terms.
     */
    @Test
    fun `two marks in the same instant still come out in a stable order`() = runTest {
        publications.upsertAll(listOf(entity("boc:1"), entity("boc:2"), entity("boc:3")))

        saved.setSavedAt("boc:1", 7_000L)
        saved.setSavedAt("boc:2", 7_000L)
        saved.setSavedAt("boc:3", 7_000L)

        val firstRead = saved.observeSaved().first().map { it.externalKey }
        val secondRead = saved.observeSaved().first().map { it.externalKey }

        assertEquals(listOf("boc:3", "boc:2", "boc:1"), firstRead)
        assertEquals(firstRead, secondRead)
    }

    /** FR-021: taking the mark off retires the mark. The publication stays where it was. */
    @Test
    fun `unsaving clears the mark and never removes the publication`() = runTest {
        publications.upsertAll(listOf(entity("boc:1")))
        saved.setSavedAt("boc:1", 5_000L)

        saved.setSavedAt("boc:1", null)

        assertEquals(emptyList<String>(), saved.observeSavedKeys().first())
        assertEquals(1, publications.count())
        assertNull(publications.observePublication("boc:1").first()!!.savedAt)
    }

    @Test
    fun `marking a key that is not stored touches no rows and does not fail`() = runTest {
        val affected = saved.setSavedAt("boc:missing", 5_000L)

        assertEquals(0, affected)
        assertEquals(emptyList<String>(), saved.observeSavedKeys().first())
    }

    /**
     * **The regression that matters** (FR-020, SC-004).
     *
     * A source only publishes its last hundred announcements, so the same publication comes back on
     * every synchronisation. The mark has to survive that, and it does because `saved_at` is not in
     * `PublicationDao.updateColumns`. If somebody adds it there, this test is what goes red.
     */
    @Test
    fun `a synchronisation that sees the publication again does not clear its mark`() = runTest {
        publications.upsertAll(listOf(entity("boc:1", title = "Título original", seenAt = 1_000)))
        saved.setSavedAt("boc:1", 5_000L)

        publications.upsertAll(listOf(entity("boc:1", title = "Título corregido", seenAt = 9_000)))

        val stored = publications.observePublication("boc:1").first()
        assertNotNull(stored)
        assertEquals("Título corregido", stored!!.title)
        assertEquals(9_000, stored.lastSeenAt)
        assertEquals(5_000L, stored.savedAt)
        assertEquals(listOf("boc:1"), saved.observeSavedKeys().first())
    }

    /** An insertion coming from the source cannot invent a mark. */
    @Test
    fun `a publication arriving from the source starts out unsaved`() = runTest {
        publications.upsertAll(listOf(entity("boc:1")))

        assertNull(publications.observePublication("boc:1").first()!!.savedAt)
    }

    private fun entity(
        externalKey: String,
        blobId: String? = externalKey.substringAfter(':'),
        title: String = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva.",
        sectionCode: String = "1",
        date: LocalDate = LocalDate.of(2026, 8, 27),
        seenAt: Long = 1_000,
    ) = PublicationEntity(
        externalKey = externalKey,
        blobId = blobId,
        idSource = if (blobId == null) IdSource.CONTENT_HASH else IdSource.BLOB_ID,
        feedId = "6802081",
        sectionCode = sectionCode,
        subsectionCode = null,
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
    )
}
