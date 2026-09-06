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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * The person's alert rules and what they caught, and the only thing that writes them.
 *
 * Same shape as [SavedPublicationRepositoryImpl]: nothing throws, `CancellationException` is
 * rethrown, a failed read emits empty and stays alive, and the analytics count things without ever
 * naming them — a rule's name, its words and its organisation say what the person cares about, and
 * that never leaves the phone (012 research.md D-438).
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

    override fun observeRules(dayStart: Long): Flow<List<AlertRuleOverview>> =
        ruleDao.observeRules(dayStart)
            .map { rows -> rows.map { it.toOverview() } }
            .catch { cause -> emitEmptyAfterReporting(cause) { emit(emptyList()) } }
            .flowOn(dispatchers.io)

    override suspend fun rule(id: String): AlertRule? = withContext(dispatchers.io) {
        runCatchingReported { ruleDao.byId(id)?.toDomain() }
    }

    override suspend fun enabledRules(): List<AlertRule> = withContext(dispatchers.io) {
        runCatchingReported { ruleDao.enabledRules().map { it.toDomain() } } ?: emptyList()
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
     * that is what keeps a publication from being delivered twice. A failure here is reported and
     * treated as "nothing new": the cycle must not notify what it could not record.
     */
    override suspend fun recordMatches(candidates: List<AlertMatch>): List<AlertMatch> =
        withContext(dispatchers.io) {
            if (candidates.isEmpty()) return@withContext emptyList()
            val distinct = candidates.distinctBy { it.ruleId to it.externalKey }
            val inserted = runCatchingReported {
                distinct.chunked(SQLITE_VARIABLE_LIMIT).flatMap { chunk ->
                    val ids = matchDao.insert(chunk.map { AlertMatchEntity(ruleId = it.ruleId, externalKey = it.externalKey, matchedAt = it.matchedAt) })
                    chunk.filterIndexed { index, _ -> ids[index] != IGNORED_ROW_ID }
                }
            } ?: emptyList()
            if (inserted.isNotEmpty()) {
                analytics.track(
                    AnalyticsEvent(
                        name = EVENT_MATCHES,
                        parameters = mapOf(
                            "recorded" to inserted.size.toString(),
                            "publications" to inserted.map { it.externalKey }.distinct().size.toString(),
                        ),
                    ),
                )
            }
            inserted
        }

    override fun observeNews(): Flow<List<AlertNews>> =
        matchDao.observeNews()
            .map { rows -> rows.mapNotNull { it.toNews() } }
            .catch { cause -> emitEmptyAfterReporting(cause) { emit(emptyList()) } }
            .flowOn(dispatchers.io)

    override fun observeUnreadCount(): Flow<Int> =
        matchDao.observeUnreadCount()
            .catch { cause -> emitEmptyAfterReporting(cause) { emit(0) } }
            .flowOn(dispatchers.io)

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

    private suspend fun emitEmptyAfterReporting(cause: Throwable, emitEmpty: suspend () -> Unit) {
        if (cause is CancellationException) throw cause
        crashReporter.recordNonFatal(cause)
        emitEmpty()
    }

    companion object {
        const val EVENT_RULE_SAVED: String = "alert_rule_saved"
        const val EVENT_RULE_TOGGLED: String = "alert_rule_toggled"
        const val EVENT_RULE_DELETED: String = "alert_rule_deleted"
        const val EVENT_MATCHES: String = "alert_matches"
        const val EVENT_READ: String = "alert_read"

        private const val IGNORED_ROW_ID = -1L
        private const val SQLITE_VARIABLE_LIMIT = 900

        /** The list converter's separator: what `GROUP_CONCAT` joins the rule names with. */
        private const val SEPARATOR = "\u001F"
    }
}
