package pm.koc.poptv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        XtreamSeriesEntity::class,
    ],
    version = 3,
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
    abstract fun xtreamSeriesDao(): XtreamSeriesDao

    companion object {
        const val NAME = "poptv.db"

        /**
         * Explicit migrations between schema versions (schemas are exported to `app/schemas`).
         * User data (favorites, history, positions, overrides) must always be migrated, never dropped.
         * Add new migrations here when bumping [Database.version].
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `xtream_series` (`seriesId` TEXT NOT NULL, `seriesKey` TEXT NOT NULL, " +
                            "`name` TEXT NOT NULL, `logo` TEXT, `groupTitle` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, " +
                            "`year` INTEGER, PRIMARY KEY(`seriesId`))",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_xtream_series_seriesKey` ON `xtream_series` (`seriesKey`)")
                }
            },
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `tmdb_cache` ADD COLUMN `originalLanguage` TEXT")
                }
            },
        )
    }
}
