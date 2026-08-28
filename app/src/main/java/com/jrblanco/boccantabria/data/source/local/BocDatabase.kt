package com.jrblanco.boccantabria.data.source.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The stored copy of the bulletin: the single source of truth of everything the screen shows.
 *
 * The schema is exported from version 1 so the migration test has something to compare against
 * when version 2 arrives. Doing it now costs a line; doing it later costs archaeology.
 */
@Database(
    entities = [PublicationEntity::class, FeedSyncStateEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BocDatabase : RoomDatabase() {

    abstract fun publicationDao(): PublicationDao

    abstract fun feedSyncStateDao(): FeedSyncStateDao

    companion object {
        const val NAME: String = "boc.db"
    }
}

/**
 * Built here and not in `core/di` because an architecture rule keeps third-party SDKs out of the
 * dependency-injection package. Same shape as the Firebase factories.
 */
fun bocDatabase(context: Context): BocDatabase =
    Room.databaseBuilder(context, BocDatabase::class.java, BocDatabase.NAME).build()
