package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.core.telemetry.AnalyticsEvent
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.source.local.AlertMatchDao
import com.jrblanco.boccantabria.data.source.local.AlertMatchEntity
import com.jrblanco.boccantabria.data.source.local.AlertNewsRow
import com.jrblanco.boccantabria.data.source.local.AlertRuleDao
import com.jrblanco.boccantabria.data.source.local.AlertRuleWithStats
import com.jrblanco.boccantabria.data.source.local.toDomain
import com.jrblanco.boccantabria.data.source.local.toEntity
import com.jrblanco.boccantabria.domain.model.AlertMatch
import com.jrblanco.boccantabria.domain.model.AlertNews
import com.jrblanco.boccantabria.domain.model.AlertRule
import com.jrblanco.boccantabria.domain.model.AlertRuleDraft
import com.jrblanco.boccantabria.domain.model.AlertRuleOverview
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.repository.AlertRepository
import com.jrblanco.boccantabria.domain.repository.BocSectionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * The person's alert rules and what they caught, and the only thing that writes them.
 *
 * Same shape as [SavedPublicationRepositoryImpl]: nothing throws, `CancellationException` is
 * rethrown, a failed read emits empty and **recovers** with a bounded retry (feature 014), and the
 * analytics count things without ever naming them — a rule's name, its words and its organisation
 * say what the person cares about, and that never leaves the phone (012 research.md D-438).
 */
@Suppress("LongParameterList")
class AlertRepositoryImpl(
    private val ruleDao: AlertRuleDao,
    private val matchDao: AlertMatchDao,
    private val sections: BocSectionRepository,
    private val time: TimeProvider,
    private val dispatchers: DispatcherProvider,
    private val analytics: AnalyticsTracker,
    private val crashReporter: CrashReporter,
) : AlertRepository {

    // The three flows recover from a read failure with `recoverReads` (feature 014, STAB-004): a
    // `catch` that emitted empty left the flow finished, and the bell's badge — whose owner lives for
    // the whole session — stayed at zero for the rest of the process. Always the last operator.
    override fun observeRules(dayStart: Long): Flow<List<AlertRuleOverview>> =
        ruleDao.observeRules(dayStart)
            .map { rows -> rows.map { it.toOverview() } }
            .flowOn(dispatchers.io)
            .recoverReads(fallback = emptyList(), name = "rules", crashReporter = crashReporter)

    override suspend fun rule(id: String): AlertRule? = withContext(dispatchers.io) {
        runCatchingReported { ruleDao.byId(id)?.toDomain() }
    }

    override suspend fun enabledRules(): AppResult<List<AlertRule>> = withContext(dispatchers.io) {
        // A Failure, not an empty list: to the cycle «no rules» means "these publications are
        // evaluated", and a read that failed must not clear anything (014 research.md D-610).
        write { ruleDao.enabledRules().map { it.toDomain() } }
    }

    override suspend fun countRules(): Int = withContext(dispatchers.io) {
        runCatchingReported { ruleDao.count() } ?: 0
    }

    override suspend fun countEnabled(): Int = withContext(dispatchers.io) {
        runCatchingReported { ruleDao.countEnabled() } ?: 0
    }

    override suspend fun save(draft: AlertRuleDraft, id: String?): AppResult<String> =
        withContext(dispatchers.io) {
            write {
                val now = time.nowMillis()
                val existing = id?.let { ruleDao.byId(it) }
                val ruleId = existing?.id ?: id ?: UUID.randomUUID().toString()
                val rule = draft.toRule(
                    id = ruleId,
                    now = now,
                    sections = sections.sections(),
                    createdAt = existing?.createdAt ?: now,
                )
                ruleDao.upsert(rule.toEntity())
                analytics.track(
                    AnalyticsEvent(
                        name = EVENT_RULE_SAVED,
                        parameters = mapOf(
                            "keywords" to rule.keywords.size.toString(),
                            "sections" to rule.sectionCodes.size.toString(),
                            "has_organization" to (rule.organizationQuery != null).toString(),
                            "match_mode" to rule.matchMode.name.lowercase(),
                            "is_edit" to (existing != null).toString(),
                        ),
                    ),
                )
                ruleId
            }
        }

    override suspend fun setEnabled(id: String, enabled: Boolean): AppResult<Unit> =
        withContext(dispatchers.io) {
            write {
                ruleDao.setEnabled(id, enabled, time.nowMillis())
                analytics.track(AnalyticsEvent(EVENT_RULE_TOGGLED, mapOf("enabled" to enabled.toString())))
            }
        }

    override suspend fun delete(id: String): AppResult<Unit> = withContext(dispatchers.io) {
        write {
            ruleDao.delete(id)
            analytics.track(AnalyticsEvent(EVENT_RULE_DELETED))
        }
    }

    /**
     * Inserts what is new and returns exactly that.
     *
     * The unique index rejects a pair already recorded and `insert` reports it as `-1`; filtering on
     * that is what keeps a publication from being delivered twice.
     *
     * **One insert, one transaction.** Until feature 014 the list went in chunks of 900, and that was
     * the defect: chunk one committed, chunk two threw, the pairs of chunk one were recorded but never
     * delivered, and the unique index then hid them for ever. The 900 limit protects `IN (...)` lists,
     * not inserts — Room binds one row per statement and runs the whole list in one transaction. And
     * a failure is a [AppResult.Failure], not "nothing new": the cycle keeps those publications
     * pending and tries again (STAB-003; 014 research.md D-611).
     */
    override suspend fun recordMatches(candidates: List<AlertMatch>): AppResult<List<AlertMatch>> =
        withContext(dispatchers.io) {
            if (candidates.isEmpty()) return@withContext AppResult.Success(emptyList())
            val distinct = candidates.distinctBy { it.ruleId to it.externalKey }
            val result = write {
                val ids = matchDao.insert(
                    distinct.map { AlertMatchEntity(ruleId = it.ruleId, externalKey = it.externalKey, matchedAt = it.matchedAt) },
                )
                distinct.filterIndexed { index, _ -> ids[index] != IGNORED_ROW_ID }
            }
            if (result is AppResult.Success && result.data.isNotEmpty()) {
                analytics.track(
                    AnalyticsEvent(
                        name = EVENT_MATCHES,
                        parameters = mapOf(
                            "recorded" to result.data.size.toString(),
                            "publications" to result.data.map { it.externalKey }.distinct().size.toString(),
                        ),
                    ),
                )
            }
            result
        }

    override fun observeNews(): Flow<List<AlertNews>> =
        matchDao.observeNews()
            .map { rows -> rows.mapNotNull { it.toNews() } }
            .flowOn(dispatchers.io)
            .recoverReads(fallback = emptyList(), name = "news", crashReporter = crashReporter)

    override fun observeUnreadCount(): Flow<Int> =
        matchDao.observeUnreadCount()
            .flowOn(dispatchers.io)
            .recoverReads(fallback = 0, name = "unread-count", crashReporter = crashReporter)

    override suspend fun markRead(externalKey: String): AppResult<Unit> = withContext(dispatchers.io) {
        write {
            val touched = matchDao.markRead(externalKey, time.nowMillis())
            if (touched > 0) analytics.track(AnalyticsEvent(EVENT_READ, mapOf("all" to "false")))
        }
    }

    override suspend fun markAllRead(): AppResult<Unit> = withContext(dispatchers.io) {
        write {
            matchDao.markAllRead(time.nowMillis())
            analytics.track(AnalyticsEvent(EVENT_READ, mapOf("all" to "true")))
        }
    }

    private fun AlertRuleWithStats.toOverview() = AlertRuleOverview(
        rule = rule.toDomain(),
        lastMatchedAt = lastMatchedAt,
        matchesToday = matchesToday,
    )

    /** A row whose rule names could not be split is skipped rather than shown blank. */
    private fun AlertNewsRow.toNews(): AlertNews? {
        val names = ruleNames.split(SEPARATOR).filter { it.isNotBlank() }.distinct()
        if (names.isEmpty()) return null
        return AlertNews(
            publication = publication.toDomain(),
            ruleNames = names,
            detectedAt = detectedAt,
            isRead = unread == 0,
        )
    }

    private suspend fun <T> write(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (unexpected: Throwable) {
        crashReporter.recordNonFatal(unexpected)
        AppResult.Failure(DomainError.Unknown)
    }

    private suspend fun <T> runCatchingReported(block: suspend () -> T): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (unexpected: Throwable) {
        crashReporter.recordNonFatal(unexpected)
        null
    }

    companion object {
        const val EVENT_RULE_SAVED: String = "alert_rule_saved"
        const val EVENT_RULE_TOGGLED: String = "alert_rule_toggled"
        const val EVENT_RULE_DELETED: String = "alert_rule_deleted"
        const val EVENT_MATCHES: String = "alert_matches"
        const val EVENT_READ: String = "alert_read"

        private const val IGNORED_ROW_ID = -1L

        /** The list converter's separator: what `GROUP_CONCAT` joins the rule names with. */
        private const val SEPARATOR = "\u001F"
    }
}
