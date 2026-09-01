package com.jrblanco.boccantabria.data.source.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Reads for the search screen. **Nothing here writes.**
 *
 * A third data-access object over the same table, and the reason is the same one that split
 * [SavedPublicationDao] off: [PublicationDao] is the synchronisation's, and the rule that keeps the
 * stored bulletin whole —no deletes, and an update that is a deliberate allow-list— holds because
 * that file can be read in one go. So the line is: `PublicationDao` writes everything derived from
 * the source, including the searchable text and its backfill; this one only ever reads.
 *
 * **There is no delete here either.** A review that sees one appear should reject it.
 *
 * Two statements instead of one because Room cannot parameterise the direction of an `ORDER BY`.
 * The alternatives were a raw query, which throws away the compile-time verification that is half
 * the reason for using Room, or a `CASE WHEN` inside the `ORDER BY` that nobody would enjoy reading
 * twice. Fifteen duplicated lines of explicit SQL is the cheaper of the three.
 *
 * Both keep the same three ordering terms as the bulletin queries —date, numeric identifier, key—
 * which is what makes two runs of the same search agree.
 *
 * `publication_date` is stored as ISO text, so lexicographic order **is** chronological order and
 * `>=` and `<=` work on it directly, with no conversion.
 */
@Dao
interface PublicationSearchDao {

    @Query(
        """
        SELECT * FROM publications
        WHERE search_text LIKE :pattern ESCAPE '\'
          AND (:sectionCode    IS NULL OR section_code     = :sectionCode)
          AND (:subsectionCode IS NULL OR subsection_code  = :subsectionCode)
          AND (:issuer         IS NULL OR issuer           = :issuer)
          AND (:from           IS NULL OR publication_date >= :from)
          AND (:to             IS NULL OR publication_date <= :to)
        ORDER BY publication_date DESC, CAST(blob_id AS INTEGER) DESC, external_key DESC
        LIMIT :limit
        """,
    )
    @Suppress("LongParameterList")
    fun searchNewestFirst(
        pattern: String,
        sectionCode: String?,
        subsectionCode: String?,
        issuer: String?,
        from: String?,
        to: String?,
        limit: Int,
    ): Flow<List<PublicationEntity>>

    @Query(
        """
        SELECT * FROM publications
        WHERE search_text LIKE :pattern ESCAPE '\'
          AND (:sectionCode    IS NULL OR section_code     = :sectionCode)
          AND (:subsectionCode IS NULL OR subsection_code  = :subsectionCode)
          AND (:issuer         IS NULL OR issuer           = :issuer)
          AND (:from           IS NULL OR publication_date >= :from)
          AND (:to             IS NULL OR publication_date <= :to)
        ORDER BY publication_date ASC, CAST(blob_id AS INTEGER) ASC, external_key ASC
        LIMIT :limit
        """,
    )
    @Suppress("LongParameterList")
    fun searchOldestFirst(
        pattern: String,
        sectionCode: String?,
        subsectionCode: String?,
        issuer: String?,
        from: String?,
        to: String?,
        limit: Int,
    ): Flow<List<PublicationEntity>>

    /**
     * The issuers with something behind them.
     *
     * Taken from what is stored rather than from a fixed catalogue, so the filter never offers an
     * organisation with not a single announcement to show.
     */
    @Query("SELECT DISTINCT issuer FROM publications WHERE issuer IS NOT NULL ORDER BY issuer")
    fun observeIssuers(): Flow<List<String>>
}
