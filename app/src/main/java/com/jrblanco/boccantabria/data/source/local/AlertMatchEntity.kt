package com.jrblanco.boccantabria.data.source.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One rule caught one publication at one instant.
 *
 * The unique index on `(rule_id, external_key)` **is** the deduplication the specification asks for
 * (FR-042): the insert ignores conflicts, so a pair recorded twice is silently dropped, and only
 * what the store really inserted is delivered (012 research.md D-410).
 *
 * The foreign key to `alert_rules` cascades: deleting a rule takes its matches with it. There is
 * **no** foreign key to `publications`: publications are never deleted, so the constraint would only
 * tax every write the synchronisation makes.
 *
 * [readAt] `null` means pending. It is the person's, and it is written per publication: marking a
 * piece of news read marks every match of that publication.
 */
@Entity(
    tableName = "alert_matches",
    foreignKeys = [
        ForeignKey(
            entity = AlertRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["rule_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["rule_id", "external_key"], unique = true),
        Index(value = ["external_key"]),
        Index(value = ["read_at"]),
        Index(value = ["rule_id"]),
    ],
)
data class AlertMatchEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "rule_id") val ruleId: String,
    @ColumnInfo(name = "external_key") val externalKey: String,
    @ColumnInfo(name = "matched_at") val matchedAt: Long,
    @ColumnInfo(name = "read_at") val readAt: Long? = null,
)
