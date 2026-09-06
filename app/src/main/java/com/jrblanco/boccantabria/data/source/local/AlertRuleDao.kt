package com.jrblanco.boccantabria.data.source.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes of the alert rules.
 *
 * **This file holds the only `DELETE` statement of the project, and it is here on purpose.** The rule
 * that no data-access object deletes exists to protect the stored bulletin: a source publishes only
 * its last hundred announcements, so removing what falls out of that window would erase the
 * application's own archive. An alert rule is not the bulletin's — it is the person's, created by
 * hand and removed by hand behind a confirmation dialog. Deleting it is what "Eliminar" means. The
 * store cascades to its matches; no publication is touched, and `AlertRuleDaoTest` is the regression
 * that keeps it so (012 research.md D-412).
 *
 * Everything else the person owns about publications —the saved mark— stays in
 * `SavedPublicationDao`, and everything the source owns stays in `PublicationDao`. Neither declares a
 * delete, and neither should.
 */
@Dao
interface AlertRuleDao {

    /**
     * Every rule with what its card shows, newest first.
     *
     * The two aggregates come from the matches, so Room re-emits when either table changes.
     * `matches_today` counts from [dayStart], which the caller computes in the person's zone.
     */
    @Query(
        """
        SELECT r.*,
               MAX(m.matched_at) AS last_matched_at,
               COALESCE(SUM(CASE WHEN m.matched_at >= :dayStart THEN 1 ELSE 0 END), 0) AS matches_today
        FROM alert_rules r
        LEFT JOIN alert_matches m ON m.rule_id = r.id
        GROUP BY r.id
        ORDER BY r.created_at DESC, r.id DESC
        """,
    )
    fun observeRules(dayStart: Long): Flow<List<AlertRuleWithStats>>

    @Query("SELECT * FROM alert_rules WHERE id = :id")
    suspend fun byId(id: String): AlertRuleEntity?

    @Query("SELECT * FROM alert_rules WHERE enabled = 1 ORDER BY created_at ASC, id ASC")
    suspend fun enabledRules(): List<AlertRuleEntity>

    @Query("SELECT COUNT(*) FROM alert_rules")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM alert_rules WHERE enabled = 1")
    suspend fun countEnabled(): Int

    @Upsert
    suspend fun upsert(rule: AlertRuleEntity)

    /** Pauses or re-enables. Renews `active_since`: a re-enabled rule starts from now (FR-040). */
    @Query("UPDATE alert_rules SET enabled = :enabled, active_since = :now, updated_at = :now WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, now: Long): Int

    /** The project's one delete. Rules belong to the person; the cascade removes their matches. */
    @Query("DELETE FROM alert_rules WHERE id = :id")
    suspend fun delete(id: String): Int
}

/** A rule row with the two aggregates its card shows. */
data class AlertRuleWithStats(
    @Embedded val rule: AlertRuleEntity,
    @ColumnInfo(name = "last_matched_at") val lastMatchedAt: Long?,
    @ColumnInfo(name = "matches_today") val matchesToday: Int,
)
