package pm.koc.poptv.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import pm.koc.poptv.data.db.AppDatabase
import pm.koc.poptv.data.db.FavoriteEntity
import pm.koc.poptv.data.db.HistoryEntity
import pm.koc.poptv.data.db.ShowEntity
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.prefs.SettingsRepository
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.domain.ItemKind
import javax.inject.Inject
import javax.inject.Singleton

enum class SortOrder { PLAYLIST, NAME, NEWEST, YEAR }

/** Special live "categories" used for browsing and zapping. */
object LiveContext {
    const val ALL = "@@all"
    const val FAVORITES = "@@favorites"
    const val RECENT = "@@recent"

    fun isSpecial(value: String?) = value == null || value == ALL || value == FAVORITES || value == RECENT
    /** Group filter for queries: null means all channels. */
    fun groupOrNull(value: String?): String? = if (isSpecial(value)) null else value
}

/** Read access to the library plus favorites and history. */
@Singleton
class LibraryRepository @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    private val pagingConfig = PagingConfig(pageSize = 60, prefetchDistance = 60, enablePlaceholders = true, initialLoadSize = 120)

    fun streams(type: ContentType, group: String?, sort: SortOrder = SortOrder.PLAYLIST): Flow<PagingData<StreamEntity>> =
        Pager(pagingConfig) {
            val dao = db.streamDao()
            when (sort) {
                SortOrder.PLAYLIST -> if (group == null) dao.pageByType(type) else dao.pageByGroup(type, group)
                SortOrder.NAME -> dao.pageByTypeSortedByName(type, group)
                SortOrder.NEWEST -> dao.pageByTypeNewestFirst(type, group)
                SortOrder.YEAR -> dao.pageByTypeByYear(type, group)
            }
        }.flow

    fun shows(group: String?, sort: SortOrder = SortOrder.PLAYLIST): Flow<PagingData<ShowEntity>> =
        Pager(pagingConfig) {
            val dao = db.showDao()
            when (sort) {
                SortOrder.PLAYLIST -> dao.page(group)
                SortOrder.NAME -> dao.pageByName(group)
                SortOrder.NEWEST -> dao.pageNewestFirst(group)
                SortOrder.YEAR -> dao.pageByYear(group)
            }
        }.flow

    fun count(type: ContentType): Flow<Int> = db.streamDao().observeCount(type)
    fun showCount(): Flow<Int> = db.showDao().observeCount()

    suspend fun stream(key: String): StreamEntity? = db.streamDao().get(key)
    fun observeStream(key: String): Flow<StreamEntity?> = db.streamDao().observe(key)
    suspend fun show(key: String): ShowEntity? = db.showDao().get(key)
    fun observeShow(key: String): Flow<ShowEntity?> = db.showDao().observe(key)
    fun observeEpisodes(seriesKey: String): Flow<List<StreamEntity>> = db.streamDao().observeEpisodes(seriesKey)
    suspend fun episodes(seriesKey: String): List<StreamEntity> = db.streamDao().episodes(seriesKey)

    // ---- Favorites ----

    fun favoriteChannels(): Flow<List<StreamEntity>> = db.favoriteDao().observeStreams(ItemKind.CHANNEL)
    fun favoriteMovies(): Flow<List<StreamEntity>> = db.favoriteDao().observeStreams(ItemKind.MOVIE)
    fun favoriteShows(): Flow<List<ShowEntity>> = db.favoriteDao().observeShows()
    fun favoriteKeys(kind: ItemKind): Flow<List<String>> = db.favoriteDao().observeKeys(kind)
    fun isFavorite(key: String): Flow<Boolean> = db.favoriteDao().observeIsFavorite(key)

    suspend fun toggleFavorite(key: String, kind: ItemKind): Boolean {
        val dao = db.favoriteDao()
        return if (dao.isFavorite(key)) {
            dao.delete(key)
            false
        } else {
            dao.upsert(FavoriteEntity(key, kind, System.currentTimeMillis()))
            true
        }
    }

    // ---- History ----

    fun recentChannels(limit: Int = 20): Flow<List<StreamEntity>> = db.historyDao().observeRecentChannels(limit)

    suspend fun recordChannelWatched(key: String) {
        db.historyDao().upsert(HistoryEntity(key, ItemKind.CHANNEL, System.currentTimeMillis()))
        db.historyDao().trim(ItemKind.CHANNEL, keep = 50)
        settings.pushLastChannel(key)
    }

    // ---- Zapping ----

    /** Neighbouring live channel in [context] (a group title or a [LiveContext] constant). */
    suspend fun adjacentChannel(current: StreamEntity, context: String?, forward: Boolean): StreamEntity? {
        val dao = db.streamDao()
        return when (context) {
            LiveContext.FAVORITES -> neighbourIn(db.favoriteDao().channels(), current, forward)
            LiveContext.RECENT -> null
            else -> {
                val group = LiveContext.groupOrNull(context)
                if (forward) {
                    dao.nextLive(group, current.sortIndex) ?: dao.firstLive(group)
                } else {
                    dao.previousLive(group, current.sortIndex) ?: dao.lastLive(group)
                }
            }
        }
    }

    private fun neighbourIn(list: List<StreamEntity>, current: StreamEntity, forward: Boolean): StreamEntity? {
        if (list.isEmpty()) return null
        val idx = list.indexOfFirst { it.key == current.key }
        if (idx < 0) return list.first()
        val next = if (forward) (idx + 1) % list.size else (idx - 1 + list.size) % list.size
        return list[next]
    }

    suspend fun channelByNumber(number: Int): StreamEntity? = db.streamDao().liveByNumber(number)

    suspend fun liveChannels(context: String?, offset: Int, limit: Int): List<StreamEntity> = when (context) {
        LiveContext.FAVORITES -> db.favoriteDao().channels().drop(offset).take(limit)
        else -> db.streamDao().liveChannels(LiveContext.groupOrNull(context), limit, offset)
    }

    suspend fun previousChannelKey(): String? = settings.lastChannels().second
}
