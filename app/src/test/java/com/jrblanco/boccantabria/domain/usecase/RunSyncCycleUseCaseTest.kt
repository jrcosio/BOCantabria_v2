package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.AlertDelivery
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.model.SyncSummary
import com.jrblanco.boccantabria.fake.FakeAlertRepository
import com.jrblanco.boccantabria.fake.FakeAppVisibilityProvider
import com.jrblanco.boccantabria.fake.FakeDocumentRepository
import com.jrblanco.boccantabria.fake.FakeInAppAlertStore
import com.jrblanco.boccantabria.fake.FakePublicationRepository
import com.jrblanco.boccantabria.fake.RecordingAlertNotifier
import com.jrblanco.boccantabria.fake.RecordingCrashReporter
import com.jrblanco.boccantabria.fake.alertRule
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cycle, against the nine cases of §24 of the functional document and the obligations of
 * `contracts` §2.1. Everything below the use case is a fake; what is under test is the order.
 */
class RunSyncCycleUseCaseTest {

    private val publications = FakePublicationRepository()
    private val alerts = FakeAlertRepository()
    private val notifier = RecordingAlertNotifier()
    private val inApp = FakeInAppAlertStore()
    private val visibility = FakeAppVisibilityProvider(visible = false)
    private val documents = FakeDocumentRepository()
    private val crashReporter = RecordingCrashReporter()
    private var now = 50_000L

    private val ganaderia: Publication = publication("boc:1", title = "Ayudas a la ganadería.")
    private val pesca: Publication = publication("boc:2", title = "Ayudas a la pesca.")

    private fun cycle() = RunSyncCycleUseCase(
        refreshPublications = RefreshPublicationsUseCase(publications),
        publications = publications,
        alerts = alerts,
        matchRule = MatchAlertRuleUseCase(BocSectionRepositoryImpl()),
        notifier = notifier,
        inAppAlerts = inApp,
        appVisibility = visibility,
        releaseUnusedDocuments = ReleaseUnusedDocumentsUseCase(documents),
        time = object : TimeProvider { override fun nowMillis() = now },
        crashReporter = crashReporter,
    )

    private fun refreshReturns(vararg fresh: Publication, baseline: Boolean = false) {
        publications.emit(fresh.toList())
        publications.refreshResult = AppResult.Success(
            SyncSummary(succeededFeeds = 19, insertedItems = fresh.size, newKeys = fresh.map { it.externalKey }.toSet(), isBaseline = baseline),
        )
    }

    // ---------- §24: the cycle ----------

    @Test
    fun `the first synchronisation is a baseline and notifies nothing`() = runTest {
        alerts.emitRules(listOf(alertRule(keywords = listOf("ganadería"))))
        refreshReturns(ganaderia, baseline = true)

        val outcome = (cycle()(force = true) as AppResult.Success).data

        assertEquals(AlertDelivery.NONE, outcome.delivery)
        assertTrue(notifier.posted.isEmpty())
        assertTrue(alerts.storedMatches.isEmpty())
        assertEquals(0, publications.pendingReads)
        assertTrue(crashReporter.messages.any { it.startsWith("cycle: baseline") })
    }

    @Test
    fun `a synchronisation without changes reads what is pending once and records nothing`() = runTest {
        alerts.emitRules(listOf(alertRule()))
        publications.refreshResult = AppResult.Success(SyncSummary(succeededFeeds = 19, unchangedFeeds = 19))

        val outcome = (cycle()(force = true) as AppResult.Success).data

        assertEquals(AlertDelivery.NONE, outcome.delivery)
        assertEquals(1, publications.pendingReads)
        assertFalse(alerts.calls.any { it.startsWith("recordMatches") })
    }

    @Test
    fun `a new item that matches is recorded and delivered`() = runTest {
        alerts.emitRules(listOf(alertRule(keywords = listOf("ganadería"))))
        refreshReturns(ganaderia)

        val outcome = (cycle()(force = true) as AppResult.Success).data

        assertEquals(AlertDelivery.SYSTEM, outcome.delivery)
        assertEquals(listOf("boc:1"), outcome.notifications.map { it.publication.externalKey })
        assertEquals(listOf("Ganadería"), outcome.notifications.single().ruleNames)
        assertEquals(1, notifier.posted.size)
        assertEquals(1, alerts.storedMatches.size)
        assertEquals(now, alerts.storedMatches.single().matchedAt)
    }

    @Test
    fun `a new item that does not match is silent`() = runTest {
        alerts.emitRules(listOf(alertRule(keywords = listOf("ganadería"))))
        refreshReturns(pesca)

        val outcome = (cycle()(force = true) as AppResult.Success).data

        assertEquals(AlertDelivery.NONE, outcome.delivery)
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `the same item received again is not delivered twice`() = runTest {
        alerts.emitRules(listOf(alertRule(keywords = listOf("ganadería"))))
        refreshReturns(ganaderia)
        cycle()(force = true)

        // The source repeats it; the repository, being fake, reports it as new again. The unique
        // pair in the store is what stops the second delivery.
        refreshReturns(ganaderia)
        val outcome = (cycle()(force = true) as AppResult.Success).data

        assertEquals(AlertDelivery.NONE, outcome.delivery)
        assertEquals(1, notifier.posted.size)
        assertEquals(1, alerts.storedMatches.size)
    }

    @Test
    fun `an item that matches two rules is one notification naming both`() = runTest {
        alerts.emitRules(
            listOf(
                alertRule(id = "r1", name = "Ganadería", keywords = listOf("ganadería")),
                alertRule(id = "r2", name = "Subvenciones rurales", keywords = listOf("ayudas")),
            ),
        )
        refreshReturns(ganaderia)

        val outcome = (cycle()(force = true) as AppResult.Success).data

        assertEquals(1, outcome.notifications.size)
        assertEquals(listOf("Ganadería", "Subvenciones rurales"), outcome.notifications.single().ruleNames)
        assertEquals(2, alerts.storedMatches.size)
        assertEquals(1, notifier.posted.single().size)
    }

    /** The rules are read before the refresh: a rule created meanwhile waits for the next cycle. */
    @Test
    fun `a rule created during the refresh is not evaluated until the next cycle`() = runTest {
        refreshReturns(ganaderia)
        publications.onRefresh = { alerts.emitRules(listOf(alertRule(keywords = listOf("ganadería")))) }

        val outcome = (cycle()(force = true) as AppResult.Success).data

        assertEquals(AlertDelivery.NONE, outcome.delivery)
        assertTrue(alerts.storedMatches.isEmpty())
    }

    @Test
    fun `a paused rule does not take part`() = runTest {
        alerts.emitRules(listOf(alertRule(keywords = listOf("ganadería"), isEnabled = false)))
        refreshReturns(ganaderia)

        val outcome = (cycle()(force = true) as AppResult.Success).data

        assertEquals(AlertDelivery.NONE, outcome.delivery)
    }

    @Test
    fun `a failed refresh evaluates nothing and is reported as it came`() = runTest {
        alerts.emitRules(listOf(alertRule()))
        publications.refreshResult = AppResult.Failure(DomainError.Network)

        val result = cycle()(force = true)

        assertEquals(AppResult.Failure(DomainError.Network), result)
        assertTrue(alerts.storedMatches.isEmpty())
        assertTrue(notifier.posted.isEmpty())
        assertEquals(0, documents.released)
        assertTrue(crashReporter.messages.any { it == "cycle: refresh failed: Network" })
    }

    @Test
    fun `a skipped refresh evaluates only what an earlier cycle left pending`() = runTest {
        alerts.emitRules(listOf(alertRule()))
        publications.stale = false

        val outcome = (cycle()(force = false) as AppResult.Success).data

        assertEquals(AlertDelivery.NONE, outcome.delivery)
        assertEquals(0, publications.refreshCount)
        assertEquals(1, publications.pendingReads)
        assertEquals(1, documents.released)
    }

    // ---------- Feature 014 (STAB-003): what could not be recorded is not lost ----------

    /**
     * The audit's reproduction, on the fakes: recording fails, the cycle ends as a success with no
     * delivery, and the next cycle finds nothing new at the source. Until feature 014 that was the end
     * of the alert. Now the key stays pending in the store and the next cycle delivers it — once.
     */
    @Test
    fun `what could not be recorded is kept pending and delivered exactly once by the next cycle`() = runTest {
        alerts.emitRules(listOf(alertRule(keywords = listOf("ganadería"))))
        refreshReturns(ganaderia)
        alerts.failRecordMatches = true

        val first = (cycle()(force = true) as AppResult.Success).data

        assertEquals(AlertDelivery.NONE, first.delivery)
        assertTrue(crashReporter.messages.any { it == "cycle: recording failed, 1 key(s) kept pending" })
        assertEquals(setOf("boc:1"), publications.pendingKeys.keys)

        alerts.failRecordMatches = false
        publications.refreshResult = AppResult.Success(SyncSummary(succeededFeeds = 19, unchangedFeeds = 19))
        val second = (cycle()(force = true) as AppResult.Success).data

        assertEquals(AlertDelivery.SYSTEM, second.delivery)
        assertEquals(listOf("boc:1"), second.notifications.map { it.publication.externalKey })
        assertEquals(1, notifier.posted.size)
        assertTrue(publications.pendingKeys.isEmpty())

        val third = (cycle()(force = true) as AppResult.Success).data

        assertEquals(AlertDelivery.NONE, third.delivery)
        assertEquals(1, notifier.posted.size)
        assertEquals(1, alerts.storedMatches.size)
    }

    /**
     * «Never retroactive» has to hold for a leftover too. The order of the cycle cannot guarantee it
     * — the rule was created after the publication was stored, but before it was evaluated — so the
     * candidate compares dates (D-609). The clock is advanced on purpose: frozen, the filter is inert.
     */
    @Test
    fun `a rule created between two cycles does not fire for what an earlier cycle left pending`() = runTest {
        publications.now = 10_000L
        val ganaderiaRule = alertRule(id = "rule-1", name = "Ganadería", keywords = listOf("ganadería"), activeSince = 5_000L)
        alerts.emitRules(listOf(ganaderiaRule))
        refreshReturns(ganaderia)
        alerts.failRecordMatches = true
        cycle()(force = true)

        alerts.failRecordMatches = false
        alerts.emitRules(
            listOf(
                ganaderiaRule,
                alertRule(id = "rule-2", name = "Ayudas", keywords = listOf("ayudas"), activeSince = 20_000L),
            ),
        )
        publications.refreshResult = AppResult.Success(SyncSummary(succeededFeeds = 19, unchangedFeeds = 19))
        val outcome = (cycle()(force = true) as AppResult.Success).data

        assertEquals(listOf("Ganadería"), outcome.notifications.single().ruleNames)
        assertEquals(listOf("rule-1"), alerts.storedMatches.map { it.ruleId })
        assertTrue(publications.pendingKeys.isEmpty())
    }

    /** The most frequent cycle — opening Inicio within the half hour — is also the recovery path. */
    @Test
    fun `a leftover is evaluated even when the refresh is skipped`() = runTest {
        alerts.emitRules(listOf(alertRule(keywords = listOf("ganadería"))))
        publications.seedPending(ganaderia, storedAt = 1_000_000L)
        publications.stale = false

        val outcome = (cycle()(force = false) as AppResult.Success).data

        assertEquals(AlertDelivery.SYSTEM, outcome.delivery)
        assertEquals(0, publications.refreshCount)
        assertEquals(listOf("boc:1"), outcome.notifications.map { it.publication.externalKey })
        assertTrue(publications.pendingKeys.isEmpty())
    }

    @Test
    fun `with no rules the new publications are cleared, so a rule created later does not see them`() = runTest {
        refreshReturns(ganaderia)
        cycle()(force = true)
        assertTrue(publications.pendingKeys.isEmpty())

        alerts.emitRules(listOf(alertRule(keywords = listOf("ganadería"))))
        publications.refreshResult = AppResult.Success(SyncSummary(succeededFeeds = 19, unchangedFeeds = 19))
        val outcome = (cycle()(force = true) as AppResult.Success).data

        assertEquals(AlertDelivery.NONE, outcome.delivery)
        assertTrue(notifier.posted.isEmpty())
    }

    /**
     * The matches are already recorded: skipping the delivery would make them undeliverable for
     * ever, because the next cycle would find every pair already in the store (D-610).
     */
    @Test
    fun `a failure to clear the flag does not block delivery and does not deliver twice`() = runTest {
        alerts.emitRules(listOf(alertRule(keywords = listOf("ganadería"))))
        refreshReturns(ganaderia)
        publications.failMarkEvaluated = true

        val first = (cycle()(force = true) as AppResult.Success).data

        assertEquals(AlertDelivery.SYSTEM, first.delivery)
        assertEquals(1, notifier.posted.size)
        assertTrue(crashReporter.messages.any { it == "cycle: 1 key(s) recorded but not cleared" })
        assertEquals(setOf("boc:1"), publications.pendingKeys.keys)

        publications.failMarkEvaluated = false
        publications.refreshResult = AppResult.Success(SyncSummary(succeededFeeds = 19, unchangedFeeds = 19))
        val second = (cycle()(force = true) as AppResult.Success).data

        assertEquals(AlertDelivery.NONE, second.delivery)
        assertEquals(1, notifier.posted.size)
        assertTrue(publications.pendingKeys.isEmpty())
    }

    /** A rule read that fails is not "no rules": the bulletin still refreshes and nothing is cleared. */
    @Test
    fun `when the rules cannot be read the refresh still runs and evaluation waits`() = runTest {
        alerts.failReads = true
        refreshReturns(ganaderia)

        val outcome = (cycle()(force = true) as AppResult.Success).data

        assertEquals(AlertDelivery.NONE, outcome.delivery)
        assertEquals(1, publications.refreshCount)
        assertEquals(0, publications.pendingReads)
        assertEquals(setOf("boc:1"), publications.pendingKeys.keys)
        assertTrue(crashReporter.messages.any { it == "cycle: rules unreadable, evaluation deferred" })
    }

    // ---------- Delivery ----------

    @Test
    fun `with the application on screen the match goes in-app and not to the system`() = runTest {
        visibility.visible = true
        alerts.emitRules(listOf(alertRule(keywords = listOf("ganadería"))))
        refreshReturns(ganaderia)

        val outcome = (cycle()(force = true) as AppResult.Success).data

        assertEquals(AlertDelivery.IN_APP, outcome.delivery)
        assertTrue(notifier.posted.isEmpty())
        assertEquals(1, inApp.published.single().publicationCount)
        assertEquals("Ganadería", inApp.published.single().ruleName)
    }

    @Test
    fun `several publications on screen name nobody and count them`() = runTest {
        visibility.visible = true
        alerts.emitRules(listOf(alertRule(keywords = listOf("ayudas"))))
        refreshReturns(ganaderia, pesca)

        cycle()(force = true)

        assertEquals(2, inApp.published.single().publicationCount)
        assertEquals(null, inApp.published.single().ruleName)
    }

    @Test
    fun `visibility is asked once per cycle`() = runTest {
        alerts.emitRules(listOf(alertRule(keywords = listOf("ayudas"))))
        refreshReturns(ganaderia, pesca)

        cycle()(force = true)

        assertEquals(1, visibility.reads)
    }

    @Test
    fun `the document cache is tidied whenever the refresh did not fail`() = runTest {
        refreshReturns(pesca)

        cycle()(force = true)

        assertEquals(1, documents.released)
    }

    @Test
    fun `what is pending is read once and then cleared, never the whole store`() = runTest {
        alerts.emitRules(listOf(alertRule()))
        refreshReturns(ganaderia)

        cycle()(force = true)

        assertEquals(1, publications.pendingReads)
        assertEquals(listOf(setOf("boc:1")), publications.markCalls)
        assertTrue(publications.pendingKeys.isEmpty())
    }

    // ---------- Privacy ----------

    @Test
    fun `the log says how many and by which channel, never what`() = runTest {
        alerts.emitRules(listOf(alertRule(name = "Ganadería", keywords = listOf("ganadería"))))
        refreshReturns(ganaderia)

        cycle()(force = true)

        val log = crashReporter.messages.joinToString("\n")
        assertTrue(log.contains("1 match(es) on 1 publication(s), delivery=SYSTEM"))
        assertFalse(log.contains("Ganadería"))
        assertFalse(log.contains("ganadería"))
        assertFalse(log.contains(ganaderia.title))
    }
}
