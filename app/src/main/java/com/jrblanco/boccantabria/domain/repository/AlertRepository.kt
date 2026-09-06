package com.jrblanco.boccantabria.domain.repository

import com.jrblanco.boccantabria.domain.model.AlertMatch
import com.jrblanco.boccantabria.domain.model.AlertNews
import com.jrblanco.boccantabria.domain.model.AlertRule
import com.jrblanco.boccantabria.domain.model.AlertRuleDraft
import com.jrblanco.boccantabria.domain.model.AlertRuleOverview
import com.jrblanco.boccantabria.domain.model.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * The person's alert rules and what they caught.
 *
 * A repository of its own, for the same reason [SavedPublicationRepository] is: it answers to the
 * person, [PublicationRepository] answers to the source. Keeping them apart also keeps the
 * synchronisation's contract free of rules.
 *
 * Contract, same as the rest of the project:
 * - Nothing here throws. Failures travel as [AppResult.Failure].
 * - `CancellationException` is always rethrown.
 * - The flows do not terminate with an error: a local read failure emits an empty result and stays
 *   alive.
 * - Nothing stored is an empty list / zero, never a failure.
 *
 * **This is the one repository of the project that deletes**: [delete] removes a rule and, through
 * the store's cascade, its matches. Rules belong to the person, and the person asked with a
 * confirmation dialog in front. No publication is ever touched (research.md D-412).
 */
interface AlertRepository {

    /**
     * Every rule with what its card shows, newest first.
     *
     * @param dayStart the start of the caller's local day, so «N coincidencias hoy» is counted where
     *   the person is and not where the server is.
     */
    fun observeRules(dayStart: Long): Flow<List<AlertRuleOverview>>

    suspend fun rule(id: String): AlertRule?

    /** The rules a cycle evaluates. Read **before** synchronising (research.md D-405). */
    suspend fun enabledRules(): List<AlertRule>

    suspend fun countRules(): Int

    suspend fun countEnabled(): Int

    /**
     * Creates when [id] is `null`, replaces otherwise. Both renew `activeSince`, so an edited rule
     * only fires for what is detected after the edit (FR-028, FR-040). Returns the rule's id.
     */
    suspend fun save(draft: AlertRuleDraft, id: String?): AppResult<String>

    /** Pauses or re-enables. Re-enabling renews `activeSince` (FR-040). */
    suspend fun setEnabled(id: String, enabled: Boolean): AppResult<Unit>

    /** Removes the rule and its matches. Never a publication. */
    suspend fun delete(id: String): AppResult<Unit>

    /**
     * Records the matches a cycle found and returns **only the ones that were really new**: a pair
     * already recorded is silently skipped by the store's unique index, and must not be delivered
     * again (FR-042, FR-043).
     */
    suspend fun recordMatches(candidates: List<AlertMatch>): List<AlertMatch>

    /** The publications that matched, one row each, newest first. */
    fun observeNews(): Flow<List<AlertNews>>

    /** Distinct publications with an unread match. What the badge shows (FR-002, FR-003). */
    fun observeUnreadCount(): Flow<Int>

    /** Marks every match of the publication read. A key with no matches is a success. */
    suspend fun markRead(externalKey: String): AppResult<Unit>

    suspend fun markAllRead(): AppResult<Unit>
}
