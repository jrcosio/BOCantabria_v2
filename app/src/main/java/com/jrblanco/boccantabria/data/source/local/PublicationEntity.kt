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
 *
 * [savedAt] belongs to the person, not to the source, and it is the one column a synchronisation
 * must never touch. That is not a convention anybody has to remember: it is absent from
 * `PublicationDao.updateColumns`, whose statement is a deliberate allow-list, and the insert ignores
 * conflicts. Same mechanism that already protects [firstSeenAt].
 *
 * [searchText] sits on the other side of that line and it is worth saying out loud, because it is
 * written into the very statement the allow-list protects: it is **derived from what the source
 * publishes**, so when the source corrects a title the searchable text has to be corrected with it.
 * Leaving it out would mean a corrected announcement stayed findable only by its old wording.
 *
 * It carries no index on purpose. A `LIKE '%…%'` cannot use one, so it would be writes and bytes
 * bought for nothing.
 */
@Entity(
    tableName = "publications",
    indices = [
        Index(value = ["blob_id"], unique = true),
        Index(value = ["publication_date"]),
        Index(value = ["section_code"]),
        Index(value = ["subsection_code"]),
        Index(value = ["edition_type"]),
        Index(value = ["saved_at"]),
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
    /** When the person saved it. `null` means not saved: there is no third state. */
    @ColumnInfo(name = "saved_at") val savedAt: Long? = null,
    /**
     * Everything searchable about the publication, folded to lower case and stripped of accents.
     *
     * An **empty** value means the row was written before this column existed: `buildSearchText`
     * can never return one, because a publication's title can never be blank. That is what lets the
     * backfill find its work without a flag stored anywhere.
     */
    @ColumnInfo(name = "search_text", defaultValue = "''") val searchText: String = "",
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

/**
 * The publication as the source describes it, ready to be stored.
 *
 * `savedAt` is deliberately left at its default: what the source publishes cannot invent a mark, and
 * an existing row's mark is out of reach anyway because the update statement does not name it.
 *
 * [searchText] is passed in rather than computed here: it needs the section catalogue, which this
 * file has no business knowing about and the repository already holds.
 */
internal fun Publication.toEntity(seenAt: Long, searchText: String): PublicationEntity = PublicationEntity(
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
    searchText = searchText,
)
