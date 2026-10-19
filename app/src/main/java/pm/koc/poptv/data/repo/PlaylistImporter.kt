package pm.koc.poptv.data.repo

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import pm.koc.poptv.core.UrlUtils
import pm.koc.poptv.data.classify.EntryMapper
import pm.koc.poptv.data.db.AppDatabase
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.db.XtreamSeriesEntity
import pm.koc.poptv.data.m3u.M3uHeader
import pm.koc.poptv.data.m3u.M3uParser
import pm.koc.poptv.data.net.AppError
import pm.koc.poptv.data.net.HttpDownloader
import pm.koc.poptv.data.xtream.XtreamAccount
import pm.koc.poptv.data.xtream.XtreamClient
import pm.koc.poptv.data.xtream.XtreamEntries
import pm.koc.poptv.data.xtream.XtreamJson
import pm.koc.poptv.data.xtream.XtreamKind
import pm.koc.poptv.domain.StableKeyGenerator
import javax.inject.Inject
import javax.inject.Singleton

data class ImportProgress(
    val phase: Phase,
    val bytesRead: Long = 0,
    val totalBytes: Long = -1,
    val entries: Int = 0,
) {
    enum class Phase { CONNECTING, DOWNLOADING, SAVING }
}

data class ImportSummary(val entries: Int, val epgUrls: List<String>)

/**
 * Downloads and parses a playlist while inserting rows in batches. Parsing (IO thread) and inserting
 * (Room) run concurrently through a small channel, so neither the whole file nor the whole entry list is
 * ever held in memory. Old rows are removed only after the new import completed successfully.
 *
 * Xtream Codes links (`get.php?username=...`) fall back to the `player_api.php` API when the M3U download
 * fails: some providers keep the API working while `get.php` is broken or disabled.
 */
@Singleton
class PlaylistImporter @Inject constructor(
    private val db: AppDatabase,
    private val downloader: HttpDownloader,
    private val xtream: XtreamClient,
    private val indexer: LibraryIndexer,
) {

    /** What one import produced before the old generation is dropped. */
    private class Imported(
        val generation: Int,
        val entries: Int,
        val epgUrls: List<String>,
        /** Non-null for Xtream API imports. */
        val xtreamSeries: List<XtreamSeriesEntity>?,
        val note: String,
    )

    /**
     * @param guardAgainstShrink for background refreshes: a playlist that suddenly has far fewer entries
     *   (typical "account expired" / "max connections" responses) is rejected instead of replacing the library.
     */
    suspend fun import(
        url: String,
        guardAgainstShrink: Boolean = false,
        onProgress: (ImportProgress) -> Unit,
    ): ImportSummary {
        if (!UrlUtils.isValidHttpUrl(url)) throw AppError.InvalidUrl()
        Log.i(TAG, "Importing ${UrlUtils.redact(url)}")
        onProgress(ImportProgress(ImportProgress.Phase.CONNECTING))

        val streamDao = db.streamDao()
        val firstGeneration = streamDao.maxGeneration() + 1
        val previousItems = streamDao.nonSeriesCount() + db.showDao().count()
        val overrides = db.overrideDao().all().associate { it.groupTitle to it.override }
        fun mapper(generation: Int) = EntryMapper(StableKeyGenerator(UrlUtils.credentials(url)), overrides, generation)
        val started = System.currentTimeMillis()

        val account = XtreamAccount.fromPlaylistUrl(url)
        val imported = try {
            importM3u(url, mapper(firstGeneration), firstGeneration, onProgress)
        } catch (e: Exception) {
            if (account == null || !canFallBack(e)) throw e
            Log.i(TAG, "Playlist download failed (${e.javaClass.simpleName} ${e.message}); trying the Xtream Codes API")
            try {
                xtream.login(account)
            } catch (x: Exception) {
                // The API's verdict on the account is more useful than the M3U error; anything else is not.
                if (x is CancellationException || x is AppError.AccountRejected) throw x
                Log.w(TAG, "Xtream API login failed too: ${x.javaClass.simpleName} ${x.message}")
                throw e
            }
            // A generation of its own: rows of a partial M3U download go away with the old library on success.
            importXtream(account, mapper(firstGeneration + 1), onProgress)
        }
        val generation = imported.generation

        val xtreamSeries = imported.xtreamSeries
        val newItems = streamDao.libraryItemCount(generation) + (xtreamSeries?.distinctBy { it.seriesKey }?.size ?: 0)
        if (guardAgainstShrink && previousItems >= SHRINK_MIN_PREVIOUS && newItems < previousItems / SHRINK_FACTOR) {
            // Keep the existing library; rows from this import replaced identical keys only.
            Log.w(TAG, "Refresh returned $newItems items (had $previousItems); keeping the current library")
            throw AppError.EmptyPlaylist()
        }

        onProgress(ImportProgress(ImportProgress.Phase.SAVING, entries = imported.entries))
        val adopted = try {
            db.withTransaction {
                val xtreamDao = db.xtreamSeriesDao()
                xtreamDao.clear()
                if (xtreamSeries == null) {
                    0
                } else {
                    xtreamSeries.chunked(BATCH_SIZE).forEach { xtreamDao.insertAll(it) }
                    streamDao.adoptXtreamEpisodes(generation)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppError.Storage(e)
        }
        val removed = streamDao.deleteOtherGenerations(generation)
        indexer.rebuildDerived()
        Log.i(
            TAG,
            "Imported ${imported.entries} entries (${imported.note}, $adopted loaded episodes kept, " +
                "$removed stale rows removed) in ${System.currentTimeMillis() - started} ms",
        )
        return ImportSummary(imported.entries, imported.epgUrls)
    }

    /** Unknown hosts and local failures would fail the same way through the API. */
    private fun canFallBack(e: Exception): Boolean =
        e !is CancellationException && e !is AppError.NoHost && e !is AppError.Storage && e !is AppError.InvalidUrl

    private suspend fun importM3u(
        url: String,
        mapper: EntryMapper,
        generation: Int,
        onProgress: (ImportProgress) -> Unit,
    ): Imported {
        var header: M3uHeader? = null
        var entries = 0
        val stats = withBatchWriter { sink ->
            downloader.download(
                url = url,
                onBytes = { read, length ->
                    onProgress(ImportProgress(ImportProgress.Phase.DOWNLOADING, read, length, entries))
                },
            ) { input ->
                M3uParser().parse(
                    input = input,
                    onHeader = { header = it },
                ) { entry ->
                    sink.add(mapper.map(entry))
                    entries++
                }
            }
        }
        if (stats.entries == 0) {
            db.streamDao().deleteGeneration(generation)
            throw if (stats.sawHeader) AppError.EmptyPlaylist() else AppError.InvalidPlaylist()
        }
        return Imported(generation, stats.entries, header?.epgUrls.orEmpty(), xtreamSeries = null, note = "${stats.skippedLines} skipped lines")
    }

    /**
     * Imports through `player_api.php` (after a successful login): live channels and movies become stream rows, series are stored as
     * [XtreamSeriesEntity] rows whose episodes load when a show is opened (one request per show, which would
     * be thousands of requests up front).
     *
     * Items are ordered like the provider's M3U: by category order, then by position within the category.
     */
    private suspend fun importXtream(
        account: XtreamAccount,
        mapper: EntryMapper,
        onProgress: (ImportProgress) -> Unit,
    ): Imported {
        var entries = 0
        var bytesDone = 0L
        var rankBase = 0
        val series = ArrayList<XtreamSeriesEntity>()

        withBatchWriter { sink ->
            for (kind in XtreamKind.entries) {
                val categories = xtream.categories(account, kind)
                val order = SortOrder(categories, rankBase)
                bytesDone += xtream.list(
                    account = account,
                    kind = kind,
                    onBytes = { read ->
                        onProgress(ImportProgress(ImportProgress.Phase.DOWNLOADING, bytesDone + read, -1, entries))
                    },
                ) { obj ->
                    when (kind) {
                        XtreamKind.LIVE, XtreamKind.MOVIE -> {
                            val s = XtreamJson.stream(obj) ?: return@list
                            val group = categories[s.categoryId].orEmpty()
                            val entry = if (kind == XtreamKind.LIVE) {
                                XtreamEntries.live(account, s, group)
                            } else {
                                XtreamEntries.movie(account, s, group)
                            }
                            sink.add(mapper.map(entry, sortIndex = order.next(s.categoryId)))
                        }
                        XtreamKind.SERIES -> {
                            val s = XtreamJson.series(obj) ?: return@list
                            val show = EntryMapper.showIdentity(s.name)
                            series += XtreamSeriesEntity(
                                seriesId = s.id,
                                seriesKey = show.key,
                                name = show.name,
                                logo = s.cover,
                                groupTitle = categories[s.categoryId].orEmpty(),
                                sortIndex = order.next(s.categoryId),
                                year = s.year ?: show.year,
                            )
                        }
                    }
                    entries++
                }
                rankBase = order.nextRankBase
            }
        }
        if (entries == 0) throw AppError.EmptyPlaylist()
        val note = "Xtream API: ${entries - series.size} channels and movies, ${series.size} shows"
        return Imported(mapper.generation, entries, epgUrls = emptyList(), xtreamSeries = series, note = note)
    }

    /** Sort index = category rank * [RANK_SPAN] + position within the category. */
    private class SortOrder(categories: Map<String, String>, rankBase: Int) {
        private val ranks = HashMap<String, Int>()
        private val uncategorized = rankBase + categories.size
        private val counts = HashMap<Int, Int>()
        val nextRankBase = uncategorized + 1

        init {
            categories.keys.forEachIndexed { i, id -> ranks[id] = rankBase + i }
        }

        fun next(categoryId: String?): Int {
            val rank = categoryId?.let { ranks[it] } ?: uncategorized
            val position = counts.merge(rank, 1, Int::plus)!! - 1
            return rank * RANK_SPAN + position.coerceAtMost(RANK_SPAN - 1)
        }
    }

    /** Collects rows from a blocking producer into batches that a concurrent writer inserts. */
    private class BatchSink(private val channel: SendChannel<List<StreamEntity>>, private val job: Job) {
        private val batch = ArrayList<StreamEntity>(BATCH_SIZE)

        fun add(row: StreamEntity) {
            if (!job.isActive) throw CancellationException("import cancelled")
            batch.add(row)
            if (batch.size >= BATCH_SIZE) flush()
        }

        fun flush() {
            if (batch.isEmpty()) return
            channel.trySendBlocking(ArrayList(batch)).getOrThrow()
            batch.clear()
        }
    }

    private suspend fun <T> withBatchWriter(produce: suspend (BatchSink) -> T): T = coroutineScope {
        val streamDao = db.streamDao()
        val batches = Channel<List<StreamEntity>>(capacity = 2)
        val writer = launch {
            for (batch in batches) {
                try {
                    streamDao.insertAll(batch)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw AppError.Storage(e)
                }
            }
        }
        try {
            val sink = BatchSink(batches, coroutineContext.job)
            val result = produce(sink)
            sink.flush()
            batches.close()
            writer.join()
            result
        } catch (e: Throwable) {
            batches.close(e)
            writer.cancel()
            throw e
        }
    }

    private companion object {
        const val TAG = "PlaylistImporter"
        const val BATCH_SIZE = 1000
        const val SHRINK_MIN_PREVIOUS = 500
        const val SHRINK_FACTOR = 5
        /** Positions per category in Xtream sort indices (2^31 / 100 000 leaves room for 21 000 categories). */
        const val RANK_SPAN = 100_000
    }
}
