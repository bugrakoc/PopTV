package pm.koc.poptv.data.repo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import pm.koc.poptv.data.db.AppDatabase
import pm.koc.poptv.data.db.PlaybackPositionEntity
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.domain.ContinueItem
import pm.koc.poptv.domain.ContinueWatching
import pm.koc.poptv.domain.EpisodeRef
import pm.koc.poptv.domain.ResumePolicy
import pm.koc.poptv.domain.SavedPosition
import pm.koc.poptv.domain.SeasonGrouper
import javax.inject.Inject
import javax.inject.Singleton

/** A continue-watching entry resolved to its stream. */
data class ContinueEntry(val item: ContinueItem, val stream: StreamEntity) {
    val progress: Float get() = ResumePolicy.progress(item.positionMs, item.durationMs)
}

/** Resume positions, watched state and the continue-watching row. */
@Singleton
class PlaybackRepository @Inject constructor(private val db: AppDatabase) {

    private val dao get() = db.positionDao()

    suspend fun position(key: String): PlaybackPositionEntity? = dao.get(key)
    fun observePosition(key: String): Flow<PlaybackPositionEntity?> = dao.observe(key)
    fun observeSeriesPositions(seriesKey: String): Flow<List<PlaybackPositionEntity>> = dao.observeForSeries(seriesKey)

    suspend fun save(stream: StreamEntity, positionMs: Long, durationMs: Long) {
        if (positionMs < 0) return
        val existing = dao.get(stream.key)
        val watched = ResumePolicy.isWatched(positionMs, durationMs)
        if (!ResumePolicy.shouldStore(positionMs, durationMs) && existing == null) return
        dao.upsert(
            PlaybackPositionEntity(
                streamKey = stream.key,
                seriesKey = stream.seriesKey.takeIf { stream.type == pm.koc.poptv.domain.ContentType.SERIES },
                positionMs = positionMs,
                durationMs = durationMs,
                // Re-watching a finished title from the start must not un-watch it before it is meaningful.
                watched = watched || (existing?.watched == true && positionMs < ResumePolicy.MIN_RESUME_MS),
                updatedAt = System.currentTimeMillis(),
                hiddenFromContinue = false,
            ),
        )
    }

    suspend fun setWatched(stream: StreamEntity, watched: Boolean) {
        val existing = dao.get(stream.key)
        dao.upsert(
            PlaybackPositionEntity(
                streamKey = stream.key,
                seriesKey = stream.seriesKey.takeIf { stream.type == pm.koc.poptv.domain.ContentType.SERIES },
                positionMs = if (watched) existing?.durationMs ?: 0 else 0,
                durationMs = existing?.durationMs ?: 0,
                watched = watched,
                updatedAt = System.currentTimeMillis(),
                hiddenFromContinue = existing?.hiddenFromContinue ?: false,
            ),
        )
    }

    suspend fun hideFromContinue(streamKey: String, seriesKey: String?) = dao.hideFromContinue(streamKey, seriesKey)

    /** Builds the continue-watching row (resolving streams that are still in the playlist). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun continueWatching(limit: Int = 20): Flow<List<ContinueEntry>> =
        dao.observeRecent(200).mapLatest { rows ->
            val positions = rows.map {
                SavedPosition(it.streamKey, it.seriesKey, it.positionMs, it.durationMs, it.watched, it.updatedAt)
            }
            val episodeCache = HashMap<String, List<StreamEntity>>()
            suspend fun episodesOf(series: String) = episodeCache.getOrPut(series) { db.streamDao().episodes(series) }

            // Pre-load episode lists for shows that may need a "next episode".
            positions.mapNotNull { it.seriesKey }.distinct().take(limit).forEach { episodesOf(it) }
            val items = ContinueWatching.build(positions, limit) { series, key ->
                val eps = episodeCache[series].orEmpty()
                val seasons = SeasonGrouper.group(eps) { EpisodeRef(it.key, it.displayName, it.season, it.episode, it.sortIndex) }
                SeasonGrouper.next(seasons, key) { it.key }?.key
            }
            val streams = db.streamDao().getMany(items.map { it.streamKey }).associateBy { it.key }
            items.mapNotNull { item -> streams[item.streamKey]?.takeIf { !it.hidden }?.let { ContinueEntry(item, it) } }
        }

    /** Episode to resume for a show: last unfinished one, else the one after the last watched, else first. */
    suspend fun resumeEpisode(seriesKey: String): StreamEntity? {
        val episodes = db.streamDao().episodes(seriesKey)
        if (episodes.isEmpty()) return null
        val seasons = SeasonGrouper.group(episodes) { EpisodeRef(it.key, it.displayName, it.season, it.episode, it.sortIndex) }
        val ordered = SeasonGrouper.flatten(seasons)
        val positions = dao.forSeries(seriesKey).associateBy { it.streamKey }
        val latest = positions.values.maxByOrNull { it.updatedAt } ?: return ordered.first()
        val latestStream = ordered.firstOrNull { it.key == latest.streamKey } ?: return ordered.first()
        val finished = latest.watched || ResumePolicy.isWatched(latest.positionMs, latest.durationMs)
        if (!finished) return latestStream
        return SeasonGrouper.next(seasons, latestStream.key) { it.key } ?: latestStream
    }

    suspend fun nextEpisode(stream: StreamEntity): StreamEntity? {
        val series = stream.seriesKey ?: return null
        val seasons = SeasonGrouper.group(db.streamDao().episodes(series)) {
            EpisodeRef(it.key, it.displayName, it.season, it.episode, it.sortIndex)
        }
        return SeasonGrouper.next(seasons, stream.key) { it.key }
    }
}
