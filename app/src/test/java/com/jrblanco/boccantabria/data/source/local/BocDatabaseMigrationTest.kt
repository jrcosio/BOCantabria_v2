package com.jrblanco.boccantabria.data.source.local

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.domain.model.EditionType
import com.jrblanco.boccantabria.domain.model.IdSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/**
 * The upgrades between stored versions, against databases that already have rows.
 *
 * This is the failure with the worst consequences in the whole feature and the only one that is
 * **invisible on a clean install**: a missing or mismatched migration throws when the database is
 * opened, and only on a device that already had `boc.db`.
 *
 * Written by hand rather than with `MigrationTestHelper`, and the reason is worth keeping: every
 * Android constructor of that helper needs an `Instrumentation` and loads the schema from the test
 * package's assets, which would mean either shipping the schema inside the APK or moving this check
 * to the gate that needs an emulator. This version costs nothing extra and is stronger where it
 * matters — it opens the database through `Room.databaseBuilder`, the very call production uses, so
 * what passes here is what will happen on a real phone (research.md D-003).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class BocDatabaseMigrationTest {

    private lateinit var databaseFile: File

    @Before
    fun setUp() {
        databaseFile = ApplicationProvider.getApplicationContext<Application>()
            .getDatabasePath(DATABASE_NAME)
        databaseFile.parentFile?.mkdirs()
        databaseFile.delete()
    }

    @Test
    fun `a version 1 database keeps its publications and gains the saved column`() = runTest {
        writeVersionOneDatabaseWithOnePublication()

        val database = openWithRoom()
        try {
            val stored = database.publicationDao().observePublication("boc:1").first()

            assertNotNull("la publicación de la versión 1 no sobrevivió a la migración", stored)
            assertEquals("AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva.", stored!!.title)
            assertEquals(1_000L, stored.firstSeenAt)
            assertEquals(2_000L, stored.lastSeenAt)
            // La columna nueva existe y la fila que ya estaba queda como «no guardada», que es lo
            // correcto: nadie la había guardado.
            assertNull(stored.savedAt)
        } finally {
            database.close()
        }
    }

    @Test
    fun `the sync state of a version 1 database also survives`() = runTest {
        writeVersionOneDatabaseWithOnePublication()

        val database = openWithRoom()
        try {
            assertEquals("hash-de-la-version-1", database.feedSyncStateDao().byFeedId("6802081")?.bodyHash)
        } finally {
            database.close()
        }
    }

    // ---------- Version 2 to version 3: the searchable text ----------

    @Test
    fun `a version 2 database keeps everything and gains the search column`() = runTest {
        writeVersionTwoDatabaseWithOneSavedPublication()

        val database = openWithRoom()
        try {
            val stored = database.publicationDao().observePublication("boc:1").first()

            assertNotNull("la publicación de la versión 2 no sobrevivió a la migración", stored)
            assertEquals("AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva.", stored!!.title)
            // La marca es de la persona y la migración no la toca.
            assertEquals(java.lang.Long.valueOf(9_000L), stored.savedAt)
            // La columna nueva existe y llega vacía, que es exactamente lo que la convierte en
            // marcador de «fila anterior a esta versión» para el relleno.
            assertEquals("", stored.searchText)
        } finally {
            database.close()
        }
    }

    @Test
    fun `the rows a version 2 database left behind are the ones the backfill has to find`() = runTest {
        writeVersionTwoDatabaseWithOneSavedPublication()

        val database = openWithRoom()
        try {
            assertEquals(
                listOf("boc:1"),
                database.publicationDao().withoutSearchText(limit = 10).map { it.externalKey },
            )
        } finally {
            database.close()
        }
    }

    /**
     * Somebody who skipped a release goes from 1 to 3 in one open, which is why the 1→2 step stays
     * declared even though nothing writes version 1 any more.
     */
    @Test
    fun `a version 1 database can reach version 3 in one go`() = runTest {
        writeVersionOneDatabaseWithOnePublication()

        val database = openWithRoom()
        try {
            val stored = database.publicationDao().observePublication("boc:1").first()

            assertNotNull("la publicación de la versión 1 no llegó a la versión 3", stored)
            assertNull(stored!!.savedAt)
            assertEquals("", stored.searchText)
        } finally {
            database.close()
        }
    }

    @Test
    fun `the saved mark is writable right after the upgrade`() = runTest {
        writeVersionOneDatabaseWithOnePublication()

        val database = openWithRoom()
        try {
            val affected = database.savedPublicationDao().setSavedAt("boc:1", 5_000L)

            assertEquals(1, affected)
            assertEquals(listOf("boc:1"), database.savedPublicationDao().observeSavedKeys().first())
        } finally {
            database.close()
        }
    }

    // ---------- Version 3 to version 4: the AI summaries ----------

    /**
     * The new table is empty, and that is the whole point: unlike version 3, there is nothing to
     * backfill. Having no summary is the normal state of a publication.
     */
    @Test
    fun `a version 3 database keeps everything and gains the summaries table`() = runTest {
        writeVersionThreeDatabaseWithOnePublication()

        val database = openWithRoom()
        try {
            val stored = database.publicationDao().observePublication("boc:1").first()

            assertNotNull("la publicación de la versión 3 no sobrevivió a la migración", stored)
            assertEquals("AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva.", stored!!.title)
            assertEquals(java.lang.Long.valueOf(9_000L), stored.savedAt)
            assertEquals("ayuntamiento de pielagos aprobacion definitiva", stored.searchText)
            // La tabla nueva existe y está vacía.
            assertNull(database.aiSummaryDao().byExternalKey("boc:1"))
        } finally {
            database.close()
        }
    }

    @Test
    fun `a summary is writable right after the upgrade`() = runTest {
        writeVersionThreeDatabaseWithOnePublication()

        val database = openWithRoom()
        try {
            database.aiSummaryDao().upsert(summaryEntity())

            assertEquals(
                "a".repeat(64),
                database.aiSummaryDao().byExternalKey("boc:1")?.pdfSha256,
            )
        } finally {
            database.close()
        }
    }

    /**
     * Somebody who skipped two releases goes from 1 to 4 in one open, which is why the 1→2 and 2→3
     * steps stay declared even though nothing writes those versions any more.
     */
    @Test
    fun `a version 1 database can reach version 4 in one go`() = runTest {
        writeVersionOneDatabaseWithOnePublication()

        val database = openWithRoom()
        try {
            val stored = database.publicationDao().observePublication("boc:1").first()

            assertNotNull("la publicación de la versión 1 no llegó a la versión 4", stored)
            assertNull(stored!!.savedAt)
            assertEquals("", stored.searchText)
            assertNull(database.aiSummaryDao().byExternalKey("boc:1"))
        } finally {
            database.close()
        }
    }

    // ---------- Version 5 to version 6: the pending mark of the alerts ----------

    /**
     * One `NOT NULL DEFAULT 0` column, which the automatic migration resolves whole. Every row that
     * was already there arrives at `0`: history is not news (014 research.md D-607). The alerts a
     * person already had survive with their matches.
     */
    @Test
    fun `a version 5 database keeps its alerts and its publications are not pending`() = runTest {
        writeVersionFiveDatabaseWithAlerts()

        val database = openWithRoom()
        try {
            val stored = database.publicationDao().observePublication("boc:1").first()

            assertNotNull("la publicación de la versión 5 no sobrevivió a la migración", stored)
            assertEquals(java.lang.Long.valueOf(9_000L), stored!!.savedAt)
            assertEquals("ayuntamiento de pielagos aprobacion definitiva", stored.searchText)
            assertFalse(stored.pendingAlertEvaluation)
            assertTrue(database.publicationDao().pendingAlertEvaluation().isEmpty())
            assertEquals("Ganadería", database.alertRuleDao().byId("r1")?.name)
            assertEquals(1, database.alertMatchDao().count())
            assertEquals(1, database.alertMatchDao().observeUnreadCount().first())
        } finally {
            database.close()
        }
    }

    /** Somebody who skipped every release since the first goes from 1 to 6 in one open. */
    @Test
    fun `a version 1 database can reach version 6 in one go`() = runTest {
        writeVersionOneDatabaseWithOnePublication()

        val database = openWithRoom()
        try {
            val stored = database.publicationDao().observePublication("boc:1").first()

            assertNotNull("la publicación de la versión 1 no llegó a la versión 6", stored)
            assertNull(stored!!.savedAt)
            assertEquals("", stored.searchText)
            assertFalse(stored.pendingAlertEvaluation)
            assertNull(database.aiSummaryDao().byExternalKey("boc:1"))
            assertEquals(0, database.alertRuleDao().count())
        } finally {
            database.close()
        }
    }

    @Test
    fun `a publication stored right after the upgrade is pending and can be cleared`() = runTest {
        writeVersionFiveDatabaseWithAlerts()

        val database = openWithRoom()
        try {
            database.publicationDao().upsertAll(listOf(freshEntity("boc:2")))

            assertEquals(listOf("boc:2"), database.publicationDao().pendingAlertEvaluation().map { it.externalKey })
            assertEquals(1, database.publicationDao().markAlertsEvaluated(listOf("boc:2")))
            assertTrue(database.publicationDao().pendingAlertEvaluation().isEmpty())
        } finally {
            database.close()
        }
    }

    /**
     * Opens the stored file exactly as the application does: a bare builder, no `addMigrations`
     * —automatic migrations need none— and no destructive fallback. If the migration were missing,
     * this is the line that would throw.
     */
    private fun openWithRoom(): BocDatabase = Room
        .databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BocDatabase::class.java,
            DATABASE_NAME,
        )
        .allowMainThreadQueries()
        .build()

    /**
     * Builds a database that looks exactly like one left behind by the previous version of the
     * application: the version-1 schema, its identity row, `user_version = 1`, and content.
     */
    private fun writeVersionOneDatabaseWithOnePublication() {
        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            VERSION_ONE_STATEMENTS.forEach(database::execSQL)

            database.execSQL(
                """
                INSERT INTO publications VALUES (
                    'boc:1', '1', 'BLOB_ID', '6802081', '1', NULL,
                    'AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva.', 'Ayuntamiento de Piélagos',
                    'Ayuntamiento de Piélagos', 'ORDINARY', '2026-08-27',
                    'https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=1',
                    '1.Disposiciones Generales|Ayuntamiento de Piélagos|ORD', '',
                    1000, 2000
                )
                """.trimIndent(),
            )
            database.execSQL(
                "INSERT INTO feed_sync_state VALUES ('6802081', 'hash-de-la-version-1', NULL, NULL, 1500, 0)",
            )

            database.version = 1
        } finally {
            database.close()
        }
    }

    /**
     * A database exactly as the previous release of the application left it: the version-2 schema,
     * its identity row, `user_version = 2`, content, **and a saved mark**, because the mark
     * surviving is the half of this that a person would notice.
     */
    private fun writeVersionTwoDatabaseWithOneSavedPublication() {
        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            VERSION_TWO_STATEMENTS.forEach(database::execSQL)

            database.execSQL(
                """
                INSERT INTO publications VALUES (
                    'boc:1', '1', 'BLOB_ID', '6802081', '1', NULL,
                    'AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva.', 'Ayuntamiento de Piélagos',
                    'Ayuntamiento de Piélagos', 'ORDINARY', '2026-08-27',
                    'https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=1',
                    '1.Disposiciones Generales|Ayuntamiento de Piélagos|ORD', '',
                    1000, 2000, 9000
                )
                """.trimIndent(),
            )
            database.execSQL(
                "INSERT INTO feed_sync_state VALUES ('6802081', 'hash-de-la-version-2', NULL, NULL, 1500, 0)",
            )

            database.version = 2
        } finally {
            database.close()
        }
    }

    /**
     * A database exactly as the previous release left it: the version-3 schema, its identity row,
     * `user_version = 3`, a saved mark **and** a filled searchable text — the state this migration
     * has to leave untouched.
     */
    private fun writeVersionThreeDatabaseWithOnePublication() {
        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            VERSION_THREE_STATEMENTS.forEach(database::execSQL)

            database.execSQL(
                """
                INSERT INTO publications VALUES (
                    'boc:1', '1', 'BLOB_ID', '6802081', '1', NULL,
                    'AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva.', 'Ayuntamiento de Piélagos',
                    'Ayuntamiento de Piélagos', 'ORDINARY', '2026-08-27',
                    'https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=1',
                    '1.Disposiciones Generales|Ayuntamiento de Piélagos|ORD', '',
                    1000, 2000, 9000, 'ayuntamiento de pielagos aprobacion definitiva'
                )
                """.trimIndent(),
            )
            database.execSQL(
                "INSERT INTO feed_sync_state VALUES ('6802081', 'hash-de-la-version-3', NULL, NULL, 1500, 0)",
            )

            database.version = 3
        } finally {
            database.close()
        }
    }

    /**
     * A database exactly as the previous release left it: the version-5 schema, its identity row,
     * `user_version = 5`, a publication with its saved mark and searchable text, **and an alert with
     * one match** — the state this migration has to leave untouched.
     */
    private fun writeVersionFiveDatabaseWithAlerts() {
        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            VERSION_FIVE_STATEMENTS.forEach(database::execSQL)

            database.execSQL(
                """
                INSERT INTO publications VALUES (
                    'boc:1', '1', 'BLOB_ID', '6802081', '1', NULL,
                    'AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva.', 'Ayuntamiento de Piélagos',
                    'Ayuntamiento de Piélagos', 'ORDINARY', '2026-08-27',
                    'https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=1',
                    '1.Disposiciones Generales|Ayuntamiento de Piélagos|ORD', '',
                    1000, 2000, 9000, 'ayuntamiento de pielagos aprobacion definitiva'
                )
                """.trimIndent(),
            )
            database.execSQL(
                "INSERT INTO feed_sync_state VALUES ('6802081', 'hash-de-la-version-5', NULL, NULL, 1500, 0)",
            )
            database.execSQL(
                "INSERT INTO alert_rules VALUES ('r1', 'Ganadería', 'ganadería', 'ANY', '', NULL, 1, 1000, 1000, 1000)",
            )
            database.execSQL("INSERT INTO alert_matches VALUES (1, 'r1', 'boc:1', 1500, NULL)")

            database.version = 5
        } finally {
            database.close()
        }
    }

    /** What a synchronisation inserts after the upgrade: a row that the alerts have not seen yet. */
    private fun freshEntity(externalKey: String) = PublicationEntity(
        externalKey = externalKey,
        blobId = externalKey.substringAfter(':'),
        idSource = IdSource.BLOB_ID,
        feedId = "6802081",
        sectionCode = "1",
        subsectionCode = null,
        title = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva.",
        issuer = "Ayuntamiento de Piélagos",
        organizationPath = listOf("Ayuntamiento de Piélagos"),
        editionType = EditionType.ORDINARY,
        publicationDate = LocalDate.of(2026, 9, 6),
        documentUrl = "https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=2",
        rawCategories = null,
        warnings = emptySet(),
        firstSeenAt = 3_000L,
        lastSeenAt = 3_000L,
        searchText = "ayuntamiento de pielagos aprobacion definitiva",
        pendingAlertEvaluation = true,
    )

    private fun summaryEntity() = AiSummaryEntity(
        externalKey = "boc:1",
        pdfSha256 = "a".repeat(64),
        modelId = "un-modelo",
        promptVersion = "v1",
        schemaVersion = "v1",
        summaryJson = """{"plainLanguageSummary":"algo"}""",
        createdAtEpochMillis = 7_000L,
        promptTokens = 100,
        completionTokens = 200,
        totalTokens = 300,
        systemFingerprint = null,
    )

    private companion object {
        const val DATABASE_NAME = "migration-boc.db"

        /**
         * The version-1 schema, transcribed **verbatim** from
         * `app/schemas/com.jrblanco.boccantabria.data.source.local.BocDatabase/1.json`, with
         * `${'$'}{TABLE_NAME}` resolved. That file is frozen, so these statements cannot drift from it.
         *
         * `room_master_table` and its hash are part of the fixture on purpose: a real version-1
         * database has them, and leaving them out would test an upgrade path nobody ever walks.
         */
        val VERSION_ONE_STATEMENTS = listOf(
            "CREATE TABLE IF NOT EXISTS `publications` (`external_key` TEXT NOT NULL, " +
                "`blob_id` TEXT, `id_source` TEXT NOT NULL, `feed_id` TEXT NOT NULL, " +
                "`section_code` TEXT NOT NULL, `subsection_code` TEXT, `title` TEXT NOT NULL, " +
                "`issuer` TEXT, `organization_path` TEXT NOT NULL, `edition_type` TEXT NOT NULL, " +
                "`publication_date` TEXT NOT NULL, `document_url` TEXT NOT NULL, " +
                "`raw_categories` TEXT, `warnings` TEXT NOT NULL, `first_seen_at` INTEGER NOT NULL, " +
                "`last_seen_at` INTEGER NOT NULL, PRIMARY KEY(`external_key`))",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_publications_blob_id` " +
                "ON `publications` (`blob_id`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_publication_date` " +
                "ON `publications` (`publication_date`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_section_code` " +
                "ON `publications` (`section_code`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_subsection_code` " +
                "ON `publications` (`subsection_code`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_edition_type` " +
                "ON `publications` (`edition_type`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_feed_id_publication_date` " +
                "ON `publications` (`feed_id`, `publication_date`)",
            "CREATE TABLE IF NOT EXISTS `feed_sync_state` (`feed_id` TEXT NOT NULL, " +
                "`body_hash` TEXT, `etag` TEXT, `last_modified` TEXT, `last_success_at` INTEGER, " +
                "`consecutive_failures` INTEGER NOT NULL, PRIMARY KEY(`feed_id`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                "VALUES(42, '477bff422c38619610ccd7c25f80497c')",
        )

        /**
         * The version-2 schema, transcribed **verbatim** from
         * `app/schemas/com.jrblanco.boccantabria.data.source.local.BocDatabase/2.json`, with
         * `${'$'}{TABLE_NAME}` resolved. That file is frozen, so these statements cannot drift.
         */
        val VERSION_TWO_STATEMENTS = listOf(
            "CREATE TABLE IF NOT EXISTS `publications` (`external_key` TEXT NOT NULL, " +
                "`blob_id` TEXT, `id_source` TEXT NOT NULL, `feed_id` TEXT NOT NULL, " +
                "`section_code` TEXT NOT NULL, `subsection_code` TEXT, `title` TEXT NOT NULL, " +
                "`issuer` TEXT, `organization_path` TEXT NOT NULL, `edition_type` TEXT NOT NULL, " +
                "`publication_date` TEXT NOT NULL, `document_url` TEXT NOT NULL, " +
                "`raw_categories` TEXT, `warnings` TEXT NOT NULL, `first_seen_at` INTEGER NOT NULL, " +
                "`last_seen_at` INTEGER NOT NULL, `saved_at` INTEGER, PRIMARY KEY(`external_key`))",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_publications_blob_id` " +
                "ON `publications` (`blob_id`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_publication_date` " +
                "ON `publications` (`publication_date`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_section_code` " +
                "ON `publications` (`section_code`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_subsection_code` " +
                "ON `publications` (`subsection_code`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_edition_type` " +
                "ON `publications` (`edition_type`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_saved_at` " +
                "ON `publications` (`saved_at`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_feed_id_publication_date` " +
                "ON `publications` (`feed_id`, `publication_date`)",
            "CREATE TABLE IF NOT EXISTS `feed_sync_state` (`feed_id` TEXT NOT NULL, " +
                "`body_hash` TEXT, `etag` TEXT, `last_modified` TEXT, `last_success_at` INTEGER, " +
                "`consecutive_failures` INTEGER NOT NULL, PRIMARY KEY(`feed_id`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                "VALUES(42, '1f93c864ff2220ed1bf0114ece8dfb40')",
        )

        /**
         * The version-3 schema, transcribed **verbatim** from
         * `app/schemas/com.jrblanco.boccantabria.data.source.local.BocDatabase/3.json`, with
         * `${'$'}{TABLE_NAME}` resolved. That file is frozen, so these statements cannot drift.
         */
        val VERSION_THREE_STATEMENTS = listOf(
            "CREATE TABLE IF NOT EXISTS `publications` (`external_key` TEXT NOT NULL, " +
                "`blob_id` TEXT, `id_source` TEXT NOT NULL, `feed_id` TEXT NOT NULL, " +
                "`section_code` TEXT NOT NULL, `subsection_code` TEXT, `title` TEXT NOT NULL, " +
                "`issuer` TEXT, `organization_path` TEXT NOT NULL, `edition_type` TEXT NOT NULL, " +
                "`publication_date` TEXT NOT NULL, `document_url` TEXT NOT NULL, " +
                "`raw_categories` TEXT, `warnings` TEXT NOT NULL, `first_seen_at` INTEGER NOT NULL, " +
                "`last_seen_at` INTEGER NOT NULL, `saved_at` INTEGER, " +
                "`search_text` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`external_key`))",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_publications_blob_id` " +
                "ON `publications` (`blob_id`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_publication_date` " +
                "ON `publications` (`publication_date`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_section_code` " +
                "ON `publications` (`section_code`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_subsection_code` " +
                "ON `publications` (`subsection_code`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_edition_type` " +
                "ON `publications` (`edition_type`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_saved_at` " +
                "ON `publications` (`saved_at`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_feed_id_publication_date` " +
                "ON `publications` (`feed_id`, `publication_date`)",
            "CREATE TABLE IF NOT EXISTS `feed_sync_state` (`feed_id` TEXT NOT NULL, " +
                "`body_hash` TEXT, `etag` TEXT, `last_modified` TEXT, `last_success_at` INTEGER, " +
                "`consecutive_failures` INTEGER NOT NULL, PRIMARY KEY(`feed_id`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                "VALUES(42, '68d17e1f4ec1b819c008659604a0bacb')",
        )

        /**
         * The version-5 schema, transcribed **verbatim** from
         * `app/schemas/com.jrblanco.boccantabria.data.source.local.BocDatabase/5.json`, with
         * `${'$'}{TABLE_NAME}` resolved. That file is frozen, so these statements cannot drift.
         */
        val VERSION_FIVE_STATEMENTS = listOf(
            "CREATE TABLE IF NOT EXISTS `publications` (`external_key` TEXT NOT NULL, " +
                "`blob_id` TEXT, `id_source` TEXT NOT NULL, `feed_id` TEXT NOT NULL, " +
                "`section_code` TEXT NOT NULL, `subsection_code` TEXT, `title` TEXT NOT NULL, " +
                "`issuer` TEXT, `organization_path` TEXT NOT NULL, `edition_type` TEXT NOT NULL, " +
                "`publication_date` TEXT NOT NULL, `document_url` TEXT NOT NULL, " +
                "`raw_categories` TEXT, `warnings` TEXT NOT NULL, `first_seen_at` INTEGER NOT NULL, " +
                "`last_seen_at` INTEGER NOT NULL, `saved_at` INTEGER, " +
                "`search_text` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`external_key`))",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_publications_blob_id` " +
                "ON `publications` (`blob_id`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_publication_date` " +
                "ON `publications` (`publication_date`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_section_code` " +
                "ON `publications` (`section_code`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_subsection_code` " +
                "ON `publications` (`subsection_code`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_edition_type` " +
                "ON `publications` (`edition_type`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_saved_at` " +
                "ON `publications` (`saved_at`)",
            "CREATE INDEX IF NOT EXISTS `index_publications_feed_id_publication_date` " +
                "ON `publications` (`feed_id`, `publication_date`)",
            "CREATE TABLE IF NOT EXISTS `feed_sync_state` (`feed_id` TEXT NOT NULL, " +
                "`body_hash` TEXT, `etag` TEXT, `last_modified` TEXT, `last_success_at` INTEGER, " +
                "`consecutive_failures` INTEGER NOT NULL, PRIMARY KEY(`feed_id`))",
            "CREATE TABLE IF NOT EXISTS `ai_summaries` (`external_key` TEXT NOT NULL, " +
                "`pdf_sha256` TEXT NOT NULL, `model_id` TEXT NOT NULL, `prompt_version` TEXT NOT NULL, " +
                "`schema_version` TEXT NOT NULL, `summary_json` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `prompt_tokens` INTEGER NOT NULL, " +
                "`completion_tokens` INTEGER NOT NULL, `total_tokens` INTEGER NOT NULL, " +
                "`system_fingerprint` TEXT, PRIMARY KEY(`external_key`))",
            "CREATE TABLE IF NOT EXISTS `alert_rules` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`keywords` TEXT NOT NULL, `match_mode` TEXT NOT NULL, `section_codes` TEXT NOT NULL, " +
                "`organization_query` TEXT, `enabled` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, `active_since` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_alert_rules_enabled` ON `alert_rules` (`enabled`)",
            "CREATE TABLE IF NOT EXISTS `alert_matches` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`rule_id` TEXT NOT NULL, `external_key` TEXT NOT NULL, `matched_at` INTEGER NOT NULL, " +
                "`read_at` INTEGER, FOREIGN KEY(`rule_id`) REFERENCES `alert_rules`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_alert_matches_rule_id_external_key` " +
                "ON `alert_matches` (`rule_id`, `external_key`)",
            "CREATE INDEX IF NOT EXISTS `index_alert_matches_external_key` " +
                "ON `alert_matches` (`external_key`)",
            "CREATE INDEX IF NOT EXISTS `index_alert_matches_read_at` ON `alert_matches` (`read_at`)",
            "CREATE INDEX IF NOT EXISTS `index_alert_matches_rule_id` ON `alert_matches` (`rule_id`)",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                "VALUES(42, '03a5f3bbfafe40bf37a1a8b38f40f610')",
        )
    }
}
