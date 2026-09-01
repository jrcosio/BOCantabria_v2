package com.jrblanco.boccantabria.data.source.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The stored copy of the bulletin: the single source of truth of everything the screen shows.
 *
 * The schema is exported from version 1 so the migration test has something to compare against
 * when version 2 arrives. Doing it now costs a line; doing it later costs archaeology. Version 2
 * arrived with the saved mark, and the promise was kept: see `BocDatabaseMigrationTest`.
 *
 * Version 2 adds one nullable column, which is the case an automatic migration resolves whole
 * against the exported schema. Writing the statement by hand would reproduce what the compiler
 * already knows how to generate, with the added risk of an identity hash that does not match.
 *
 * Version 3 adds the searchable text, and is the same case again. The 1→2 step **stays declared**:
 * somebody who skipped a release has to be able to get from 1 to 3 in one go.
 *
 * What version 3 does **not** resolve on its own is the content of the new column: rows already
 * stored come out of the migration with it empty, and a synchronisation only refreshes each
 * source's last hundred announcements. Filling them is the repository's job, and it is the failure
 * of this feature that a clean install cannot reveal.
 */
@Database(
    entities = [PublicationEntity::class, FeedSyncStateEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
@TypeConverters(Converters::class)
abstract class BocDatabase : RoomDatabase() {

    abstract fun publicationDao(): PublicationDao

    abstract fun feedSyncStateDao(): FeedSyncStateDao

    abstract fun savedPublicationDao(): SavedPublicationDao

    abstract fun publicationSearchDao(): PublicationSearchDao

    companion object {
        const val NAME: String = "boc.db"
    }
}

/**
 * Built here and not in `core/di` because an architecture rule keeps third-party SDKs out of the
 * dependency-injection package. Same shape as the Firebase factories.
 *
 * Deliberately a bare `build()`. Automatic migrations need no `addMigrations`, and
 * `fallbackToDestructiveMigration()` does not belong here even as a last resort: it would pass the
 * compilation gate and silently empty the stored bulletin of everybody who already has the
 * application installed.
 */
fun bocDatabase(context: Context): BocDatabase =
    Room.databaseBuilder(context, BocDatabase::class.java, BocDatabase.NAME).build()
