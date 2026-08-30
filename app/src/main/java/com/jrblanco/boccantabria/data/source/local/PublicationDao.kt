package com.jrblanco.boccantabria.data.source.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes of stored publications.
 *
 * **There is deliberately no delete.** Falling out of a source's hundred-item window must not
 * remove anything, and the surest way to guarantee that is for the statement not to exist. A
 * review that sees one appear here should reject it.
 *
 * Every query orders by date, then by numeric identifier, then by key. The last term is what
 * makes two runs agree even though the nineteen sources answer in a different order each time.
 */
@Dao
interface PublicationDao {

    @Query(
        """
        SELECT * FROM publications
        WHERE publication_date = (SELECT MAX(publication_date) FROM publications)
        ORDER BY publication_date DESC, CAST(blob_id AS INTEGER) DESC, external_key DESC
        """,
    )
    fun observeTodaysBulletin(): Flow<List<PublicationEntity>>

    @Query(
        """
        SELECT * FROM publications
        WHERE section_code = :sectionCode
        ORDER BY publication_date DESC, CAST(blob_id AS INTEGER) DESC, external_key DESC
        """,
    )
    fun observeBySection(sectionCode: String): Flow<List<PublicationEntity>>

    @Query(
        """
        SELECT * FROM publications
        WHERE subsection_code = :subsectionCode
        ORDER BY publication_date DESC, CAST(blob_id AS INTEGER) DESC, external_key DESC
        """,
    )
    fun observeBySubsection(subsectionCode: String): Flow<List<PublicationEntity>>

    /** Emits `null` when the key is not stored. The detail screen turns that into an explanation. */
    @Query("SELECT * FROM publications WHERE external_key = :externalKey")
    fun observePublication(externalKey: String): Flow<PublicationEntity?>

    @Query("SELECT COUNT(*) FROM publications")
    suspend fun count(): Int

    @Query("SELECT external_key FROM publications WHERE external_key IN (:keys)")
    suspend fun existingKeys(keys: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(items: List<PublicationEntity>): List<Long>

    /**
     * Updates everything a source can change, and **nothing else**. `first_seen_at` is absent
     * from the statement on purpose: it records when the application first learnt of the
     * announcement, and a later sighting must not rewrite history.
     */
    @Query(
        """
        UPDATE publications SET
            blob_id = :blobId,
            id_source = :idSource,
            feed_id = :feedId,
            section_code = :sectionCode,
            subsection_code = :subsectionCode,
            title = :title,
            issuer = :issuer,
            organization_path = :organizationPath,
            edition_type = :editionType,
            publication_date = :publicationDate,
            document_url = :documentUrl,
            raw_categories = :rawCategories,
            warnings = :warnings,
            last_seen_at = :lastSeenAt
        WHERE external_key = :externalKey
        """,
    )
    @Suppress("LongParameterList")
    suspend fun updateColumns(
        externalKey: String,
        blobId: String?,
        idSource: String,
        feedId: String,
        sectionCode: String,
        subsectionCode: String?,
        title: String,
        issuer: String?,
        organizationPath: String,
        editionType: String,
        publicationDate: String,
        documentUrl: String,
        rawCategories: String?,
        warnings: String,
        lastSeenAt: Long,
    )

    /**
     * Inserts what is new and refreshes what already existed, in one transaction, reporting how
     * many of each so the synchronisation summary can be built without a second pass.
     */
    @Transaction
    suspend fun upsertAll(items: List<PublicationEntity>): UpsertCounts {
        if (items.isEmpty()) return UpsertCounts()

        val converters = Converters()
        val existing = items.map { it.externalKey }
            .chunked(SQLITE_VARIABLE_LIMIT)
            .flatMap { existingKeys(it) }
            .toSet()
        val (toUpdate, toInsert) = items.partition { it.externalKey in existing }

        val insertedIds = insert(toInsert)
        toUpdate.forEach { entity ->
            updateColumns(
                externalKey = entity.externalKey,
                blobId = entity.blobId,
                idSource = entity.idSource.name,
                feedId = entity.feedId,
                sectionCode = entity.sectionCode,
                subsectionCode = entity.subsectionCode,
                title = entity.title,
                issuer = entity.issuer,
                organizationPath = converters.listToString(entity.organizationPath),
                editionType = entity.editionType.name,
                publicationDate = entity.publicationDate.toString(),
                documentUrl = entity.documentUrl,
                rawCategories = entity.rawCategories,
                warnings = converters.warningsToString(entity.warnings),
                lastSeenAt = entity.lastSeenAt,
            )
        }
        return UpsertCounts(
            inserted = insertedIds.count { it != IGNORED_ROW_ID },
            updated = toUpdate.size,
        )
    }

    private companion object {
        const val IGNORED_ROW_ID = -1L

        /** SQLite caps the bound variables of one statement; the `IN` list has to respect it. */
        const val SQLITE_VARIABLE_LIMIT = 900
    }
}

/** How a single upsert broke down. Feeds the synchronisation summary. */
data class UpsertCounts(val inserted: Int = 0, val updated: Int = 0)
