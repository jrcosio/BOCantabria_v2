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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The rules' store, and the project's only delete.
 *
 * The last test is the regression the new doctrine rests on: deleting a rule takes its matches and
 * leaves `publications` with exactly the rows it had (012 research.md D-412).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class AlertRuleDaoTest {

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

    @Test
    fun `a rule round-trips through the store`() = runTest {
        rules.upsert(rule("r1", keywords = listOf("ganadería", "medio rural"), sectionCodes = listOf("2.1", "6")))

        val stored = rules.byId("r1")!!
        assertEquals(listOf("ganadería", "medio rural"), stored.keywords)
        assertEquals(listOf("2.1", "6"), stored.sectionCodes)
        assertEquals("ANY", stored.matchMode)
    }

    @Test
    fun `the overview carries the last match and today's count`() = runTest {
        rules.upsert(rule("r1"))
        matches.insert(
            listOf(match("r1", "boc:1", at = 5_000), match("r1", "boc:2", at = 9_000), match("r1", "boc:3", at = 12_000)),
        )

        val overview = rules.observeRules(dayStart = 8_000).first().single()

        assertEquals(java.lang.Long.valueOf(12_000L), overview.lastMatchedAt)
        assertEquals(2, overview.matchesToday)
    }

    @Test
    fun `a rule without matches has no last match and zero today`() = runTest {
        rules.upsert(rule("r1"))

        val overview = rules.observeRules(dayStart = 0).first().single()

        assertNull(overview.lastMatchedAt)
        assertEquals(0, overview.matchesToday)
    }

    @Test
    fun `rules come newest first`() = runTest {
        rules.upsert(rule("old", createdAt = 1_000))
        rules.upsert(rule("new", createdAt = 2_000))

        assertEquals(listOf("new", "old"), rules.observeRules(0).first().map { it.rule.id })
    }

    @Test
    fun `only enabled rules are handed to the cycle`() = runTest {
        rules.upsert(rule("on", enabled = true))
        rules.upsert(rule("off", enabled = false))

        assertEquals(listOf("on"), rules.enabledRules().map { it.id })
        assertEquals(2, rules.count())
        assertEquals(1, rules.countEnabled())
    }

    @Test
    fun `pausing and re-enabling renew active since`() = runTest {
        rules.upsert(rule("r1", activeSince = 1_000))

        rules.setEnabled("r1", enabled = false, now = 5_000)
        assertEquals(5_000L, rules.byId("r1")!!.activeSince)
        assertEquals(false, rules.byId("r1")!!.enabled)

        rules.setEnabled("r1", enabled = true, now = 9_000)
        assertEquals(9_000L, rules.byId("r1")!!.activeSince)
        assertEquals(9_000L, rules.byId("r1")!!.updatedAt)
    }

    /** The regression of the doctrine: the only delete of the project never reaches a publication. */
    @Test
    fun `deleting a rule cascades to its matches and leaves publications untouched`() = runTest {
        database.publicationDao().upsertAll(listOf(publicationEntity("boc:1"), publicationEntity("boc:2")))
        rules.upsert(rule("r1"))
        rules.upsert(rule("r2"))
        matches.insert(listOf(match("r1", "boc:1"), match("r1", "boc:2"), match("r2", "boc:1")))

        val deleted = rules.delete("r1")

        assertEquals(1, deleted)
        assertNull(rules.byId("r1"))
        assertEquals(1, matches.count())
        assertEquals(2, database.publicationDao().count())
        assertTrue(database.publicationDao().observePublication("boc:1").first() != null)
    }

    private fun rule(
        id: String,
        keywords: List<String> = listOf("ganadería"),
        sectionCodes: List<String> = emptyList(),
        enabled: Boolean = true,
        createdAt: Long = 1_000,
        activeSince: Long = createdAt,
    ) = AlertRuleEntity(
        id = id,
        name = "Ganadería",
        keywords = keywords,
        matchMode = "ANY",
        sectionCodes = sectionCodes,
        organizationQuery = null,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = createdAt,
        activeSince = activeSince,
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
}
