package pm.koc.poptv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        StreamEntity::class,
        CategoryEntity::class,
        ShowEntity::class,
        CategoryOverrideEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        PlaybackPositionEntity::class,
        EpgChannelEntity::class,
        ProgramEntity::class,
        MetadataEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun streamDao(): StreamDao
    abstract fun categoryDao(): CategoryDao
    abstract fun showDao(): ShowDao
    abstract fun overrideDao(): OverrideDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun positionDao(): PositionDao
    abstract fun epgDao(): EpgDao
    abstract fun metadataDao(): MetadataDao

    companion object {
        const val NAME = "poptv.db"

        /**
         * Explicit migrations between schema versions (schemas are exported to `app/schemas`).
         * User data (favorites, history, positions, overrides) must always be migrated, never dropped.
         * Add new migrations here when bumping [Database.version].
         */
        val MIGRATIONS: Array<Migration> = arrayOf()
    }
}
