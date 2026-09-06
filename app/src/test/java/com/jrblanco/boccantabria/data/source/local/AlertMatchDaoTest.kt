package com.jrblanco.boccantabria.data.source.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
 * What the rules caught: one pair at most, news grouped by publication, and a counter that counts
 * publications rather than matches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class AlertMatchDaoTest {

    private lateinit var database: BocDatabase
    private lateinit var rules: AlertRuleDao
    private lateinit var matches: AlertMatchDao

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BocDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        rules = database.alertRuleDao()
        matches = database.alertMatchDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** FR-042: the unique index is the deduplication. */
    @Test
    fun `the same pair inserted twice is reported as ignored`() = runTest {
        rules.upsert(rule("r1"))

        val first = matches.insert(listOf(match("r1", "boc:1")))
        val second = matches.insert(listOf(match("r1", "boc:1"), match("r1", "boc:2")))

        assertTrue(first.single() >= 0)
        assertEquals(-1L, second[0])
        assertTrue(second[1] >= 0)
        assertEquals(2, matches.count())
    }

    @Test
    fun `news is one row per publication naming every rule`() = runTest {
        seedPublications("boc:1", "boc:2")
        rules.upsert(rule("r1", name = "Ganadería"))
        rules.upsert(rule("r2", name = "Rural"))
        matches.insert(
            listOf(match("r1", "boc:1", at = 5_000), match("r2", "boc:1", at = 7_000), match("r1", "boc:2", at = 9_000)),
        )

        val news = matches.observeNews().first()

        assertEquals(2, news.size)
        val first = news.single { it.publication.externalKey == "boc:1" }
        assertEquals(setOf("Ganadería", "Rural"), first.ruleNames.split(SEPARATOR).toSet())
        assertEquals(5_000L, first.detectedAt)
        assertEquals(1, first.unread)
    }

    @Test
    fun `the unread counter counts publications, not matches`() = runTest {
        rules.upsert(rule("r1"))
        rules.upsert(rule("r2"))
        matches.insert(listOf(match("r1", "boc:1"), match("r2", "boc:1"), match("r1", "boc:2")))

        assertEquals(2, matches.observeUnreadCount().first())
    }

    @Test
    fun `marking a publication read marks every one of its matches, once`() = runTest {
        seedPublications("boc:1")
        rules.upsert(rule("r1"))
        rules.upsert(rule("r2"))
        matches.insert(listOf(match("r1", "boc:1"), match("r2", "boc:1")))

        assertEquals(2, matches.markRead("boc:1", now = 9_000))
        assertEquals(0, matches.markRead("boc:1", now = 9_500))
        assertEquals(0, matches.observeUnreadCount().first())
        assertEquals(0, matches.observeNews().first().single().unread)
    }

    @Test
    fun `a publication without matches is zero rows, not an error`() = runTest {
        assertEquals(0, matches.markRead("boc:9", now = 1))
    }

    @Test
    fun `mark all read empties the counter`() = runTest {
        rules.upsert(rule("r1"))
        matches.insert(listOf(match("r1", "boc:1"), match("r1", "boc:2")))

        matches.markAllRead(now = 5)

        assertEquals(0, matches.observeUnreadCount().first())
    }

    @Test
    fun `news without a stored publication is not listed`() = runTest {
        rules.upsert(rule("r1"))
        matches.insert(listOf(match("r1", "boc:missing")))

        assertTrue(matches.observeNews().first().isEmpty())
    }

    private suspend fun seedPublications(vararg keys: String) {
        database.publicationDao().upsertAll(keys.map { publicationEntity(it) })
    }

    private fun rule(id: String, name: String = "Ganadería") = AlertRuleEntity(
        id = id,
        name = name,
        keywords = listOf("ganadería"),
        matchMode = "ANY",
        sectionCodes = emptyList(),
        organizationQuery = null,
        enabled = true,
        createdAt = 1_000,
        updatedAt = 1_000,
        activeSince = 1_000,
    )

    private fun match(ruleId: String, key: String, at: Long = 1_000) =
        AlertMatchEntity(ruleId = ruleId, externalKey = key, matchedAt = at)

    private fun publicationEntity(key: String) = PublicationEntity(
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
        documentUrl = "https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=${key.substringAfter(':')}",
        rawCategories = null,
        warnings = emptySet(),
        firstSeenAt = 1_000,
        lastSeenAt = 1_000,
        searchText = "ayuntamiento de pielagos aprobacion definitiva",
    )

    private companion object {
        /** The list converter's separator, which `GROUP_CONCAT` joins the names with. */
        const val SEPARATOR = "\u001F"
    }
}
