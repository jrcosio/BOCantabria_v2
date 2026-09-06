package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.AlertMatch
import com.jrblanco.boccantabria.domain.model.AlertNews
import com.jrblanco.boccantabria.domain.model.AlertRule
import com.jrblanco.boccantabria.domain.model.AlertRuleDraft
import com.jrblanco.boccantabria.domain.model.AlertRuleOverview
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.AlertRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The alert store held in memory, with the same rules the real one enforces: one match per
 * rule–publication pair, `activeSince` renewed on every write that matters, and a delete that
 * cascades.
 *
 * It records every call so a use-case or view-model test can assert the delegation. Publications
 * for the news rows come from [publications], which the test fills.
 */
class FakeAlertRepository(
    initialRules: List<AlertRule> = emptyList(),
    var now: Long = 1_000_000L,
) : AlertRepository {

    private val rules = MutableStateFlow(initialRules.associateBy { it.id })
    private val matches = MutableStateFlow(emptyList<StoredMatch>())

    /** Where the news rows find their publication. */
    val publications: MutableMap<String, Publication> = mutableMapOf()

    /** Every call received, in order, so a test can assert the delegation instead of guessing. */
    val calls: MutableList<String> = mutableListOf()

    /** When set, the next write fails. */
    var failWrites: Boolean = false

    private var nextId = 1

    val storedRules: List<AlertRule> get() = rules.value.values.toList()
    val storedMatches: List<AlertMatch> get() = matches.value.map { it.match }

    fun emitRules(items: List<AlertRule>) {
        rules.value = items.associateBy { it.id }
    }

    /** Seeds a match as if a previous cycle had recorded it. */
    fun seedMatch(ruleId: String, externalKey: String, matchedAt: Long = now, read: Boolean = false) {
        matches.value = matches.value + StoredMatch(AlertMatch(ruleId, externalKey, matchedAt), read)
    }

    override fun observeRules(dayStart: Long): Flow<List<AlertRuleOverview>> {
        calls += "observeRules($dayStart)"
        return combine(rules, matches) { byId, stored ->
            byId.values.sortedByDescending { it.createdAt }.map { rule ->
                val own = stored.filter { it.match.ruleId == rule.id }
                AlertRuleOverview(
                    rule = rule,
                    lastMatchedAt = own.maxOfOrNull { it.match.matchedAt },
                    matchesToday = own.count { it.match.matchedAt >= dayStart },
                )
            }
        }
    }

    override suspend fun rule(id: String): AlertRule? {
        calls += "rule($id)"
        return rules.value[id]
    }

    override suspend fun enabledRules(): List<AlertRule> {
        calls += "enabledRules"
        return rules.value.values.filter { it.isEnabled }
    }

    override suspend fun countRules(): Int = rules.value.size

    override suspend fun countEnabled(): Int = rules.value.values.count { it.isEnabled }

    override suspend fun save(draft: AlertRuleDraft, id: String?): AppResult<String> {
        calls += "save(${id ?: "new"})"
        if (failWrites) return AppResult.Failure(DomainError.Unknown)
        val ruleId = id ?: "rule-${nextId++}"
        val existing = rules.value[ruleId]
        val rule = draft.toRule(
            id = ruleId,
            now = now,
            sections = SECTIONS,
            createdAt = existing?.createdAt ?: now,
        )
        rules.value = rules.value + (ruleId to rule)
        return AppResult.Success(ruleId)
    }

    override suspend fun setEnabled(id: String, enabled: Boolean): AppResult<Unit> {
        calls += "setEnabled($id, $enabled)"
        if (failWrites) return AppResult.Failure(DomainError.Unknown)
        val rule = rules.value[id] ?: return AppResult.Success(Unit)
        rules.value = rules.value + (id to rule.copy(isEnabled = enabled, activeSince = now, updatedAt = now))
        return AppResult.Success(Unit)
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        calls += "delete($id)"
        if (failWrites) return AppResult.Failure(DomainError.Unknown)
        rules.value = rules.value - id
        matches.value = matches.value.filterNot { it.match.ruleId == id }
        return AppResult.Success(Unit)
    }

    override suspend fun recordMatches(candidates: List<AlertMatch>): List<AlertMatch> {
        calls += "recordMatches(${candidates.size})"
        val known = matches.value.map { it.match.ruleId to it.match.externalKey }.toSet()
        val fresh = candidates.filter { (it.ruleId to it.externalKey) !in known }
            .distinctBy { it.ruleId to it.externalKey }
        matches.value = matches.value + fresh.map { StoredMatch(it, read = false) }
        return fresh
    }

    override fun observeNews(): Flow<List<AlertNews>> = matches.map { stored ->
        stored.groupBy { it.match.externalKey }.mapNotNull { (key, own) ->
            publications[key]?.let { publication ->
                AlertNews(
                    publication = publication,
                    ruleNames = own.mapNotNull { rules.value[it.match.ruleId]?.name }.distinct(),
                    detectedAt = own.minOf { it.match.matchedAt },
                    isRead = own.all { it.read },
                )
            }
        }.sortedWith(compareByDescending<AlertNews> { it.publication.publicationDate }.thenByDescending { it.detectedAt })
    }

    override fun observeUnreadCount(): Flow<Int> = matches.map { stored ->
        stored.filter { !it.read }.map { it.match.externalKey }.distinct().size
    }

    override suspend fun markRead(externalKey: String): AppResult<Unit> {
        calls += "markRead($externalKey)"
        if (failWrites) return AppResult.Failure(DomainError.Unknown)
        matches.value = matches.value.map { if (it.match.externalKey == externalKey) it.copy(read = true) else it }
        return AppResult.Success(Unit)
    }

    override suspend fun markAllRead(): AppResult<Unit> {
        calls += "markAllRead"
        if (failWrites) return AppResult.Failure(DomainError.Unknown)
        matches.value = matches.value.map { it.copy(read = true) }
        return AppResult.Success(Unit)
    }

    private data class StoredMatch(val match: AlertMatch, val read: Boolean)

    private companion object {
        val SECTIONS = BocSectionRepositoryImpl().sections()
    }
}
