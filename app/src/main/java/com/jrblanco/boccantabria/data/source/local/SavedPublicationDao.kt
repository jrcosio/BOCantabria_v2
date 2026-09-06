package com.jrblanco.boccantabria.data.source.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes of the saved mark.
 *
 * A separate data-access object over the same table, on purpose. [PublicationDao] is the
 * synchronisation's: its header states the rule that keeps the stored bulletin whole, and that rule
 * holds because the file can be read in one go. Adding the first write that does not come from the
 * source would dilute it.
 *
 * **There is deliberately no delete here either.** Unsaving is `setSavedAt(key, null)`: the mark goes
 * and the publication stays exactly where it was. A review that sees a delete statement appear over
 * `publications` —in this file or any other— should reject it. The one delete of the project is
 * `AlertRuleDao.delete`, over the person's alert rules, and the reason is written there.
 */
@Dao
interface SavedPublicationDao {

    /**
     * The saved publications, most recently saved first.
     *
     * The second term of the ordering is not decoration: two marks made in the same millisecond
     * would tie, and without a tie-breaker the list could come out in a different order between two
     * reads. Same reason the three bulletin queries carry three terms.
     */
    @Query(
        """
        SELECT * FROM publications
        WHERE saved_at IS NOT NULL
        ORDER BY saved_at DESC, external_key DESC
        """,
    )
    fun observeSaved(): Flow<List<PublicationEntity>>

    /** Just the keys. What a card and a detail screen need to draw their own state. */
    @Query("SELECT external_key FROM publications WHERE saved_at IS NOT NULL")
    fun observeSavedKeys(): Flow<List<String>>

    /**
     * Writes or clears the mark, and reports how many rows it touched.
     *
     * **Zero is a legitimate result**: the key is not stored. Nothing is created and nothing fails,
     * which is what lets the test assert that case instead of guessing at it.
     */
    @Query("UPDATE publications SET saved_at = :savedAt WHERE external_key = :externalKey")
    suspend fun setSavedAt(externalKey: String, savedAt: Long?): Int
}
