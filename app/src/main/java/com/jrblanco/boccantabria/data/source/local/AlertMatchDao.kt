package com.jrblanco.boccantabria.data.source.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes of what the rules caught.
 *
 * No delete here: matches go when their rule goes, through the cascade declared on the entity.
 *
 * The news query groups by **publication**, because that is what the person sees and counts: one
 * row per announcement naming every rule that caught it, and an unread counter that counts distinct
 * publications (FR-003; 012 research.md D-413). Room observes the three joined tables, so the tab
 * and the badge update on their own.
 */
@Dao
interface AlertMatchDao {

    /** Returns `-1` for a pair that already existed: the unique index does the deduplication. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(items: List<AlertMatchEntity>): List<Long>

    /**
     * One row per publication that matched, newest first.
     *
     * `GROUP_CONCAT` joins the rule names with `char(31)`, the ASCII unit separator the list converter
     * uses, so the repository can split them back without guessing. Written as `char(31)` because a
     * raw Kotlin string has no escapes and the character itself must not appear in source. Its order is not guaranteed and does not need
     * to be: «Coincide con A y B» reads the same either way.
     */
    @Query(
        """
        SELECT p.*,
               GROUP_CONCAT(r.name, char(31)) AS rule_names,
               MIN(m.matched_at) AS detected_at,
               MAX(CASE WHEN m.read_at IS NULL THEN 1 ELSE 0 END) AS unread
        FROM alert_matches m
        JOIN publications p ON p.external_key = m.external_key
        JOIN alert_rules r ON r.id = m.rule_id
        GROUP BY p.external_key
        ORDER BY p.publication_date DESC, detected_at DESC, p.external_key DESC
        """,
    )
    fun observeNews(): Flow<List<AlertNewsRow>>

    /** Distinct publications with an unread match: what the bell shows. */
    @Query("SELECT COUNT(DISTINCT external_key) FROM alert_matches WHERE read_at IS NULL")
    fun observeUnreadCount(): Flow<Int>

    /** Every match of the publication, idempotent. Zero rows is a legitimate result. */
    @Query("UPDATE alert_matches SET read_at = :now WHERE external_key = :externalKey AND read_at IS NULL")
    suspend fun markRead(externalKey: String, now: Long): Int

    @Query("UPDATE alert_matches SET read_at = :now WHERE read_at IS NULL")
    suspend fun markAllRead(now: Long): Int

    @Query("SELECT COUNT(*) FROM alert_matches")
    suspend fun count(): Int
}

/** A publication with what the matches say about it. */
data class AlertNewsRow(
    @Embedded val publication: PublicationEntity,
    @ColumnInfo(name = "rule_names") val ruleNames: String,
    @ColumnInfo(name = "detected_at") val detectedAt: Long,
    @ColumnInfo(name = "unread") val unread: Int,
)
