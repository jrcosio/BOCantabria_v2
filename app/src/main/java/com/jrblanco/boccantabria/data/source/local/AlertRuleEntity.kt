package com.jrblanco.boccantabria.data.source.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jrblanco.boccantabria.domain.model.AlertRule
import com.jrblanco.boccantabria.domain.model.KeywordMatchMode

/**
 * An alert rule as it is stored.
 *
 * The lists use the project's `List<String>` converter —the ASCII unit separator— rather than JSON:
 * it is registered, tested and already in use for the issuer hierarchy (012 research.md D-411).
 * Keywords are stored **as typed**; normalising happens when comparing, so a change in the
 * normalisation cannot desynchronise what is stored from what is matched (D-408).
 *
 * [matchMode] is stored by name and read tolerantly: an unknown name is the form's default.
 */
@Entity(
    tableName = "alert_rules",
    indices = [Index(value = ["enabled"])],
)
data class AlertRuleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "keywords") val keywords: List<String>,
    @ColumnInfo(name = "match_mode") val matchMode: String,
    @ColumnInfo(name = "section_codes") val sectionCodes: List<String>,
    @ColumnInfo(name = "organization_query") val organizationQuery: String?,
    @ColumnInfo(name = "enabled") val enabled: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "active_since") val activeSince: Long,
)

internal fun AlertRuleEntity.toDomain(): AlertRule = AlertRule(
    id = id,
    name = name,
    keywords = keywords,
    matchMode = KeywordMatchMode.byNameOrDefault(matchMode),
    sectionCodes = sectionCodes.toSet(),
    organizationQuery = organizationQuery,
    isEnabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    activeSince = activeSince,
)

internal fun AlertRule.toEntity(): AlertRuleEntity = AlertRuleEntity(
    id = id,
    name = name,
    keywords = keywords,
    matchMode = matchMode.name,
    sectionCodes = sectionCodes.sorted(),
    organizationQuery = organizationQuery,
    enabled = isEnabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    activeSince = activeSince,
)
