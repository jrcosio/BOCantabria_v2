package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.data.source.local.PublicationEntity
import com.jrblanco.boccantabria.data.source.local.PublicationSearchDao
import com.jrblanco.boccantabria.domain.model.EditionType
import com.jrblanco.boccantabria.domain.model.IdSource
import com.jrblanco.boccantabria.domain.model.SearchQuery
import com.jrblanco.boccantabria.domain.model.SearchSort
import com.jrblanco.boccantabria.fake.RecordingCrashReporter
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The translation between a search and a statement.
 *
 * What the statements themselves do is checked against a real database in `PublicationSearchDaoTest`.
 * What is checked here is the part that lives above them: which statement gets picked, what pattern
 * it is handed, and that a read that goes wrong leaves the screen with an answer instead of nothing.
 */
class SearchRepositoryImplTest {

    private val dao = RecordingSearchDao()
    private val crashReporter = RecordingCrashReporter()

    private val repository = SearchRepositoryImpl(
        searchDao = dao,
        dispatchers = TestDispatcherProvider(),
        crashReporter = crashReporter,
    )

    @Test
    fun `the pattern is normalised and wrapped so it matches anywhere`() = runTest {
        repository.search(SearchQuery(text = "  PIÉLAGOS "), limit = 10).first()

        assertEquals("%pielagos%", dao.lastPattern)
    }

    /** Without this, `100%` comes back with the whole archive and looks like a broken search. */
    @Test
    fun `what SQL would read as a wildcard is escaped`() = runTest {
        repository.search(SearchQuery(text = "100%"), limit = 10).first()

        assertEquals("%100\\%%", dao.lastPattern)
    }

    @Test
    fun `the newest-first statement is the one used by default`() = runTest {
        repository.search(SearchQuery(text = "pielagos"), limit = 10).first()

        assertEquals(listOf("newest"), dao.calls)
    }

    @Test
    fun `choosing the oldest first picks the other statement`() = runTest {
        repository.search(
            SearchQuery(text = "pielagos", sort = SearchSort.OLDEST_FIRST),
            limit = 10,
        ).first()

        assertEquals(listOf("oldest"), dao.calls)
    }

    @Test
    fun `the filters travel as they are, and dates as ISO text`() = runTest {
        val query = SearchQuery(
            text = "pielagos",
            from = LocalDate.of(2026, 1, 1),
            to = LocalDate.of(2026, 8, 27),
            sectionCode = "3",
            subsectionCode = "3.1",
            issuer = "Gobierno de Cantabria",
        )

        repository.search(query, limit = 25).first()

        assertEquals("3", dao.lastSectionCode)
        assertEquals("3.1", dao.lastSubsectionCode)
        assertEquals("Gobierno de Cantabria", dao.lastIssuer)
        assertEquals("2026-01-01", dao.lastFrom)
        assertEquals("2026-08-27", dao.lastTo)
        assertEquals(25, dao.lastLimit)
    }

    @Test
    fun `a filter nobody set travels as null, which narrows nothing`() = runTest {
        repository.search(SearchQuery(text = "pielagos"), limit = 10).first()

        assertEquals(null, dao.lastSectionCode)
        assertEquals(null, dao.lastIssuer)
        assertEquals(null, dao.lastFrom)
    }

    /** The stored entity must not cross into the screen. */
    @Test
    fun `rows come back as domain models`() = runTest {
        dao.rows = listOf(entity("boc:1"))

        val found = repository.search(SearchQuery(text = "pielagos"), limit = 10).first()

        assertEquals(listOf("boc:1"), found.map { it.externalKey })
        assertEquals("AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva.", found.single().title)
    }

    @Test
    fun `nothing found is an empty list and not a failure`() = runTest {
        dao.rows = emptyList()

        assertEquals(emptyList<String>(), repository.search(SearchQuery(text = "nada"), limit = 10).first().map { it.externalKey })
        assertTrue(crashReporter.nonFatals.isEmpty())
    }

    /**
     * A screen with no state at all reads as a frozen application. So the flow survives, the
     * failure is recorded where somebody can see it, and the screen gets an empty answer.
     */
    @Test
    fun `a read failure is recorded and emits empty instead of terminating the flow`() = runTest {
        dao.failWith = IllegalStateException("la base de datos está corrupta")

        val found = repository.search(SearchQuery(text = "pielagos"), limit = 10).first()

        assertEquals(emptyList<String>(), found.map { it.externalKey })
        assertEquals(1, crashReporter.nonFatals.size)
    }

    @Test
    fun `the issuers come straight through`() = runTest {
        dao.issuers = listOf("Ayuntamiento de Piélagos", "Gobierno de Cantabria")

        assertEquals(dao.issuers, repository.observeIssuers().first())
    }

    @Test
    fun `a failure reading the issuers is an empty list, not a broken sheet`() = runTest {
        dao.failWith = IllegalStateException("no se puede leer")

        assertEquals(emptyList<String>(), repository.observeIssuers().first())
        assertEquals(1, crashReporter.nonFatals.size)
    }

    private fun entity(key: String) = PublicationEntity(
        externalKey = key,
        blobId = key.substringAfter(':'),
        idSource = IdSource.BLOB_ID,
        feedId = "6802081",
        sectionCode = "1",
        subsectionCode = null,
        title = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva.",
        issuer = "Ayuntamiento de Piélagos",
        organizationPath = listOf("Ayuntamiento de Piélagos"),
        editionType = EditionType.ORDINARY,
        publicationDate = LocalDate.of(2026, 8, 27),
        documentUrl = "https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=1",
        rawCategories = null,
        warnings = emptySet(),
        firstSeenAt = 1_000,
        lastSeenAt = 1_000,
        searchText = "ayuntamiento de pielagos aprobacion definitiva",
    )

    /** Records what it was asked, which is the whole point of this test class. */
    private class RecordingSearchDao : PublicationSearchDao {
        var rows: List<PublicationEntity> = emptyList()
        var issuers: List<String> = emptyList()
        var failWith: Throwable? = null

        val calls: MutableList<String> = mutableListOf()
        var lastPattern: String? = null
        var lastSectionCode: String? = null
        var lastSubsectionCode: String? = null
        var lastIssuer: String? = null
        var lastFrom: String? = null
        var lastTo: String? = null
        var lastLimit: Int = 0

        override fun searchNewestFirst(
            pattern: String,
            sectionCode: String?,
            subsectionCode: String?,
            issuer: String?,
            from: String?,
            to: String?,
            limit: Int,
        ): Flow<List<PublicationEntity>> =
            record("newest", pattern, sectionCode, subsectionCode, issuer, from, to, limit)

        override fun searchOldestFirst(
            pattern: String,
            sectionCode: String?,
            subsectionCode: String?,
            issuer: String?,
            from: String?,
            to: String?,
            limit: Int,
        ): Flow<List<PublicationEntity>> =
            record("oldest", pattern, sectionCode, subsectionCode, issuer, from, to, limit)

        override fun observeIssuers(): Flow<List<String>> =
            failWith?.let { cause -> flow<List<String>> { throw cause } } ?: flowOf(issuers)

        @Suppress("LongParameterList")
        private fun record(
            statement: String,
            pattern: String,
            sectionCode: String?,
            subsectionCode: String?,
            issuer: String?,
            from: String?,
            to: String?,
            limit: Int,
        ): Flow<List<PublicationEntity>> {
            calls += statement
            lastPattern = pattern
            lastSectionCode = sectionCode
            lastSubsectionCode = subsectionCode
            lastIssuer = issuer
            lastFrom = from
            lastTo = to
            lastLimit = limit
            return failWith?.let { cause -> flow { throw cause } } ?: flowOf(rows)
        }
    }
}
