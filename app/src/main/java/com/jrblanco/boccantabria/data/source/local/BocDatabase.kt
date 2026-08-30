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
 */
@Database(
    entities = [PublicationEntity::class, FeedSyncStateEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class BocDatabase : RoomDatabase() {

    abstract fun publicationDao(): PublicationDao

    abstract fun feedSyncStateDao(): FeedSyncStateDao

    abstract fun savedPublicationDao(): SavedPublicationDao

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
