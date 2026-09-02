package com.jrblanco.boccantabria.data.source.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes of the stored AI summaries.
 *
 * **There is no delete statement here either**, like the other three data-access objects of this
 * project. Regenerating is an upsert, not a delete followed by an insert: for the moment between
 * the two there would be no summary at all, and a review that sees a delete statement appear should
 * reject it.
 *
 * There is no index beyond the primary key, on purpose: every query here looks up by key.
 */
@Dao
interface AiSummaryDao {

    /** Emits `null` while there is no summary, which is the normal state of a publication. */
    @Query("SELECT * FROM ai_summaries WHERE external_key = :externalKey")
    fun observe(externalKey: String): Flow<AiSummaryEntity?>

    @Query("SELECT * FROM ai_summaries WHERE external_key = :externalKey")
    suspend fun byExternalKey(externalKey: String): AiSummaryEntity?

    @Upsert
    suspend fun upsert(entity: AiSummaryEntity)
}
