package com.jrblanco.boccantabria.domain.repository

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.Publication
import kotlinx.coroutines.flow.Flow

/**
 * What the person has marked to come back to.
 *
 * Separate from [PublicationRepository] because the two answer to different owners: that one is
 * about what the source publishes, this one about what the person keeps. Keeping them apart also
 * means the synchronisation's contract never has to mention marks.
 *
 * Contract, same as the rest of the project:
 * - Nothing here throws. Failures travel as [AppResult.Failure].
 * - `CancellationException` is always rethrown.
 * - The flows do not terminate with an error: a local read failure emits an empty result and stays
 *   alive. A terminated flow leaves the screen with no state at all, which reads as a frozen
 *   application rather than as an empty one.
 * - Nothing saved is `Success(emptyList())` / an empty set, never a failure.
 */
interface SavedPublicationRepository {

    /**
     * The saved publications, **most recently saved first**.
     *
     * The order comes from the store, not from the screen: it is the instant of the mark, which is
     * the one thing the presentation layer has no way of knowing.
     */
    fun observeSaved(): Flow<List<Publication>>

    /**
     * The keys of everything saved.
     *
     * One flow serves both screens that need to draw the state of a single publication: a card asks
     * whether its key is in here, and so does the detail screen. Cheaper than a dedicated flow per
     * publication, and the set is small by definition — a person writes it by hand.
     */
    fun observeSavedKeys(): Flow<Set<String>>

    /**
     * Marks or unmarks [externalKey].
     *
     * Unmarking **never** removes the publication: it clears the mark and leaves the stored copy
     * where it was. A key that is not stored is not an error — nothing is created and nothing fails.
     */
    suspend fun setSaved(externalKey: String, saved: Boolean): AppResult<Unit>
}
