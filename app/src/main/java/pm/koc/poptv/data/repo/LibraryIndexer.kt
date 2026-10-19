package pm.koc.poptv.data.repo

import android.util.Log
import androidx.room.withTransaction
import pm.koc.poptv.core.TextNormalizer
import pm.koc.poptv.data.db.AppDatabase
import pm.koc.poptv.data.db.ShowEntity
import pm.koc.poptv.data.epg.EpgMatcher
import pm.koc.poptv.domain.CategoryOverride
import javax.inject.Inject
import javax.inject.Singleton

/** Maintains derived tables (categories, shows) and EPG channel matches. */
@Singleton
class LibraryIndexer @Inject constructor(private val db: AppDatabase) {

    suspend fun rebuildDerived() {
        val aggregates = db.streamDao().showAggregates()
        val episodic = aggregates.map { a ->
            val name = a.seriesName?.takeIf { it.isNotBlank() } ?: a.seriesKey
            ShowEntity(
                seriesKey = a.seriesKey,
                name = name,
                searchName = TextNormalizer.searchKey(name),
                logo = a.logo,
                groupTitle = a.groupTitle,
                sortIndex = a.firstIndex,
                episodeCount = a.episodeCount,
                seasonCount = a.seasonCount.coerceAtLeast(1),
                year = a.year,
            )
        }
        val shows = mergeXtreamShows(episodic)
        db.withTransaction {
            db.showDao().clear()
            shows.chunked(2000).forEach { db.showDao().insertAll(it) }
            db.categoryDao().clear()
            db.categoryDao().rebuildFromStreams()
            db.categoryDao().rebuildFromShows()
        }
        Log.i(TAG, "Rebuilt ${shows.size} shows")
    }

    /**
     * Adds the shows of an Xtream API import. Their listing data (name, cover, category, position) comes from
     * the series list; episode counts come from episodes loaded so far (0 until the show is first opened).
     * A series category overridden to another type or hidden is left out, like its episodes would be.
     */
    private suspend fun mergeXtreamShows(episodic: List<ShowEntity>): List<ShowEntity> {
        val series = db.xtreamSeriesDao().all()
        if (series.isEmpty()) return episodic
        val overrides = db.overrideDao().all().associate { it.groupTitle to it.override }
        val loaded = episodic.associateBy { it.seriesKey }
        val xtreamShows = series
            .filter { overrides[it.groupTitle].let { o -> o == null || o == CategoryOverride.SERIES } }
            .groupBy { it.seriesKey } // sorted by sortIndex, so first() is the earliest listing
            .map { (key, rows) ->
                val first = rows.first()
                val episodes = loaded[key]
                ShowEntity(
                    seriesKey = key,
                    name = first.name,
                    searchName = TextNormalizer.searchKey(first.name),
                    logo = first.logo ?: episodes?.logo,
                    groupTitle = first.groupTitle,
                    sortIndex = first.sortIndex,
                    episodeCount = episodes?.episodeCount ?: 0,
                    seasonCount = episodes?.seasonCount ?: 0,
                    year = first.year ?: episodes?.year,
                )
            }
        val keys = xtreamShows.mapTo(HashSet()) { it.seriesKey }
        return xtreamShows + episodic.filter { it.seriesKey !in keys }
    }

    /** Recomputes `streams.epgChannelId` for all live channels. Returns the number of matched channels. */
    suspend fun rematchEpg(): Int {
        val guide = db.epgDao().channels().map { c ->
            EpgMatcher.GuideChannel(c.id, c.nameKeys.split('|').filter { it.isNotEmpty() })
        }
        val channels = db.streamDao().channelMatchRows().map { EpgMatcher.Channel(it.key, it.tvgId, it.epgNameKey) }
        val matches = EpgMatcher.match(channels, guide)
        db.withTransaction {
            db.streamDao().clearEpgMatches()
            for ((key, id) in matches) db.streamDao().setEpgMatch(key, id)
        }
        Log.i(TAG, "EPG matched ${matches.size} of ${channels.size} live channels (${guide.size} guide channels)")
        return matches.size
    }

    private companion object {
        const val TAG = "LibraryIndexer"
    }
}
