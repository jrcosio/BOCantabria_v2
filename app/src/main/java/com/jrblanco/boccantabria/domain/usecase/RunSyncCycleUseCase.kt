package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.util.AppVisibilityProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
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
 * against **what was new**, record the matches, deliver them by one channel, tidy the document
 * cache.
 *
 * The single path for both the home screen and the background worker, so the two can never drift
 * (FR-037, FR-064; research.md D-404).
 *
 * The order of the first two steps is not incidental. The rules are read **before** the refresh:
 * every publication the refresh inserts is seen after that instant, so it is seen after the
 * `activeSince` of every rule in the snapshot. A rule created, edited or re-enabled while the cycle
 * runs is not evaluated until the next one — and then only against what that next cycle finds. That
 * is how "never retroactive" holds without comparing a single date (research.md D-405).
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
        val rules = alerts.enabledRules()

        val summary = when (val result = refreshPublications(force)) {
            is AppResult.Failure -> {
                crashReporter.log("cycle: refresh failed: ${result.error}")
                return result
            }
            is AppResult.Success -> result.data
        }

        val outcome = try {
            evaluate(summary, rules)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (unexpected: Throwable) {
            // The bulletin is already stored; a broken evaluation must not read as a failed refresh.
            crashReporter.recordNonFatal(unexpected)
            SyncCycleOutcome(summary, emptyList(), AlertDelivery.NONE)
        }

        // The bulletin has just changed, so yesterday's documents are the ones nobody is about to
        // open. Done here rather than while one is being read.
        releaseUnusedDocuments()
        return AppResult.Success(outcome)
    }

    private suspend fun evaluate(summary: SyncSummary, rules: List<AlertRule>): SyncCycleOutcome {
        if (summary.isBaseline) {
            crashReporter.log("cycle: baseline (${summary.insertedItems} inserted), alerts not evaluated")
            return SyncCycleOutcome(summary, emptyList(), AlertDelivery.NONE)
        }
        if (summary.newKeys.isEmpty() || rules.isEmpty()) {
            crashReporter.log("cycle: ${summary.newKeys.size} new, ${rules.size} rule(s), nothing to evaluate")
            return SyncCycleOutcome(summary, emptyList(), AlertDelivery.NONE)
        }

        val fresh = publications.byKeys(summary.newKeys)
        val now = time.nowMillis()
        val candidates = fresh.flatMap { publication ->
            rules.filter { rule -> matchRule(rule, publication) }
                .map { rule -> AlertMatch(rule.id, publication.externalKey, now) }
        }
        val recorded = alerts.recordMatches(candidates)
        if (recorded.isEmpty()) {
            crashReporter.log("cycle: ${fresh.size} new, ${rules.size} rule(s), 0 match(es)")
            return SyncCycleOutcome(summary, emptyList(), AlertDelivery.NONE)
        }

        val nameById = rules.associate { it.id to it.name }
        val byKey = fresh.associateBy { it.externalKey }
        val notifications = recorded.groupBy { it.externalKey }.mapNotNull { (key, matches) ->
            byKey[key]?.let { publication ->
                AlertNotification(publication, matches.mapNotNull { nameById[it.ruleId] }.distinct())
            }
        }

        // Decided once, for the whole cycle: every match of this cycle goes out by one channel.
        val delivery = if (appVisibility.isAppVisible()) AlertDelivery.IN_APP else AlertDelivery.SYSTEM
        crashReporter.log(
            "cycle: ${fresh.size} new, ${rules.size} rule(s), ${recorded.size} match(es) on " +
                "${notifications.size} publication(s), delivery=$delivery",
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
