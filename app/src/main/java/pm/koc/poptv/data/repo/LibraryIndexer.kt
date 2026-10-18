package pm.koc.poptv.data.repo

import android.util.Log
import androidx.room.withTransaction
import pm.koc.poptv.core.TextNormalizer
import pm.koc.poptv.data.db.AppDatabase
import pm.koc.poptv.data.db.ShowEntity
import pm.koc.poptv.data.epg.EpgMatcher
import javax.inject.Inject
import javax.inject.Singleton

/** Maintains derived tables (categories, shows) and EPG channel matches. */
@Singleton
class LibraryIndexer @Inject constructor(private val db: AppDatabase) {

    suspend fun rebuildDerived() {
        val aggregates = db.streamDao().showAggregates()
        val shows = aggregates.map { a ->
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
        db.withTransaction {
            db.showDao().clear()
            shows.chunked(2000).forEach { db.showDao().insertAll(it) }
            db.categoryDao().clear()
            db.categoryDao().rebuildFromStreams()
            db.categoryDao().rebuildFromShows()
        }
        Log.i(TAG, "Rebuilt ${shows.size} shows")
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
