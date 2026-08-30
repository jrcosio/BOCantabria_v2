package com.jrblanco.boccantabria.domain.repository

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
 * - The flows do not terminate with an error: a local read failure emits an empty list.
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
}
