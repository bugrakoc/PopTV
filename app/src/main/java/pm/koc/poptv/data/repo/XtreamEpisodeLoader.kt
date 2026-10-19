package pm.koc.poptv.data.repo

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pm.koc.poptv.core.UrlUtils
import pm.koc.poptv.data.classify.EntryMapper
import pm.koc.poptv.data.db.AppDatabase
import pm.koc.poptv.data.net.AppError
import pm.koc.poptv.data.prefs.SettingsRepository
import pm.koc.poptv.data.xtream.XtreamAccount
import pm.koc.poptv.data.xtream.XtreamClient
import pm.koc.poptv.data.xtream.XtreamEntries
import pm.koc.poptv.domain.StableKeyGenerator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the episodes of shows imported through the Xtream API (`get_series_info`) and stores them as
 * regular episode rows, so the show screen, the player's "next episode" and continue watching work as for
 * M3U playlists. Shows from an M3U import already have their episodes and are left alone.
 */
@Singleton
class XtreamEpisodeLoader @Inject constructor(
    private val db: AppDatabase,
    private val client: XtreamClient,
    private val settings: SettingsRepository,
) {
    private val mutex = Mutex()

    /**
     * Fetches the current episode list of [seriesKey] and replaces the stored one. Returns false when the show
     * is not an Xtream show. Throws [AppError] on failure (stored episodes are then kept).
     */
    suspend fun refresh(seriesKey: String): Boolean = mutex.withLock {
        val series = db.xtreamSeriesDao().forKey(seriesKey)
        if (series.isEmpty()) return false
        val url = settings.playlistUrl() ?: return false
        val account = XtreamAccount.fromPlaylistUrl(url) ?: return false

        val streamDao = db.streamDao()
        val overrides = db.overrideDao().all().associate { it.groupTitle to it.override }
        val mapper = EntryMapper(
            StableKeyGenerator(UrlUtils.credentials(url)), overrides, streamDao.maxGeneration(), settings.current().hideAdult,
        )
        val rows = series.flatMap { s ->
            client.episodes(account, s.seriesId).map { e ->
                mapper.map(
                    XtreamEntries.episode(account, s.name, e, s.groupTitle, s.logo),
                    sortIndex = s.sortIndex,
                    show = EntryMapper.ShowRef(seriesKey, s.name, e.season, e.episode),
                )
            }
        }
        if (rows.isEmpty()) {
            Log.i(TAG, "No episodes for $seriesKey")
            return true
        }
        try {
            db.withTransaction {
                streamDao.deleteEpisodes(seriesKey)
                streamDao.insertAll(rows)
                db.showDao().updateCounts(seriesKey, rows.size, rows.mapNotNull { it.season }.distinct().size.coerceAtLeast(1))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppError.Storage(e)
        }
        Log.i(TAG, "Loaded ${rows.size} episodes for $seriesKey")
        true
    }

    private companion object {
        const val TAG = "XtreamEpisodes"
    }
}
