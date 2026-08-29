package com.jrblanco.boccantabria.data.source.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What is known about the last conversation with one source.
 *
 * [bodyHash] is the only way to tell whether a source has changed: the service publishes neither
 * `ETag` nor `Last-Modified`. [etag] and [lastModified] are here anyway so that the day it starts
 * publishing them nothing has to be migrated.
 */
@Entity(tableName = "feed_sync_state")
data class FeedSyncStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "feed_id") val feedId: String,
    @ColumnInfo(name = "body_hash") val bodyHash: String? = null,
    @ColumnInfo(name = "etag") val etag: String? = null,
    @ColumnInfo(name = "last_modified") val lastModified: String? = null,
    @ColumnInfo(name = "last_success_at") val lastSuccessAt: Long? = null,
    @ColumnInfo(name = "consecutive_failures") val consecutiveFailures: Int = 0,
)
