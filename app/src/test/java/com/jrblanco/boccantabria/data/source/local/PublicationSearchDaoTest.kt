package com.jrblanco.boccantabria.data.source.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jrblanco.boccantabria.core.util.SearchText
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.domain.model.EditionType
import com.jrblanco.boccantabria.domain.model.IdSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
 * What the search actually finds, against a real in-memory database.
 *
 * Faking the data-access object would mean reimplementing `LIKE` semantics in the test, and the two
 * copies would drift on exactly the cases that matter — accents and wildcards. This project never
 * fakes a DAO, and this is why.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class PublicationSearchDaoTest {

    private lateinit var database: BocDatabase
    private lateinit var dao: PublicationSearchDao

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BocDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.publicationSearchDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ---------- Accents and case ----------

    /** The search the whole feature exists for. */
    @Test
    fun `a query without accents finds a publication that has them`() = runTest {
        store(entity("boc:1", title = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva."))

        assertEquals(listOf("boc:1"), search("pielagos"))
    }

    @Test
    fun `a query with accents finds it too`() = runTest {
        store(entity("boc:1", title = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva."))

        assertEquals(listOf("boc:1"), search("Piélagos"))
    }

    @Test
    fun `the match can start in the middle of a word`() = runTest {
        store(entity("boc:1", title = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva."))

        assertEquals(listOf("boc:1"), search("ielagos"))
    }

    /**
     * The table stores `section_code`, never the name, so this only works because the name went
     * into the searchable text when the row was written.
     */
    @Test
    fun `a section can be found by its name even though the table stores a code`() = runTest {
        store(
            entity(
                "boc:1",
                title = "Anuncio de licitación",
                sectionCode = "3",
                searchText = "anuncio de licitacion contratacion administrativa",
            ),
        )

        assertEquals(listOf("boc:1"), search("contratacion"))
    }

    @Test
    fun `nothing matching is an empty list, which is an answer and not a failure`() = runTest {
        store(entity("boc:1"))

        assertEquals(emptyList<String>(), search("expropiacion"))
    }

    // ---------- Wildcards are characters ----------

    /**
     * Without the escaping this returns the whole archive, and it does it silently: no error, no
     * log, just a list that looks like the search is broken.
     */
    @Test
    fun `a percent sign in the query is a character and not a wildcard`() = runTest {
        store(entity("boc:1", searchText = "subvencion del 100% del importe"))
        store(entity("boc:2", searchText = "otra cosa cualquiera"))

        assertEquals(listOf("boc:1"), search("100%"))
    }

    @Test
    fun `an underscore in the query is a character too`() = runTest {
        store(entity("boc:1", searchText = "expediente a_b"))
        store(entity("boc:2", searchText = "expediente axb"))

        assertEquals(listOf("boc:1"), search("a_b"))
    }

    // ---------- Filters ----------

    @Test
    fun `a null filter narrows nothing`() = runTest {
        store(entity("boc:1", sectionCode = "1"))
        store(entity("boc:2", sectionCode = "3"))

        assertEquals(listOf("boc:2", "boc:1"), search("aprobacion"))
    }

    @Test
    fun `the section filter narrows to that section`() = runTest {
        store(entity("boc:1", sectionCode = "1"))
        store(entity("boc:2", sectionCode = "3"))

        assertEquals(listOf("boc:2"), search("aprobacion", sectionCode = "3"))
    }

    @Test
    fun `the subsection filter narrows further`() = runTest {
        store(entity("boc:1", sectionCode = "2", subsectionCode = "2.1"))
        store(entity("boc:2", sectionCode = "2", subsectionCode = "2.2"))

        assertEquals(listOf("boc:2"), search("aprobacion", subsectionCode = "2.2"))
    }

    @Test
    fun `the issuer filter narrows to that organisation`() = runTest {
        store(entity("boc:1", issuer = "Ayuntamiento de Piélagos"))
        store(entity("boc:2", issuer = "Gobierno de Cantabria"))

        assertEquals(listOf("boc:2"), search("aprobacion", issuer = "Gobierno de Cantabria"))
    }

    @Test
    fun `the date range includes both of its ends`() = runTest {
        store(entity("boc:1", date = LocalDate.of(2026, 8, 1)))
        store(entity("boc:2", date = LocalDate.of(2026, 8, 15)))
        store(entity("boc:3", date = LocalDate.of(2026, 8, 27)))

        val found = search("aprobacion", from = "2026-08-01", to = "2026-08-15")

        assertEquals(listOf("boc:2", "boc:1"), found)
    }

    @Test
    fun `an open end leaves that side unbounded`() = runTest {
        store(entity("boc:1", date = LocalDate.of(2026, 8, 1)))
        store(entity("boc:2", date = LocalDate.of(2026, 8, 27)))

        assertEquals(listOf("boc:2"), search("aprobacion", from = "2026-08-15"))
        assertEquals(listOf("boc:1"), search("aprobacion", to = "2026-08-15"))
    }

    @Test
    fun `filters combine, they do not replace each other`() = runTest {
        store(entity("boc:1", sectionCode = "3", issuer = "Gobierno de Cantabria", date = LocalDate.of(2026, 8, 1)))
        store(entity("boc:2", sectionCode = "3", issuer = "Ayuntamiento de Piélagos", date = LocalDate.of(2026, 8, 2)))
        store(entity("boc:3", sectionCode = "1", issuer = "Gobierno de Cantabria", date = LocalDate.of(2026, 8, 3)))

        val found = search("aprobacion", sectionCode = "3", issuer = "Gobierno de Cantabria")

        assertEquals(listOf("boc:1"), found)
    }

    // ---------- Order ----------

    @Test
    fun `newest first is newest first`() = runTest {
        store(entity("boc:1", date = LocalDate.of(2026, 8, 1)))
        store(entity("boc:2", date = LocalDate.of(2026, 8, 27)))

        assertEquals(listOf("boc:2", "boc:1"), search("aprobacion"))
    }

    @Test
    fun `oldest first is the same list turned around`() = runTest {
        store(entity("boc:1", date = LocalDate.of(2026, 8, 1)))
        store(entity("boc:2", date = LocalDate.of(2026, 8, 27)))

        assertEquals(listOf("boc:1", "boc:2"), search("aprobacion", newestFirst = false))
    }

    /** Same reason the bulletin queries carry three terms: the sources answer in any order. */
    @Test
    fun `the order is stable when the date repeats`() = runTest {
        val sameDay = LocalDate.of(2026, 8, 27)
        store(entity("boc:10", date = sameDay))
        store(entity("boc:2", date = sameDay))
        store(entity("boc:100", date = sameDay))

        assertEquals(listOf("boc:100", "boc:10", "boc:2"), search("aprobacion"))
        assertEquals(listOf("boc:2", "boc:10", "boc:100"), search("aprobacion", newestFirst = false))
    }

    @Test
    fun `the limit is respected, which is what makes the cap possible`() = runTest {
        (1..5).forEach { store(entity("boc:$it", date = LocalDate.of(2026, 8, it))) }

        assertEquals(3, search("aprobacion", limit = 3).size)
    }

    // ---------- Issuers ----------

    @Test
    fun `the issuers are the ones actually stored, without repeats and in order`() = runTest {
        store(entity("boc:1", issuer = "Gobierno de Cantabria"))
        store(entity("boc:2", issuer = "Ayuntamiento de Piélagos"))
        store(entity("boc:3", issuer = "Gobierno de Cantabria"))
        store(entity("boc:4", issuer = null))

        val issuers = dao.observeIssuers().first()

        assertEquals(listOf("Ayuntamiento de Piélagos", "Gobierno de Cantabria"), issuers)
        assertTrue(issuers.none { it.isBlank() })
    }

    // ---------- Helpers ----------

    @Suppress("LongParameterList")
    private suspend fun search(
        text: String,
        sectionCode: String? = null,
        subsectionCode: String? = null,
        issuer: String? = null,
        from: String? = null,
        to: String? = null,
        limit: Int = 100,
        newestFirst: Boolean = true,
    ): List<String> {
        val pattern = likeContains(SearchText.normalise(text))
        val flow = if (newestFirst) {
            dao.searchNewestFirst(pattern, sectionCode, subsectionCode, issuer, from, to, limit)
        } else {
            dao.searchOldestFirst(pattern, sectionCode, subsectionCode, issuer, from, to, limit)
        }
        return flow.first().map { it.externalKey }
    }

    private suspend fun store(entity: PublicationEntity) {
        database.publicationDao().insert(listOf(entity))
    }

    @Suppress("LongParameterList")
    private fun entity(
        externalKey: String,
        title: String = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva.",
        issuer: String? = "Ayuntamiento de Piélagos",
        sectionCode: String = "1",
        subsectionCode: String? = null,
        date: LocalDate = LocalDate.of(2026, 8, 27),
        searchText: String = buildSearchText(
            title = title,
            issuer = issuer,
            organizationPath = listOfNotNull(issuer),
            blobId = externalKey.substringAfter(':'),
            sectionName = null,
            subsectionName = null,
        ),
    ) = PublicationEntity(
        externalKey = externalKey,
        blobId = externalKey.substringAfter(':'),
        idSource = IdSource.BLOB_ID,
        feedId = "6802081",
        sectionCode = sectionCode,
        subsectionCode = subsectionCode,
        title = title,
        issuer = issuer,
        organizationPath = listOfNotNull(issuer),
        editionType = EditionType.ORDINARY,
        publicationDate = date,
        documentUrl = "https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=" +
            externalKey.substringAfter(':'),
        rawCategories = null,
        warnings = emptySet(),
        firstSeenAt = 1_000,
        lastSeenAt = 1_000,
        searchText = searchText,
    )
}
