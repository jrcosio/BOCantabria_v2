package com.jrblanco.boccantabria.data.source.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jrblanco.boccantabria.domain.model.EditionType
import com.jrblanco.boccantabria.domain.model.IdSource
import com.jrblanco.boccantabria.domain.model.ParserWarning
import com.jrblanco.boccantabria.domain.model.Publication
import java.time.LocalDate

/**
 * A publication as it is stored.
 *
 * The unique index on `blob_id` does the deduplication the specification asks for: SQLite allows
 * many nulls in a unique index, so publications without an identifier —which fall back to the URL
 * or to a content digest— are unaffected.
 *
 * [firstSeenAt] is written once and never touched again; [lastSeenAt] moves on every sighting.
 * Together they are the only record of how long the application has known about an announcement,
 * which the source itself does not provide.
 */
@Entity(
    tableName = "publications",
    indices = [
        Index(value = ["blob_id"], unique = true),
        Index(value = ["publication_date"]),
        Index(value = ["section_code"]),
        Index(value = ["subsection_code"]),
        Index(value = ["edition_type"]),
        Index(value = ["feed_id", "publication_date"]),
    ],
)
data class PublicationEntity(
    @PrimaryKey
    @ColumnInfo(name = "external_key") val externalKey: String,
    @ColumnInfo(name = "blob_id") val blobId: String?,
    @ColumnInfo(name = "id_source") val idSource: IdSource,
    @ColumnInfo(name = "feed_id") val feedId: String,
    @ColumnInfo(name = "section_code") val sectionCode: String,
    @ColumnInfo(name = "subsection_code") val subsectionCode: String?,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "issuer") val issuer: String?,
    @ColumnInfo(name = "organization_path") val organizationPath: List<String>,
    @ColumnInfo(name = "edition_type") val editionType: EditionType,
    @ColumnInfo(name = "publication_date") val publicationDate: LocalDate,
    @ColumnInfo(name = "document_url") val documentUrl: String,
    @ColumnInfo(name = "raw_categories") val rawCategories: String?,
    @ColumnInfo(name = "warnings") val warnings: Set<ParserWarning>,
    @ColumnInfo(name = "first_seen_at") val firstSeenAt: Long,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
)

internal fun PublicationEntity.toDomain(): Publication = Publication(
    externalKey = externalKey,
    blobId = blobId,
    idSource = idSource,
    feedId = feedId,
    sectionCode = sectionCode,
    subsectionCode = subsectionCode,
    title = title,
    issuer = issuer,
    organizationPath = organizationPath,
    editionType = editionType,
    publicationDate = publicationDate,
    documentUrl = documentUrl,
    rawCategories = rawCategories,
    warnings = warnings,
)

internal fun Publication.toEntity(seenAt: Long): PublicationEntity = PublicationEntity(
    externalKey = externalKey,
    blobId = blobId,
    idSource = idSource,
    feedId = feedId,
    sectionCode = sectionCode,
    subsectionCode = subsectionCode,
    title = title,
    issuer = issuer,
    organizationPath = organizationPath,
    editionType = editionType,
    publicationDate = publicationDate,
    documentUrl = documentUrl,
    rawCategories = rawCategories,
    warnings = warnings,
    firstSeenAt = seenAt,
    lastSeenAt = seenAt,
)
