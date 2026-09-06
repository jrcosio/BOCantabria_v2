package com.jrblanco.boccantabria.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.jrblanco.boccantabria.core.telemetry.NoOpCrashReporter
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.source.local.AlertMatchDao
import com.jrblanco.boccantabria.data.source.local.BocDatabase
import com.jrblanco.boccantabria.data.source.local.toEntity
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.domain.model.AlertMatch
import com.jrblanco.boccantabria.domain.model.AlertRuleDraft
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.KeywordMatchMode
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import com.jrblanco.boccantabria.fake.publication
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The alerts' repository against a real in-memory database: the instants it writes, the parents
 * it expands, what it delivers back from a batch of matches, and what it never tells analytics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class AlertRepositoryImplTest {

    private lateinit var database: BocDatabase
    private val analytics = RecordingAnalyticsTracker()
    private var now: Long = 1_000L

    private val draft = AlertRuleDraft(name = " Ganadería ", keywords = listOf("ganadería"), sectionCodes = setOf("2"), organizationQuery = "Piélagos")

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

    @Test
    fun `creating writes the instants, expands the parent and trims the name`() = runTest {
        val id = (repository().save(draft, id = null) as AppResult.Success).data

        val stored = repository().rule(id)!!
        assertEquals("Ganadería", stored.name)
        assertEquals(setOf("2.1", "2.2", "2.3"), stored.sectionCodes)
        assertEquals("Piélagos", stored.organizationQuery)
        assertEquals(1_000L, stored.createdAt)
        assertEquals(1_000L, stored.activeSince)
    }

    @Test
    fun `editing keeps the id and the creation instant and renews active since`() = runTest {
        val repository = repository()
        val id = (repository.save(draft, id = null) as AppResult.Success).data
        now = 5_000L

        val result = repository.save(draft.copy(matchMode = KeywordMatchMode.ALL), id = id)

        assertEquals(AppResult.Success(id), result)
        val stored = repository.rule(id)!!
        assertEquals(KeywordMatchMode.ALL, stored.matchMode)
        assertEquals(1_000L, stored.createdAt)
        assertEquals(5_000L, stored.activeSince)
        assertEquals(1, repository.countRules())
    }

    @Test
    fun `two creations get two different ids`() = runTest {
        val repository = repository()

        val a = (repository.save(draft, null) as AppResult.Success).data
        val b = (repository.save(draft, null) as AppResult.Success).data

        assertNotEquals(a, b)
    }

    @Test
    fun `re-enabling renews active since`() = runTest {
        val repository = repository()
        val id = (repository.save(draft, null) as AppResult.Success).data
        now = 3_000L
        repository.setEnabled(id, enabled = false)
        now = 7_000L

        repository.setEnabled(id, enabled = true)

        assertEquals(7_000L, repository.rule(id)!!.activeSince)
        assertEquals(1, repository.countEnabled())
    }

    @Test
    fun `record matches returns only what was really new`() = runTest {
        val repository = repository()
        val id = (repository.save(draft, null) as AppResult.Success).data

        val first = repository.recordMatches(listOf(AlertMatch(id, "boc:1", 1L), AlertMatch(id, "boc:1", 1L)))
        val second = repository.recordMatches(listOf(AlertMatch(id, "boc:1", 2L), AlertMatch(id, "boc:2", 2L)))

        assertEquals(listOf("boc:1"), (first as AppResult.Success).data.map { it.externalKey })
        assertEquals(listOf("boc:2"), (second as AppResult.Success).data.map { it.externalKey })
    }

    /**
     * Feature 014 (STAB-003). The chunked loop of 900 was what broke the atomicity: chunk one
     * committed, chunk two threw, and the pairs of chunk one were recorded but never delivered — and
     * the unique index then hid them for ever. One insert is one transaction: all or nothing, and a
     * failure is a failure, not "nothing new".
     */
    @Test
    fun `a batch that cannot be recorded whole records nothing and reports the failure`() = runTest {
        val repository = repository()
        val id = (repository.save(draft, null) as AppResult.Success).data
        // 900 valid pairs, then one for a rule that does not exist: `INSERT OR IGNORE` does not ignore
        // a foreign-key violation, so the last row makes the whole statement fail.
        val batch = (1..900).map { AlertMatch(id, "boc:$it", 1L) } + AlertMatch("ghost-rule", "boc:901", 1L)

        val result = repository.recordMatches(batch)

        assertTrue("debía ser Failure, fue: $result", result is AppResult.Failure)
        assertEquals(0, database.alertMatchDao().count())
    }

    @Test
    fun `news and the unread counter follow the matches`() = runTest {
        database.publicationDao().upsertAll(listOf(publication("boc:1").toEntity(1L, "x")))
        val repository = repository()
        val id = (repository.save(draft, null) as AppResult.Success).data
        repository.recordMatches(listOf(AlertMatch(id, "boc:1", 1L)))

        assertEquals(1, repository.observeUnreadCount().first())
        val news = repository.observeNews().first().single()
        assertEquals(listOf("Ganadería"), news.ruleNames)
        assertFalse(news.isRead)

        repository.markRead("boc:1")

        assertEquals(0, repository.observeUnreadCount().first())
        assertTrue(repository.observeNews().first().single().isRead)
    }

    @Test
    fun `deleting removes the rule and its matches`() = runTest {
        val repository = repository()
        val id = (repository.save(draft, null) as AppResult.Success).data
        repository.recordMatches(listOf(AlertMatch(id, "boc:1", 1L)))

        repository.delete(id)

        assertNull(repository.rule(id))
        assertEquals(0, repository.observeUnreadCount().first())
    }

    @Test
    fun `an unknown key marked read is a success`() = runTest {
        assertEquals(AppResult.Success(Unit), repository().markRead("boc:none"))
    }

    /** FR-069: counts and enumerations, never what the person follows. */
    @Test
    fun `analytics never carries the name, the words or the organisation`() = runTest {
        val repository = repository()
        val id = (repository.save(draft, null) as AppResult.Success).data
        repository.setEnabled(id, false)
        repository.recordMatches(listOf(AlertMatch(id, "boc:1", 1L)))
        repository.markAllRead()
        repository.delete(id)

        val everything = analytics.events.joinToString { "${it.name}${it.parameters}" }
        assertFalse(everything.contains("Ganadería"))
        assertFalse(everything.contains("ganadería"))
        assertFalse(everything.contains("Piélagos"))
        assertFalse(everything.contains("boc:1"))
        assertEquals(
            listOf("alert_rule_saved", "alert_rule_toggled", "alert_matches", "alert_read", "alert_rule_deleted"),
            analytics.events.map { it.name },
        )
        assertEquals("1", analytics.events.first().parameters["keywords"])
        assertEquals("3", analytics.events.first().parameters["sections"])
        assertEquals("true", analytics.events.first().parameters["has_organization"])
    }

    /**
     * Feature 014 (STAB-004). The bell's badge is the one flow of the application that is never
     * re-subscribed: `MainShellViewModel` lives for the whole session. One transient read failure
     * used to mean a badge at zero for the rest of the process.
     */
    @Test
    fun `the unread count survives a read failure`() = runTest {
        val repository = repository()
        val id = (repository.save(draft, null) as AppResult.Success).data
        repository.recordMatches(listOf(AlertMatch(id, "boc:1", 1L)))
        val real = database.alertMatchDao()
        var attempts = 0
        val flaky = mockk<AlertMatchDao>()
        every { flaky.observeUnreadCount() } returns flow {
            if (attempts++ == 0) throw IllegalStateException("base ocupada")
            emitAll(real.observeUnreadCount())
        }

        repository(matchDao = flaky).observeUnreadCount().test {
            assertEquals(0, awaitItem())
            assertEquals(1, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun repository(matchDao: AlertMatchDao = database.alertMatchDao()) = AlertRepositoryImpl(
        ruleDao = database.alertRuleDao(),
        matchDao = matchDao,
        sections = BocSectionRepositoryImpl(),
        time = object : TimeProvider { override fun nowMillis(): Long = now },
        dispatchers = TestDispatcherProvider(),
        analytics = analytics,
        crashReporter = NoOpCrashReporter(),
    )
}
