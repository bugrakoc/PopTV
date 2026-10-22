package pm.koc.poptv.data.repo

import android.content.Context
import android.util.Log
import android.util.Xml
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pm.koc.poptv.core.UrlUtils
import pm.koc.poptv.data.db.AppDatabase
import pm.koc.poptv.data.db.EpgChannelEntity
import pm.koc.poptv.data.db.ProgramEntity
import pm.koc.poptv.data.epg.EpgMatcher
import pm.koc.poptv.data.epg.EpgSources
import pm.koc.poptv.data.epg.XmltvParser
import pm.koc.poptv.data.net.AppError
import pm.koc.poptv.data.net.HttpDownloader
import pm.koc.poptv.data.prefs.EpgStatus
import pm.koc.poptv.data.prefs.SettingsRepository
import pm.koc.poptv.di.ApplicationScope
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface EpgRefreshState {
    data object Idle : EpgRefreshState
    data class Running(val programmes: Int) : EpgRefreshState
    data class Done(val programmes: Int, val matchedChannels: Int) : EpgRefreshState
    data object Unavailable : EpgRefreshState
}

/** A programme with the user's EPG time offset applied. */
data class Programme(
    val channelId: String,
    val start: Long,
    val end: Long,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val category: String?,
) {
    fun progress(now: Long): Float =
        if (end <= start) 0f else ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)

    fun isLive(now: Long) = now in start until end
}

/**
 * Downloads, parses and stores the XMLTV guide, and serves now/next queries.
 * The download goes to a temp file first so a network failure never wipes the existing guide.
 */
@Singleton
class EpgRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val downloader: HttpDownloader,
    private val settings: SettingsRepository,
    private val indexer: LibraryIndexer,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<EpgRefreshState>(EpgRefreshState.Idle)
    val state: StateFlow<EpgRefreshState> = _state.asStateFlow()
    private var job: Job? = null

    fun refreshInBackground() {
        if (_state.value is EpgRefreshState.Running) return
        job = scope.launch { refresh() }
    }

    fun cancel() {
        job?.cancel()
    }

    suspend fun rematch(): Int = withContext(Dispatchers.IO) { indexer.rematchEpg() }

    /** Tries every candidate source in order. Returns true when a guide was loaded. */
    suspend fun refresh(): Boolean = mutex.withLock {
        val candidates = EpgSources.candidates(
            headerUrls = settings.headerEpgUrls(),
            playlistUrl = settings.playlistUrl(),
            manualUrl = settings.manualEpgUrl(),
        )
        if (candidates.isEmpty()) {
            Log.i(TAG, "No EPG source available")
            markUnavailable()
            return false
        }
        _state.value = EpgRefreshState.Running(0)
        for (url in candidates) {
            try {
                val count = load(url)
                if (count > 0) {
                    val matched = indexer.rematchEpg()
                    settings.setEpgStatus(EpgStatus.OK)
                    settings.setLastEpgRefresh(System.currentTimeMillis())
                    _state.value = EpgRefreshState.Done(count, matched)
                    return true
                }
                Log.w(TAG, "No programmes in ${UrlUtils.redact(url)}")
            } catch (e: CancellationException) {
                _state.value = EpgRefreshState.Idle
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "EPG source failed (${UrlUtils.redact(url)}): ${e.javaClass.simpleName} ${e.message}")
            }
        }
        markUnavailable()
        false
    }

    private suspend fun markUnavailable() {
        // Keep an older guide if one exists; only report "unavailable" when there is nothing to show.
        val hasData = db.epgDao().programCount() > 0
        if (!hasData) settings.setEpgStatus(EpgStatus.UNAVAILABLE)
        _state.value = if (hasData) EpgRefreshState.Idle else EpgRefreshState.Unavailable
    }

    private suspend fun load(url: String): Int {
        val tmp = File(context.cacheDir, "epg_download.tmp")
        try {
            downloader.download(url) { input -> tmp.outputStream().use { input.copyTo(it, 64 * 1024) } }
            if (tmp.length() == 0L) throw AppError.InvalidGuide()
            return store(tmp)
        } finally {
            tmp.delete()
        }
    }

    private suspend fun store(file: File): Int = coroutineScope {
        val now = System.currentTimeMillis()
        val windowStart = now - EpgSources.WINDOW_BACK_MS
        val windowEnd = now + EpgSources.WINDOW_AHEAD_MS
        val dao = db.epgDao()
        val batches = Channel<Batch>(capacity = 2)
        var programmes = 0

        // Replace the old guide atomically from the readers' point of view: clear + inserts in one transaction.
        val writer = launch {
            db.withTransaction {
                dao.clearPrograms()
                dao.clearChannels()
                for (batch in batches) {
                    if (batch.channels.isNotEmpty()) dao.insertChannels(batch.channels)
                    if (batch.programmes.isNotEmpty()) dao.insertPrograms(batch.programmes)
                }
            }
        }
        val job = coroutineContext.job
        try {
            val stats = withContext(Dispatchers.IO) {
                file.inputStream().use { input ->
                    // Provider guides repeat a <channel> id once per playlist entry ("beIN SPORTS 1 HD",
                    // "beIN SPORTS 1 FHD", ...); all their names are kept for name matching.
                    val channels = LinkedHashMap<String, EpgChannelEntity>()
                    val progs = ArrayList<ProgramEntity>(BATCH)
                    val parser = XmltvParser({ Xml.newPullParser() }, Locale.getDefault().language)
                    val result = parser.parse(
                        input = input,
                        windowStart = windowStart,
                        windowEnd = windowEnd,
                        onChannel = { c ->
                            val keys = c.displayNames.map(EpgMatcher::nameKey)
                            val previous = channels[c.id]
                            channels[c.id] = previous?.copy(
                                nameKeys = (previous.nameKeys.split('|') + keys).filter { it.isNotEmpty() }.distinct().joinToString("|"),
                                icon = previous.icon ?: c.icon,
                            ) ?: EpgChannelEntity(
                                id = c.id,
                                displayName = c.displayNames.firstOrNull() ?: c.id,
                                nameKeys = keys.distinct().joinToString("|"),
                                icon = c.icon,
                            )
                        },
                    ) { p ->
                        if (!job.isActive) throw CancellationException("epg cancelled")
                        progs.add(ProgramEntity(0, p.channelId, p.start, p.end, p.title, p.subtitle, p.description, p.category))
                        programmes++
                        if (progs.size >= BATCH) {
                            batches.trySendBlocking(Batch(emptyList(), ArrayList(progs))).getOrThrow()
                            progs.clear()
                            _state.value = EpgRefreshState.Running(programmes)
                        }
                    }
                    batches.trySendBlocking(Batch(channels.values.toList(), ArrayList(progs))).getOrThrow()
                    result
                }
            }
            if (stats.programmes == 0) {
                // Nothing usable: abort the transaction so the previous guide stays.
                throw AppError.InvalidGuide()
            }
            batches.close()
            writer.join()
            Log.i(
                TAG,
                "EPG stored: ${stats.channels} channels, ${stats.programmes} programmes " +
                    "(${stats.skippedOutsideWindow} outside window, ${stats.malformed} malformed, truncated=${stats.truncated})",
            )
            stats.programmes
        } catch (e: Throwable) {
            batches.close(e)
            writer.cancel()
            throw e
        }
    }

    private class Batch(val channels: List<EpgChannelEntity>, val programmes: List<ProgramEntity>)

    // ---- Queries ----

    /** Current and next programmes for a guide channel, with the time offset applied. */
    suspend fun upcoming(channelId: String, limit: Int = 2): List<Programme> {
        val offset = offsetMs()
        val now = System.currentTimeMillis()
        return db.epgDao().upcoming(channelId, now - offset, limit).map { it.toProgramme(offset) }
    }

    /** Programmes overlapping [from, to) (display time) for several channels, keyed by channel id. */
    suspend fun inRange(channelIds: List<String>, from: Long, to: Long): Map<String, List<Programme>> {
        if (channelIds.isEmpty()) return emptyMap()
        val offset = offsetMs()
        return channelIds.chunked(500).flatMap { chunk ->
            db.epgDao().inRange(chunk, from - offset, to - offset)
        }.map { it.toProgramme(offset) }.groupBy { it.channelId }
    }

    private suspend fun offsetMs(): Long = settings.current().epgOffsetMinutes * 60_000L

    private fun ProgramEntity.toProgramme(offset: Long) =
        Programme(channelId, start + offset, end + offset, title, subtitle, description, category)

    private companion object {
        const val TAG = "EpgRepository"
        const val BATCH = 2000
    }
}
