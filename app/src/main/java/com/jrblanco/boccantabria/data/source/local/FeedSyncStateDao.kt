package com.jrblanco.boccantabria.data.source.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface FeedSyncStateDao {

    @Query("SELECT * FROM feed_sync_state WHERE feed_id = :feedId")
    suspend fun byFeedId(feedId: String): FeedSyncStateEntity?

    @Query("SELECT * FROM feed_sync_state")
    suspend fun all(): List<FeedSyncStateEntity>

    /** The most recent successful conversation with any source. Basis of the staleness rule. */
    @Query("SELECT MAX(last_success_at) FROM feed_sync_state")
    suspend fun lastSuccessAt(): Long?

    @Upsert
    suspend fun upsert(state: FeedSyncStateEntity)
}
