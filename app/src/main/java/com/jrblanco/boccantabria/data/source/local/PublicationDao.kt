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
 * review that sees one appear here should reject it. (The project's one delete is `AlertRuleDao.delete`,
 * over the person's alert rules, never over this table.)
 *
 * Every query orders by date, then by numeric identifier, then by key. The last term is what
 * makes two runs agree even though the nineteen sources answer in a different order each time.
 *
 * This is the synchronisation's data-access object: **everything the source publishes, and things
 * derived from it, are written here** — including the searchable text and its backfill. What
 * belongs to the person is written elsewhere, by `SavedPublicationDao`, and reading for a search
 * happens elsewhere too, in `PublicationSearchDao`. Keeping this file to one owner is what lets the
 * rule above be read in one go.
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

    /**
     * The rows a synchronisation cycle evaluates the alerts against: what was inserted and not yet
     * evaluated, whichever cycle inserted it (feature 014). Same order as every list of the bulletin.
     */
    @Query(
        """
        SELECT * FROM publications
        WHERE pending_alert_evaluation = 1
        ORDER BY publication_date DESC, CAST(blob_id AS INTEGER) DESC, external_key DESC
        """,
    )
    suspend fun pendingAlertEvaluation(): List<PublicationEntity>

    /** Clears the mark on exactly [keys]; touches nothing else. Callers chunk the `IN` list at 900. */
    @Query("UPDATE publications SET pending_alert_evaluation = 0 WHERE external_key IN (:keys)")
    suspend fun markAlertsEvaluated(keys: List<String>): Int

    /** The newest rows, for the alert form's preview. Same order as every list of the bulletin. */
    @Query(
        """
        SELECT * FROM publications
        ORDER BY publication_date DESC, CAST(blob_id AS INTEGER) DESC, external_key DESC
        LIMIT :limit
        """,
    )
    suspend fun newest(limit: Int): List<PublicationEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(items: List<PublicationEntity>): List<Long>

    /**
     * Updates everything a source can change, and **nothing else**. `first_seen_at` is absent
     * from the statement on purpose: it records when the application first learnt of the
     * announcement, and a later sighting must not rewrite history. `saved_at` is absent for the
     * stronger version of the same reason: it belongs to the person, and `SavedPublicationDaoTest`
     * is the regression that keeps it out.
     *
     * `search_text` **is** here, and the difference is worth stating: it is derived from the title,
     * the issuer and the classification the source publishes, so when the source corrects a title
     * the searchable text has to be corrected with it. Leaving it out would mean a corrected
     * announcement stayed findable only by its old wording.
     *
     * `pending_alert_evaluation` (feature 014) is absent for the same reason as `first_seen_at`: a
     * publication the store already had is not news, however many times the source corrects it.
     * Adding it here would make every corrected announcement fire the alerts again;
     * `PublicationDaoTest` is the regression that keeps it out.
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
            last_seen_at = :lastSeenAt,
            search_text = :searchText
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
        searchText: String,
    )

    /**
     * Inserts what is new and refreshes what already existed, in one transaction, reporting how
     * many of each so the synchronisation summary can be built without a second pass.
     *
     * Since feature 012 it also reports **which** keys were new. They are taken from the rows the
     * insert really wrote, not from the partition: the unique index on `blob_id` can reject a row
     * whose key did not exist, and that row is not new (012 research.md D-401).
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
        val insertedKeys = toInsert
            .filterIndexed { index, _ -> insertedIds[index] != IGNORED_ROW_ID }
            .map { it.externalKey }
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
                searchText = entity.searchText,
            )
        }
        return UpsertCounts(
            inserted = insertedKeys.size,
            updated = toUpdate.size,
            insertedKeys = insertedKeys,
        )
    }

    /**
     * Rows written before the searchable text existed.
     *
     * An empty `search_text` is a trustworthy marker of exactly that: `buildSearchText` can never
     * return an empty string, because a publication's title can never be blank. So no flag has to be
     * stored anywhere and the work can be picked up again wherever it was left.
     */
    @Query("SELECT * FROM publications WHERE search_text = '' LIMIT :limit")
    suspend fun withoutSearchText(limit: Int): List<PublicationEntity>

    /** Fills in one row's searchable text. Touches nothing else — least of all the saved mark. */
    @Query("UPDATE publications SET search_text = :searchText WHERE external_key = :externalKey")
    suspend fun setSearchText(externalKey: String, searchText: String)

    private companion object {
        const val IGNORED_ROW_ID = -1L

        /** SQLite caps the bound variables of one statement; the `IN` list has to respect it. */
        const val SQLITE_VARIABLE_LIMIT = 900
    }
}

/**
 * How a single upsert broke down. Feeds the synchronisation summary.
 *
 * [insertedKeys] are the rows that did not exist before. Until feature 014 they were what the alerts
 * were evaluated against; now the same rows carry the pending mark in the store, and these keys feed
 * the summary, the log and the tests. `inserted` is kept as a count for the callers that only need
 * one.
 */
data class UpsertCounts(
    val inserted: Int = 0,
    val updated: Int = 0,
    val insertedKeys: List<String> = emptyList(),
)
