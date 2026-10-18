package pm.koc.poptv.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.domain.ItemKind

/** Aggregate row used by the category settings screen. */
data class GroupSummary(
    val groupTitle: String,
    val detectedType: ContentType,
    val count: Int,
    val firstIndex: Int,
)

/** Aggregate row used to rebuild the shows table. */
data class ShowAggregate(
    val seriesKey: String,
    val seriesName: String?,
    val logo: String?,
    val groupTitle: String,
    val firstIndex: Int,
    val episodeCount: Int,
    val seasonCount: Int,
    val year: Int?,
)

/** Minimal live-channel projection used for EPG matching. */
data class ChannelMatchRow(
    val key: String,
    val tvgId: String?,
    val epgNameKey: String,
)

@Dao
interface StreamDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<StreamEntity>)

    @Query("DELETE FROM streams WHERE generation != :generation")
    suspend fun deleteOtherGenerations(generation: Int): Int

    @Query("DELETE FROM streams WHERE generation = :generation")
    suspend fun deleteGeneration(generation: Int): Int

    @Query("SELECT COALESCE(MAX(generation), 0) FROM streams")
    suspend fun maxGeneration(): Int

    @Query("SELECT COUNT(*) FROM streams")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM streams WHERE type = :type AND hidden = 0")
    fun observeCount(type: ContentType): Flow<Int>

    @Query("SELECT * FROM streams WHERE `key` = :key")
    suspend fun get(key: String): StreamEntity?

    @Query("SELECT * FROM streams WHERE `key` = :key")
    fun observe(key: String): Flow<StreamEntity?>

    @Query("SELECT * FROM streams WHERE `key` IN (:keys)")
    suspend fun getMany(keys: List<String>): List<StreamEntity>

    // ---- Browsing ----

    @Query("SELECT * FROM streams WHERE type = :type AND hidden = 0 AND groupTitle = :group ORDER BY sortIndex")
    fun pageByGroup(type: ContentType, group: String): PagingSource<Int, StreamEntity>

    @Query("SELECT * FROM streams WHERE type = :type AND hidden = 0 ORDER BY sortIndex")
    fun pageByType(type: ContentType): PagingSource<Int, StreamEntity>

    @Query("SELECT * FROM streams WHERE type = :type AND hidden = 0 AND (:group IS NULL OR groupTitle = :group) ORDER BY displayName COLLATE NOCASE")
    fun pageByTypeSortedByName(type: ContentType, group: String?): PagingSource<Int, StreamEntity>

    @Query("SELECT * FROM streams WHERE type = :type AND hidden = 0 AND (:group IS NULL OR groupTitle = :group) ORDER BY sortIndex DESC")
    fun pageByTypeNewestFirst(type: ContentType, group: String?): PagingSource<Int, StreamEntity>

    @Query("SELECT * FROM streams WHERE type = :type AND hidden = 0 AND (:group IS NULL OR groupTitle = :group) ORDER BY year IS NULL, year DESC, sortIndex")
    fun pageByTypeByYear(type: ContentType, group: String?): PagingSource<Int, StreamEntity>

    @Query("SELECT * FROM streams WHERE type = 'LIVE' AND hidden = 0 AND (:group IS NULL OR groupTitle = :group) ORDER BY sortIndex LIMIT :limit OFFSET :offset")
    suspend fun liveChannels(group: String?, limit: Int, offset: Int): List<StreamEntity>

    @Query("SELECT COUNT(*) FROM streams WHERE type = 'LIVE' AND hidden = 0 AND (:group IS NULL OR groupTitle = :group)")
    suspend fun liveChannelCount(group: String?): Int

    // ---- Zapping ----

    @Query("SELECT * FROM streams WHERE type = 'LIVE' AND hidden = 0 AND (:group IS NULL OR groupTitle = :group) AND sortIndex > :sortIndex ORDER BY sortIndex LIMIT 1")
    suspend fun nextLive(group: String?, sortIndex: Int): StreamEntity?

    @Query("SELECT * FROM streams WHERE type = 'LIVE' AND hidden = 0 AND (:group IS NULL OR groupTitle = :group) AND sortIndex < :sortIndex ORDER BY sortIndex DESC LIMIT 1")
    suspend fun previousLive(group: String?, sortIndex: Int): StreamEntity?

    @Query("SELECT * FROM streams WHERE type = 'LIVE' AND hidden = 0 AND (:group IS NULL OR groupTitle = :group) ORDER BY sortIndex LIMIT 1")
    suspend fun firstLive(group: String?): StreamEntity?

    @Query("SELECT * FROM streams WHERE type = 'LIVE' AND hidden = 0 AND (:group IS NULL OR groupTitle = :group) ORDER BY sortIndex DESC LIMIT 1")
    suspend fun lastLive(group: String?): StreamEntity?

    @Query("SELECT * FROM streams WHERE type = 'LIVE' AND hidden = 0 AND number = :number ORDER BY sortIndex LIMIT 1")
    suspend fun liveByNumber(number: Int): StreamEntity?

    // ---- Search ----

    @Query("SELECT * FROM streams WHERE type = :type AND hidden = 0 AND searchName LIKE :pattern ESCAPE '\\' ORDER BY sortIndex")
    fun search(type: ContentType, pattern: String): PagingSource<Int, StreamEntity>

    @Query("SELECT * FROM streams WHERE type = :type AND hidden = 0 AND searchName LIKE :pattern ESCAPE '\\' ORDER BY sortIndex LIMIT :limit")
    suspend fun searchList(type: ContentType, pattern: String, limit: Int): List<StreamEntity>

    // ---- Shows ----

    @Query("SELECT * FROM streams WHERE seriesKey = :seriesKey AND type = 'SERIES' AND hidden = 0 ORDER BY season IS NULL, season, episode IS NULL, episode, sortIndex")
    fun observeEpisodes(seriesKey: String): Flow<List<StreamEntity>>

    @Query("SELECT * FROM streams WHERE seriesKey = :seriesKey AND type = 'SERIES' AND hidden = 0 ORDER BY season IS NULL, season, episode IS NULL, episode, sortIndex")
    suspend fun episodes(seriesKey: String): List<StreamEntity>

    @Query(
        """SELECT seriesKey, seriesName, logo, groupTitle, MIN(sortIndex) AS firstIndex, COUNT(*) AS episodeCount,
           COUNT(DISTINCT season) AS seasonCount, MAX(year) AS year
           FROM streams WHERE type = 'SERIES' AND hidden = 0 AND seriesKey IS NOT NULL GROUP BY seriesKey"""
    )
    suspend fun showAggregates(): List<ShowAggregate>

    // ---- Categories / overrides ----

    @Query("SELECT groupTitle, detectedType, COUNT(*) AS count, MIN(sortIndex) AS firstIndex FROM streams GROUP BY groupTitle, detectedType")
    suspend fun groupSummaries(): List<GroupSummary>

    @Query("UPDATE streams SET type = :type, hidden = 0 WHERE groupTitle = :group")
    suspend fun setGroupType(group: String, type: ContentType)

    @Query("UPDATE streams SET type = detectedType, hidden = :hidden WHERE groupTitle = :group")
    suspend fun resetGroup(group: String, hidden: Boolean)

    @Query("UPDATE streams SET number = sortIndex + 1 WHERE groupTitle = :group AND number <= 0")
    suspend fun fillMissingNumbers(group: String)

    // ---- EPG matching ----

    @Query("SELECT `key`, tvgId, epgNameKey FROM streams WHERE type = 'LIVE'")
    suspend fun channelMatchRows(): List<ChannelMatchRow>

    @Query("UPDATE streams SET epgChannelId = NULL WHERE epgChannelId IS NOT NULL")
    suspend fun clearEpgMatches()

    @Query("UPDATE streams SET epgChannelId = :epgId WHERE `key` = :key")
    suspend fun setEpgMatch(key: String, epgId: String)

    @Query("DELETE FROM streams")
    suspend fun clear()
}

@Dao
interface CategoryDao {
    @Query("DELETE FROM categories")
    suspend fun clear()

    @Query(
        """INSERT INTO categories (id, type, name, displayName, sortIndex, itemCount)
           SELECT type || '|' || groupTitle, type, groupTitle, groupTitle, MIN(sortIndex), COUNT(*)
           FROM streams WHERE hidden = 0 AND type != 'SERIES' GROUP BY type, groupTitle"""
    )
    suspend fun rebuildFromStreams()

    @Query(
        """INSERT INTO categories (id, type, name, displayName, sortIndex, itemCount)
           SELECT 'SERIES|' || groupTitle, 'SERIES', groupTitle, groupTitle, MIN(sortIndex), COUNT(*)
           FROM shows GROUP BY groupTitle"""
    )
    suspend fun rebuildFromShows()

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sortIndex")
    fun observeByType(type: ContentType): Flow<List<CategoryEntity>>
}

@Dao
interface ShowDao {
    @Query("DELETE FROM shows")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ShowEntity>)

    @Query("SELECT * FROM shows WHERE seriesKey = :key")
    suspend fun get(key: String): ShowEntity?

    @Query("SELECT * FROM shows WHERE seriesKey = :key")
    fun observe(key: String): Flow<ShowEntity?>

    @Query("SELECT * FROM shows WHERE seriesKey IN (:keys)")
    suspend fun getMany(keys: List<String>): List<ShowEntity>

    @Query("SELECT * FROM shows WHERE (:group IS NULL OR groupTitle = :group) ORDER BY sortIndex")
    fun page(group: String?): PagingSource<Int, ShowEntity>

    @Query("SELECT * FROM shows WHERE (:group IS NULL OR groupTitle = :group) ORDER BY name COLLATE NOCASE")
    fun pageByName(group: String?): PagingSource<Int, ShowEntity>

    @Query("SELECT * FROM shows WHERE (:group IS NULL OR groupTitle = :group) ORDER BY sortIndex DESC")
    fun pageNewestFirst(group: String?): PagingSource<Int, ShowEntity>

    @Query("SELECT * FROM shows WHERE (:group IS NULL OR groupTitle = :group) ORDER BY year IS NULL, year DESC, sortIndex")
    fun pageByYear(group: String?): PagingSource<Int, ShowEntity>

    @Query("SELECT * FROM shows WHERE searchName LIKE :pattern ESCAPE '\\' ORDER BY sortIndex")
    fun search(pattern: String): PagingSource<Int, ShowEntity>

    @Query("SELECT * FROM shows WHERE searchName LIKE :pattern ESCAPE '\\' ORDER BY sortIndex LIMIT :limit")
    suspend fun searchList(pattern: String, limit: Int): List<ShowEntity>

    @Query("SELECT COUNT(*) FROM shows")
    fun observeCount(): Flow<Int>
}

@Dao
interface OverrideDao {
    @Query("SELECT * FROM category_overrides")
    suspend fun all(): List<CategoryOverrideEntity>

    @Query("SELECT * FROM category_overrides")
    fun observeAll(): Flow<List<CategoryOverrideEntity>>

    @Upsert
    suspend fun upsert(item: CategoryOverrideEntity)

    @Query("DELETE FROM category_overrides WHERE groupTitle = :group")
    suspend fun delete(group: String)

    @Query("DELETE FROM category_overrides")
    suspend fun clear()
}

@Dao
interface FavoriteDao {
    @Upsert
    suspend fun upsert(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE `key` = :key)")
    fun observeIsFavorite(key: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE `key` = :key)")
    suspend fun isFavorite(key: String): Boolean

    @Query("SELECT `key` FROM favorites WHERE kind = :kind")
    fun observeKeys(kind: ItemKind): Flow<List<String>>

    @Query("SELECT s.* FROM favorites f JOIN streams s ON s.`key` = f.`key` WHERE f.kind = :kind AND s.hidden = 0 ORDER BY f.addedAt DESC")
    fun observeStreams(kind: ItemKind): Flow<List<StreamEntity>>

    @Query("SELECT s.* FROM favorites f JOIN streams s ON s.`key` = f.`key` WHERE f.kind = 'CHANNEL' AND s.hidden = 0 ORDER BY f.addedAt DESC")
    suspend fun channels(): List<StreamEntity>

    @Query("SELECT sh.* FROM favorites f JOIN shows sh ON sh.seriesKey = f.`key` WHERE f.kind = 'SHOW' ORDER BY f.addedAt DESC")
    fun observeShows(): Flow<List<ShowEntity>>

    @Query("DELETE FROM favorites")
    suspend fun clear()
}

@Dao
interface HistoryDao {
    @Upsert
    suspend fun upsert(item: HistoryEntity)

    @Query("SELECT s.* FROM watch_history h JOIN streams s ON s.`key` = h.`key` WHERE h.kind = 'CHANNEL' AND s.hidden = 0 ORDER BY h.watchedAt DESC LIMIT :limit")
    fun observeRecentChannels(limit: Int): Flow<List<StreamEntity>>

    @Query("DELETE FROM watch_history WHERE kind = :kind AND `key` NOT IN (SELECT `key` FROM watch_history WHERE kind = :kind ORDER BY watchedAt DESC LIMIT :keep)")
    suspend fun trim(kind: ItemKind, keep: Int)

    @Query("DELETE FROM watch_history")
    suspend fun clear()
}

@Dao
interface PositionDao {
    @Upsert
    suspend fun upsert(item: PlaybackPositionEntity)

    @Query("SELECT * FROM playback_positions WHERE streamKey = :key")
    suspend fun get(key: String): PlaybackPositionEntity?

    @Query("SELECT * FROM playback_positions WHERE streamKey = :key")
    fun observe(key: String): Flow<PlaybackPositionEntity?>

    @Query("SELECT * FROM playback_positions WHERE seriesKey = :seriesKey")
    fun observeForSeries(seriesKey: String): Flow<List<PlaybackPositionEntity>>

    @Query("SELECT * FROM playback_positions WHERE seriesKey = :seriesKey")
    suspend fun forSeries(seriesKey: String): List<PlaybackPositionEntity>

    @Query("SELECT * FROM playback_positions WHERE hiddenFromContinue = 0 ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlaybackPositionEntity>>

    @Query("UPDATE playback_positions SET hiddenFromContinue = 1 WHERE streamKey = :key OR (:seriesKey IS NOT NULL AND seriesKey = :seriesKey)")
    suspend fun hideFromContinue(key: String, seriesKey: String?)

    @Query("DELETE FROM playback_positions WHERE streamKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM playback_positions")
    suspend fun clear()
}

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(items: List<EpgChannelEntity>)

    @Insert
    suspend fun insertPrograms(items: List<ProgramEntity>)

    @Query("DELETE FROM epg_channels")
    suspend fun clearChannels()

    @Query("DELETE FROM epg_programs")
    suspend fun clearPrograms()

    @Query("DELETE FROM epg_programs WHERE `end` < :before")
    suspend fun deleteEndedBefore(before: Long): Int

    @Query("SELECT * FROM epg_channels")
    suspend fun channels(): List<EpgChannelEntity>

    @Query("SELECT COUNT(*) FROM epg_programs")
    suspend fun programCount(): Int

    @Query("SELECT COUNT(*) FROM epg_programs")
    fun observeProgramCount(): Flow<Int>

    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND `end` > :now ORDER BY start LIMIT :limit")
    suspend fun upcoming(channelId: String, now: Long, limit: Int): List<ProgramEntity>

    @Query("SELECT * FROM epg_programs WHERE channelId IN (:channelIds) AND `end` > :from AND start < :to ORDER BY channelId, start")
    suspend fun inRange(channelIds: List<String>, from: Long, to: Long): List<ProgramEntity>
}

@Dao
interface MetadataDao {
    @Query("SELECT * FROM tmdb_cache WHERE cacheKey = :key")
    suspend fun get(key: String): MetadataEntity?

    @Upsert
    suspend fun upsert(item: MetadataEntity)

    @Query("DELETE FROM tmdb_cache")
    suspend fun clear()
}
