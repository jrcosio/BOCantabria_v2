package com.jrblanco.boccantabria.domain.repository

import com.jrblanco.boccantabria.domain.model.AlertCandidate
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.BulletinHeaderData
import com.jrblanco.boccantabria.domain.model.HomeSelection
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.model.SyncSummary
import kotlinx.coroutines.flow.Flow

/**
 * Access to the bulletin the device has stored.
 *
 * The stored copy is the **single source of truth**: the screen observes it and never reads the
 * network. [refresh] only writes. That is what lets content appear as each source lands instead
 * of after all nineteen finish, and what makes the screen work offline without a single extra
 * branch.
 *
 * Contract, same as the rest of the project:
 * - Nothing here throws. Failures travel as [AppResult.Failure].
 * - No publications is `Success(emptyList())`, never a failure.
 * - `CancellationException` is always rethrown.
 * - The flows never fail. A transient local read failure emits an empty list, is retried with a
 *   bounded budget and keeps observing afterwards; a persistent one leaves that empty list in place
 *   (feature 014, STAB-004).
 */
interface PublicationRepository {

    /** Publications matching [selection], newest first, in a stable order. */
    fun observePublications(selection: HomeSelection): Flow<List<Publication>>

    /**
     * One publication, by its stable key.
     *
     * Emits `null` when it is not stored — because it never was, or because it was cleared. That is
     * information, not a failure: the detail screen uses it to explain rather than show a blank.
     *
     * It keeps emitting, so a later synchronisation that corrects the title reaches an open detail
     * screen without anyone having to go back and in again.
     */
    fun observePublication(externalKey: String): Flow<Publication?>

    /** Date and count for the editorial header of [selection]. */
    fun observeHeader(selection: HomeSelection): Flow<BulletinHeaderData>

    /** Whether the stored copy is old enough to be worth refreshing. */
    suspend fun isCacheStale(): Boolean

    /**
     * Reads every enabled source and writes what it finds.
     *
     * Returns [AppResult.Failure] with [com.jrblanco.boccantabria.domain.model.DomainError.Network]
     * **only** when every source failed and nothing is stored. If sources failed but there is
     * content, the result is a success whose summary reports it.
     */
    suspend fun refresh(): AppResult<SyncSummary>

    /**
     * What a synchronisation cycle evaluates the alerts against: every stored publication still
     * marked as pending, with the instant it was stored.
     *
     * The mark is written with the row, in the same statement, and survives a process death; only
     * [markAlertsEvaluated] clears it. So a match that could not be recorded is not lost — the next
     * cycle reads it again (feature 014, STAB-003; research.md D-607). A read failure is a
     * [AppResult.Failure], never an empty list: the cycle must not mistake it for «nothing pending».
     */
    suspend fun pendingAlertCandidates(): AppResult<List<AlertCandidate>>

    /** Clears the pending mark on exactly [keys]. Touches nothing else. */
    suspend fun markAlertsEvaluated(keys: Set<String>): AppResult<Unit>

    /** The newest [limit] stored publications. What the alert form's preview is run against. */
    suspend fun newest(limit: Int): List<Publication>

    /** When any source last answered successfully, or `null` if none ever has. */
    suspend fun lastSuccessfulSyncAt(): Long?
}
