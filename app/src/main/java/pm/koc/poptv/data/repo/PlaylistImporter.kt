package pm.koc.poptv.data.repo

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import pm.koc.poptv.core.UrlUtils
import pm.koc.poptv.data.classify.EntryMapper
import pm.koc.poptv.data.db.AppDatabase
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.m3u.M3uHeader
import pm.koc.poptv.data.m3u.M3uParser
import pm.koc.poptv.data.net.AppError
import pm.koc.poptv.data.net.HttpDownloader
import pm.koc.poptv.domain.StableKeyGenerator
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

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
 */
@Singleton
class PlaylistImporter @Inject constructor(
    private val db: AppDatabase,
    private val downloader: HttpDownloader,
    private val indexer: LibraryIndexer,
) {

    suspend fun import(url: String, onProgress: (ImportProgress) -> Unit): ImportSummary {
        if (!UrlUtils.isValidHttpUrl(url)) throw AppError.InvalidUrl()
        Log.i(TAG, "Importing ${UrlUtils.redact(url)}")
        onProgress(ImportProgress(ImportProgress.Phase.CONNECTING))

        val streamDao = db.streamDao()
        val generation = streamDao.maxGeneration() + 1
        val overrides = db.overrideDao().all().associate { it.groupTitle to it.override }
        val mapper = EntryMapper(StableKeyGenerator(UrlUtils.credentials(url)), overrides, generation)
        var header: M3uHeader? = null
        var entries = 0
        var bytes = 0L
        var total = -1L
        val started = System.currentTimeMillis()

        val stats = coroutineScope {
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
            val job = coroutineContext.job
            try {
                val result = downloader.download(
                    url = url,
                    onBytes = { read, length ->
                        bytes = read
                        total = length
                        onProgress(ImportProgress(ImportProgress.Phase.DOWNLOADING, read, length, entries))
                    },
                ) { input ->
                    val batch = ArrayList<StreamEntity>(BATCH_SIZE)
                    val parsed = M3uParser().parse(
                        input = input,
                        onHeader = { header = it },
                    ) { entry ->
                        if (!job.isActive) throw CancellationException("import cancelled")
                        batch.add(mapper.map(entry))
                        entries++
                        if (batch.size >= BATCH_SIZE) {
                            batches.trySendBlocking(ArrayList(batch)).getOrThrow()
                            batch.clear()
                        }
                    }
                    if (batch.isNotEmpty()) batches.trySendBlocking(ArrayList(batch)).getOrThrow()
                    parsed
                }
                batches.close()
                writer.join()
                result
            } catch (e: Throwable) {
                batches.close(e)
                writer.cancel()
                throw e
            }
        }

        if (stats.entries == 0) {
            streamDao.deleteGeneration(generation)
            throw if (stats.sawHeader) AppError.EmptyPlaylist() else AppError.InvalidPlaylist()
        }

        onProgress(ImportProgress(ImportProgress.Phase.SAVING, bytes, total, entries))
        val removed = streamDao.deleteOtherGenerations(generation)
        indexer.rebuildDerived()
        Log.i(
            TAG,
            "Imported ${stats.entries} entries (${stats.skippedLines} skipped lines, $removed stale rows removed) " +
                "in ${System.currentTimeMillis() - started} ms",
        )
        return ImportSummary(stats.entries, header?.epgUrls.orEmpty())
    }

    private companion object {
        const val TAG = "PlaylistImporter"
        const val BATCH_SIZE = 1000
    }
}
