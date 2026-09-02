package com.jrblanco.boccantabria.data.source.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A stored AI summary.
 *
 * The primary key is the **publication**, not a key derived from the document hash and the model.
 * Two reasons, both practical. The screen has to observe the summary from the moment it opens, and
 * at that point the document may not be downloaded, so its hash is unknown: with a derived key there
 * would be nothing to observe. And one summary per publication is exactly what the interface shows
 * (research.md D-020).
 *
 * The four provenance columns give the same guarantee a derived key would: when any of them stops
 * matching what would be produced today, the row is **stale rather than absent** — still shown,
 * marked, with the option to make it again. It is never discarded here (FR-035).
 *
 * [summaryJson] holds the summary as a document rather than as columns. Flattening a shape with
 * seven nested lists into a table would be reimplementing the schema in SQL, and the schema is
 * already versioned by [schemaVersion].
 *
 * The three token counts are what the request **actually** cost, as reported by the service. They
 * are kept to calibrate the budget against reality rather than against the estimate. No column here
 * holds document content beyond the summary itself, which is what gets shown.
 */
@Entity(tableName = "ai_summaries")
data class AiSummaryEntity(
    @PrimaryKey @ColumnInfo(name = "external_key") val externalKey: String,
    @ColumnInfo(name = "pdf_sha256") val pdfSha256: String,
    @ColumnInfo(name = "model_id") val modelId: String,
    @ColumnInfo(name = "prompt_version") val promptVersion: String,
    @ColumnInfo(name = "schema_version") val schemaVersion: String,
    @ColumnInfo(name = "summary_json") val summaryJson: String,
    @ColumnInfo(name = "created_at") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "prompt_tokens") val promptTokens: Int,
    @ColumnInfo(name = "completion_tokens") val completionTokens: Int,
    @ColumnInfo(name = "total_tokens") val totalTokens: Int,
    @ColumnInfo(name = "system_fingerprint") val systemFingerprint: String?,
)
