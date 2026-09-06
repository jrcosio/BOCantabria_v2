package com.jrblanco.boccantabria.integration

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jrblanco.boccantabria.core.telemetry.NoOpCrashReporter
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.repository.AlertRepositoryImpl
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.data.repository.InMemoryInAppAlertStore
import com.jrblanco.boccantabria.data.repository.PublicationRepositoryImpl
import com.jrblanco.boccantabria.data.source.local.AlertMatchDao
import com.jrblanco.boccantabria.data.source.local.BocDatabase
import com.jrblanco.boccantabria.data.source.remote.BocFeedCatalog
import com.jrblanco.boccantabria.data.source.remote.FeedFailure
import com.jrblanco.boccantabria.data.source.remote.FeedFetchResult
import com.jrblanco.boccantabria.data.source.remote.PublicationNormalizer
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.domain.model.AlertDelivery
import com.jrblanco.boccantabria.domain.model.AlertRuleDraft
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.SyncCycleOutcome
import com.jrblanco.boccantabria.domain.usecase.DeleteAlertRuleUseCase
import com.jrblanco.boccantabria.domain.usecase.MarkAlertReadUseCase
import com.jrblanco.boccantabria.domain.usecase.MatchAlertRuleUseCase
import com.jrblanco.boccantabria.domain.usecase.ReconcileBackgroundSyncUseCase
import com.jrblanco.boccantabria.domain.usecase.RefreshPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.ReleaseUnusedDocumentsUseCase
import com.jrblanco.boccantabria.domain.usecase.RunSyncCycleUseCase
import com.jrblanco.boccantabria.domain.usecase.SaveAlertRuleUseCase
import com.jrblanco.boccantabria.domain.usecase.SetAlertRuleEnabledUseCase
import com.jrblanco.boccantabria.fake.FakeAppVisibilityProvider
import com.jrblanco.boccantabria.fake.FakeBackgroundSyncScheduler
import com.jrblanco.boccantabria.fake.FailingOnceAlertMatchDao
import com.jrblanco.boccantabria.fake.FakeDocumentRepository
import com.jrblanco.boccantabria.fake.FakePublicationRemoteDataSource
import com.jrblanco.boccantabria.fake.RecordingAlertNotifier
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import com.jrblanco.boccantabria.fake.RecordingCrashReporter
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import com.jrblanco.boccantabria.fake.rssItem
import kotlinx.coroutines.flow.first
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
 * The whole alerts chain — source → synchronisation → matches → delivery — with the real
 * repositories on a real in-memory database. Only the frontier is faked: the RSS source, the
 * notification manager, the process visibility and WorkManager.
 *
 * These are the nine cycle cases of §24 of the functional document, plus the management ones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class AlertFlowIntegrationTest {

    private lateinit var database: BocDatabase
    private val remote = FakePublicationRemoteDataSource()
    private val notifier = RecordingAlertNotifier()
    private val inApp = InMemoryInAppAlertStore()
    private val visibility = FakeAppVisibilityProvider(visible = false)
    private val scheduler = FakeBackgroundSyncScheduler()
    private val crashReporter = RecordingCrashReporter()
    private var now = 1_000_000L
    private val time = object : TimeProvider { override fun nowMillis() = now }

    private lateinit var publications: PublicationRepositoryImpl
    private lateinit var alerts: AlertRepositoryImpl
    private lateinit var cycle: RunSyncCycleUseCase
    private lateinit var saveRule: SaveAlertRuleUseCase
    private lateinit var setEnabled: SetAlertRuleEnabledUseCase
    private lateinit var deleteRule: DeleteAlertRuleUseCase

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BocDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        build()
    }

    /**
     * Wires the real repositories and the cycle over the database. [matchDao] can be wrapped to make
     * the match store fail on demand, which is how the recovery of feature 014 is exercised.
     */
    private fun build(matchDao: AlertMatchDao = database.alertMatchDao()) {
        val sections = BocSectionRepositoryImpl()
        val dispatchers = TestDispatcherProvider()
        publications = PublicationRepositoryImpl(
            remoteDataSource = remote,
            publicationDao = database.publicationDao(),
            feedSyncStateDao = database.feedSyncStateDao(),
            normalizer = PublicationNormalizer(),
            sectionRepository = sections,
            feeds = BocFeedCatalog.definitions,
            time = time,
            dispatchers = dispatchers,
            analytics = RecordingAnalyticsTracker(),
            crashReporter = NoOpCrashReporter(),
        )
        alerts = AlertRepositoryImpl(
            ruleDao = database.alertRuleDao(),
            matchDao = matchDao,
            sections = sections,
            time = time,
            dispatchers = dispatchers,
            analytics = RecordingAnalyticsTracker(),
            crashReporter = NoOpCrashReporter(),
        )
        cycle = RunSyncCycleUseCase(
            refreshPublications = RefreshPublicationsUseCase(publications),
            publications = publications,
            alerts = alerts,
            matchRule = MatchAlertRuleUseCase(sections),
            notifier = notifier,
            inAppAlerts = inApp,
            appVisibility = visibility,
            releaseUnusedDocuments = ReleaseUnusedDocumentsUseCase(FakeDocumentRepository()),
            time = time,
            crashReporter = crashReporter,
        )
        val reconcile = ReconcileBackgroundSyncUseCase(alerts, scheduler)
        saveRule = SaveAlertRuleUseCase(alerts, reconcile)
        setEnabled = SetAlertRuleEnabledUseCase(alerts, reconcile)
        deleteRule = DeleteAlertRuleUseCase(alerts, reconcile)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun rule(name: String, vararg keywords: String, enabled: Boolean = true): String =
        (saveRule(AlertRuleDraft(name = name, keywords = keywords.toList(), isEnabled = enabled), id = null) as AppResult.Success).data

    private suspend fun run(): SyncCycleOutcome = (cycle(force = true) as AppResult.Success).data

    private fun bulletin(hash: String, vararg titles: Pair<String, String>) {
        remote.respondWithItems("6802081", hash, *titles.map { (id, title) -> rssItem(id, title = title) }.toTypedArray())
    }

    // ---------- §24: the cycle ----------

    @Test
    fun `the first synchronisation is a baseline and notifies nothing, whatever the rules`() = runTest {
        rule("Ganadería", "ganadería")
        bulletin("h1", "1" to "Ayudas a la ganadería.", "2" to "Más ganadería.")

        val outcome = run()

        assertTrue(outcome.summary.isBaseline)
        assertEquals(AlertDelivery.NONE, outcome.delivery)
        assertTrue(notifier.posted.isEmpty())
        assertEquals(0, alerts.observeUnreadCount().first())
        assertTrue(crashReporter.messages.any { it.startsWith("cycle: baseline") })
    }

    @Test
    fun `a second synchronisation without changes notifies nothing`() = runTest {
        rule("Ganadería", "ganadería")
        bulletin("h1", "1" to "Ayudas a la ganadería.")
        run()

        val outcome = run()

        assertEquals(AlertDelivery.NONE, outcome.delivery)
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `a new item that matches is one notification, one piece of news, one on the badge`() = runTest {
        rule("Ganadería", "ganadería")
        bulletin("h1", "1" to "Aprobación definitiva.")
        run()

        bulletin("h2", "1" to "Aprobación definitiva.", "2" to "Ayudas a la ganadería extensiva.")
        val outcome = run()

        assertEquals(AlertDelivery.SYSTEM, outcome.delivery)
        val notification = notifier.posted.single().single()
        assertEquals("boc:2", notification.publication.externalKey)
        assertEquals(listOf("Ganadería"), notification.ruleNames)
        assertEquals(1, alerts.observeUnreadCount().first())
        val news = alerts.observeNews().first().single()
        assertEquals("boc:2", news.publication.externalKey)
        assertFalse(news.isRead)
    }

    @Test
    fun `a new item that does not match is silent`() = runTest {
        rule("Ganadería", "ganadería")
        bulletin("h1", "1" to "Aprobación definitiva.")
        run()

        bulletin("h2", "1" to "Aprobación definitiva.", "2" to "Ayudas a la pesca.")
        val outcome = run()

        assertEquals(AlertDelivery.NONE, outcome.delivery)
        assertTrue(notifier.posted.isEmpty())
        assertEquals(0, alerts.observeUnreadCount().first())
    }

    @Test
    fun `the same item received again is not notified twice`() = runTest {
        rule("Ganadería", "ganadería")
        bulletin("h1", "1" to "Aprobación definitiva.")
        run()
        bulletin("h2", "1" to "Aprobación definitiva.", "2" to "Ayudas a la ganadería.")
        run()

        // The source republishes the same items under a new hash: nothing is new for the store.
        bulletin("h3", "2" to "Ayudas a la ganadería.", "1" to "Aprobación definitiva.")
        val outcome = run()

        assertEquals(AlertDelivery.NONE, outcome.delivery)
        assertEquals(1, notifier.posted.size)
        assertEquals(1, alerts.observeUnreadCount().first())
    }

    @Test
    fun `an item matching two rules is one notification naming both and one on the badge`() = runTest {
        rule("Ganadería", "ganadería")
        rule("Subvenciones rurales", "ayudas")
        bulletin("h1", "1" to "Aprobación definitiva.")
        run()

        bulletin("h2", "1" to "Aprobación definitiva.", "2" to "Ayudas a la ganadería.")
        val outcome = run()

        val notification = notifier.posted.single().single()
        assertEquals(setOf("Ganadería", "Subvenciones rurales"), notification.ruleNames.toSet())
        assertEquals(1, alerts.observeUnreadCount().first())
        assertEquals(setOf("Ganadería", "Subvenciones rurales"), alerts.observeNews().first().single().ruleNames.toSet())
        assertEquals(1, outcome.notifications.size)
    }

    @Test
    fun `a partial synchronisation notifies only what the sources that answered brought`() = runTest {
        rule("Todo", "aprobación")
        bulletin("h1", "1" to "Aprobación definitiva.")
        remote.respondWithItems("6802085", "g1", rssItem("10", title = "Aprobación de bases."))
        run()

        remote.respondWith("6802081", FeedFetchResult.Failed(FeedFailure.SERVER_ERROR))
        remote.respondWithItems("6802085", "g2", rssItem("10", title = "Aprobación de bases."), rssItem("11", title = "Aprobación de otra cosa."))
        val outcome = run()

        assertEquals(1, outcome.summary.failedFeeds)
        assertEquals(listOf("boc:11"), notifier.posted.single().map { it.publication.externalKey })
    }

    // ---------- §24: editing and re-enabling never look back ----------

    @Test
    fun `editing a rule does not fire for what is already stored`() = runTest {
        val id = rule("Ganadería", "ganadería")
        bulletin("h1", "1" to "Ayudas a la pesca.", "2" to "Ayudas a la ganadería.")
        run()

        saveRule(AlertRuleDraft(name = "Ganadería y pesca", keywords = listOf("ganadería", "pesca")), id = id)
        val outcome = run()

        assertEquals(AlertDelivery.NONE, outcome.delivery)
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `re-enabling does not recover what was published during the pause`() = runTest {
        val id = rule("Ganadería", "ganadería")
        bulletin("h1", "1" to "Aprobación definitiva.")
        run()
        setEnabled(id, enabled = false)

        bulletin("h2", "1" to "Aprobación definitiva.", "2" to "Ayudas a la ganadería.")
        run()
        assertTrue(notifier.posted.isEmpty())

        setEnabled(id, enabled = true)
        assertEquals(AlertDelivery.NONE, run().delivery)

        bulletin("h3", "1" to "Aprobación definitiva.", "2" to "Ayudas a la ganadería.", "3" to "Ganadería de montaña.")
        val outcome = run()
        assertEquals(listOf("boc:3"), outcome.notifications.map { it.publication.externalKey })
    }

    // ---------- Feature 014 (STAB-003): a match the store could not record is not lost ----------

    /**
     * The audit's scenario on the real database: the match store throws once, the cycle ends with no
     * delivery, and the next cycle finds the source unchanged — `NotModified`, `newKeys` empty. Until
     * feature 014 the alert was gone for good. Now the publication is still pending in the store, the
     * next cycle delivers it, and the one after that delivers nothing more.
     */
    @Test
    fun `a match the store could not record is delivered by the next cycle, once`() = runTest {
        build(matchDao = FailingOnceAlertMatchDao(database.alertMatchDao(), failuresLeft = 1))
        rule("Ganadería", "ganadería")
        bulletin("h1", "1" to "Aprobación definitiva.")
        run()

        bulletin("h2", "1" to "Aprobación definitiva.", "2" to "Ayudas a la ganadería.")
        val failed = run()

        assertEquals(AlertDelivery.NONE, failed.delivery)
        assertTrue(notifier.posted.isEmpty())
        assertEquals(listOf("boc:2"), database.publicationDao().pendingAlertEvaluation().map { it.externalKey })
        assertTrue(crashReporter.messages.any { it == "cycle: recording failed, 1 key(s) kept pending" })

        val recovered = run()

        assertEquals(AlertDelivery.SYSTEM, recovered.delivery)
        assertEquals("boc:2", notifier.posted.single().single().publication.externalKey)
        assertEquals(1, alerts.observeUnreadCount().first())
        assertTrue(database.publicationDao().pendingAlertEvaluation().isEmpty())

        assertEquals(AlertDelivery.NONE, run().delivery)
        assertEquals(1, notifier.posted.size)
        assertEquals(1, database.alertMatchDao().count())
    }

    /**
     * The clock moves between the two cycles on purpose: with it frozen, `activeSince` equals
     * `first_seen_at` and the date filter of `AlertCandidate` is inert. A rule created after the
     * leftover was stored is newer than it, and must not fire for it (FR-017).
     */
    @Test
    fun `a rule created after a leftover was stored does not fire for it`() = runTest {
        build(matchDao = FailingOnceAlertMatchDao(database.alertMatchDao(), failuresLeft = 1))
        rule("Ganadería", "ganadería")
        bulletin("h1", "1" to "Aprobación definitiva.")
        run()
        bulletin("h2", "1" to "Aprobación definitiva.", "2" to "Ayudas a la ganadería.")
        run()

        now += 1_000_000
        rule("Ayudas", "ayudas")
        val outcome = run()

        assertEquals(listOf("Ganadería"), outcome.notifications.single().ruleNames)
        assertEquals(1, database.alertMatchDao().count())
    }

    // ---------- Delivery ----------

    @Test
    fun `with the application on screen the match goes in-app and the badge still counts`() = runTest {
        visibility.visible = true
        rule("Ganadería", "ganadería")
        bulletin("h1", "1" to "Aprobación definitiva.")
        run()

        bulletin("h2", "1" to "Aprobación definitiva.", "2" to "Ayudas a la ganadería.")
        val outcome = run()

        assertEquals(AlertDelivery.IN_APP, outcome.delivery)
        assertTrue(notifier.posted.isEmpty())
        assertEquals("Ganadería", inApp.observePending().first()?.ruleName)
        assertEquals(1, alerts.observeUnreadCount().first())
    }

    // ---------- Reading and managing ----------

    @Test
    fun `opening the publication reads it and lowers the badge`() = runTest {
        rule("Ganadería", "ganadería")
        rule("Ayudas", "ayudas")
        bulletin("h1", "1" to "Aprobación definitiva.")
        run()
        bulletin("h2", "1" to "Aprobación definitiva.", "2" to "Ayudas a la ganadería.")
        run()

        MarkAlertReadUseCase(alerts)("boc:2")

        assertEquals(0, alerts.observeUnreadCount().first())
        assertTrue(alerts.observeNews().first().single().isRead)
    }

    @Test
    fun `deleting a rule removes its news and leaves the bulletin whole`() = runTest {
        val id = rule("Ganadería", "ganadería")
        bulletin("h1", "1" to "Aprobación definitiva.")
        run()
        bulletin("h2", "1" to "Aprobación definitiva.", "2" to "Ayudas a la ganadería.")
        run()

        deleteRule(id)

        assertTrue(alerts.observeNews().first().isEmpty())
        assertEquals(2, database.publicationDao().count())
        assertEquals(listOf("ensureScheduled", "cancel"), scheduler.calls)
    }

    @Test
    fun `pausing keeps the rule and its news`() = runTest {
        val id = rule("Ganadería", "ganadería")
        bulletin("h1", "1" to "Aprobación definitiva.")
        run()
        bulletin("h2", "1" to "Aprobación definitiva.", "2" to "Ayudas a la ganadería.")
        run()

        setEnabled(id, enabled = false)

        assertEquals(1, alerts.countRules())
        assertEquals(1, alerts.observeNews().first().size)
    }
}
