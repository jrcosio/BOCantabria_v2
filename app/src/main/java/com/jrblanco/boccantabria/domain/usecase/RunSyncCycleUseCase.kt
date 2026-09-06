package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.util.AppVisibilityProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.domain.model.AlertCandidate
import com.jrblanco.boccantabria.domain.model.AlertDelivery
import com.jrblanco.boccantabria.domain.model.AlertMatch
import com.jrblanco.boccantabria.domain.model.AlertNotification
import com.jrblanco.boccantabria.domain.model.AlertRule
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.InAppAlert
import com.jrblanco.boccantabria.domain.model.SyncCycleOutcome
import com.jrblanco.boccantabria.domain.model.SyncSummary
import com.jrblanco.boccantabria.domain.repository.AlertNotifier
import com.jrblanco.boccantabria.domain.repository.AlertRepository
import com.jrblanco.boccantabria.domain.repository.InAppAlertStore
import com.jrblanco.boccantabria.domain.repository.PublicationRepository
import kotlinx.coroutines.CancellationException

/**
 * One synchronisation cycle, start to finish: refresh the bulletin, evaluate the active rules
 * against **what the store still marks as pending**, record the matches, deliver them by one channel,
 * tidy the document cache.
 *
 * The single path for both the home screen and the background worker, so the two can never drift
 * (FR-037, FR-064; research.md D-404).
 *
 * The order of the first two steps is not incidental. The rules are read **before** the refresh:
 * every publication the refresh inserts is seen after that instant, so it is seen after the
 * `activeSince` of every rule in the snapshot. A rule created, edited or re-enabled while the cycle
 * runs is not evaluated until the next one (012 research.md D-405).
 *
 * Since feature 014 the evaluation reads the store, not the refresh (STAB-003). Every row a
 * synchronisation inserts carries a pending mark; the cycle reads what is pending, records the
 * matches, and clears the mark **only then**. If recording fails, or the process dies in between, the
 * mark stays and the next cycle — even one that finds the source unchanged — picks it up. The unique
 * index on the matches makes that retry deliver each pair once. And because a leftover may be older
 * than a rule created in the meantime, the order argument above is not enough for it:
 * [AlertCandidate.isVisibleTo] compares the two instants and keeps «never retroactive» true for both
 * the fresh rows and the leftovers (014 research.md D-607, D-609, D-610).
 */
@Suppress("LongParameterList")
class RunSyncCycleUseCase(
    private val refreshPublications: RefreshPublicationsUseCase,
    private val publications: PublicationRepository,
    private val alerts: AlertRepository,
    private val matchRule: MatchAlertRuleUseCase,
    private val notifier: AlertNotifier,
    private val inAppAlerts: InAppAlertStore,
    private val appVisibility: AppVisibilityProvider,
    private val releaseUnusedDocuments: ReleaseUnusedDocumentsUseCase,
    private val time: TimeProvider,
    private val crashReporter: CrashReporter,
) {

    suspend operator fun invoke(force: Boolean): AppResult<SyncCycleOutcome> {
        // A read that fails is not «no rules». The bulletin still has to refresh; the evaluation waits
        // for a cycle that can read them, and nothing pending is touched meanwhile (D-612).
        val rules = when (val read = alerts.enabledRules()) {
            is AppResult.Success -> read.data
            is AppResult.Failure -> null
        }

        val summary = when (val result = refreshPublications(force)) {
            is AppResult.Failure -> {
                crashReporter.log("cycle: refresh failed: ${result.error}")
                return result
            }
            is AppResult.Success -> result.data
        }

        val outcome = try {
            if (rules == null) {
                crashReporter.log("cycle: rules unreadable, evaluation deferred")
                SyncCycleOutcome(summary, emptyList(), AlertDelivery.NONE)
            } else {
                evaluate(summary, rules)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (unexpected: Throwable) {
            // The bulletin is already stored; a broken evaluation must not read as a failed refresh.
            // Whatever was pending stays pending, so nothing is lost either.
            crashReporter.recordNonFatal(unexpected)
            SyncCycleOutcome(summary, emptyList(), AlertDelivery.NONE)
        }

        // The bulletin has just changed, so yesterday's documents are the ones nobody is about to
        // open. Done here rather than while one is being read.
        releaseUnusedDocuments()
        return AppResult.Success(outcome)
    }

    private suspend fun evaluate(summary: SyncSummary, rules: List<AlertRule>): SyncCycleOutcome {
        val nothing = SyncCycleOutcome(summary, emptyList(), AlertDelivery.NONE)
        if (summary.isBaseline) {
            // The baseline stored its rows with the mark already off: nothing to read (D-608).
            crashReporter.log("cycle: baseline (${summary.insertedItems} inserted), alerts not evaluated")
            return nothing
        }

        // Read also when the refresh was skipped or brought nothing new: what an earlier cycle left
        // pending is exactly what has to be picked up, and opening the home screen within the half
        // hour is the most frequent cycle there is (D-612).
        val pending = when (val read = publications.pendingAlertCandidates()) {
            is AppResult.Success -> read.data
            is AppResult.Failure -> {
                crashReporter.log("cycle: pending unreadable")
                return nothing
            }
        }
        val leftover = pending.count { it.publication.externalKey !in summary.newKeys }
        if (pending.isEmpty()) {
            crashReporter.log("cycle: ${summary.newKeys.size} new, 0 pending, ${rules.size} rule(s), nothing to evaluate")
            return nothing
        }

        val now = time.nowMillis()
        val matches = pending.flatMap { candidate ->
            rules.filter { rule -> candidate.isVisibleTo(rule) && matchRule(rule, candidate.publication) }
                .map { rule -> AlertMatch(rule.id, candidate.publication.externalKey, now) }
        }
        val recorded = if (matches.isEmpty()) {
            emptyList()
        } else {
            when (val result = alerts.recordMatches(matches)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> {
                    // Nothing was recorded, so nothing is cleared: the next cycle finds these
                    // publications still pending and tries again (D-610).
                    crashReporter.log("cycle: recording failed, ${pending.size} key(s) kept pending")
                    return nothing
                }
            }
        }

        // Exactly the keys that were read — also with zero matches, or the publications nobody
        // matched would stay pending and a rule created later would see them. If clearing fails the
        // delivery goes ahead anyway: the matches are recorded, and skipping it would make them
        // undeliverable for ever, because the next cycle would find every pair already stored. That
        // next cycle simply retries the clearing (D-610).
        val evaluated = pending.map { it.publication.externalKey }.toSet()
        if (publications.markAlertsEvaluated(evaluated) is AppResult.Failure) {
            crashReporter.log("cycle: ${pending.size} key(s) recorded but not cleared")
        }
        if (recorded.isEmpty()) {
            crashReporter.log(
                "cycle: ${summary.newKeys.size} new, $leftover pending from earlier, ${rules.size} rule(s), 0 match(es)",
            )
            return nothing
        }

        val nameById = rules.associate { it.id to it.name }
        val byKey = pending.associate { it.publication.externalKey to it.publication }
        val notifications = recorded.groupBy { it.externalKey }.mapNotNull { (key, own) ->
            byKey[key]?.let { publication ->
                AlertNotification(publication, own.mapNotNull { nameById[it.ruleId] }.distinct())
            }
        }

        // Decided once, for the whole cycle: every match of this cycle goes out by one channel.
        val delivery = if (appVisibility.isAppVisible()) AlertDelivery.IN_APP else AlertDelivery.SYSTEM
        crashReporter.log(
            "cycle: ${summary.newKeys.size} new, $leftover pending from earlier, ${rules.size} rule(s), " +
                "${recorded.size} match(es) on ${notifications.size} publication(s), delivery=$delivery",
        )
        when (delivery) {
            AlertDelivery.SYSTEM -> notifier.post(notifications)
            AlertDelivery.IN_APP -> inAppAlerts.publish(
                InAppAlert(
                    publicationCount = notifications.size,
                    ruleName = notifications.singleOrNull()?.ruleNames?.singleOrNull(),
                ),
            )
            AlertDelivery.NONE -> Unit
        }
        return SyncCycleOutcome(summary, notifications, delivery)
    }
}
