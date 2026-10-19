package pm.koc.poptv.data.xtream

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.decodeToSequence
import pm.koc.poptv.data.net.AppError
import pm.koc.poptv.data.net.HttpDownloader
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal Xtream Codes `player_api.php` client. Stream lists are decoded element by element straight from
 * the response, so a catalog with tens of thousands of movies is never held in memory as one JSON tree.
 */
@OptIn(ExperimentalSerializationApi::class)
@Singleton
class XtreamClient @Inject constructor(
    private val downloader: HttpDownloader,
    private val json: Json,
) {

    /** Verifies the account. Throws [AppError.AccountRejected] for wrong credentials or inactive accounts. */
    suspend fun login(account: XtreamAccount): XtreamUserInfo {
        val info = downloader.download(account.apiUrl()) { input -> XtreamJson.userInfo(decodeObject(input)) }
            ?: throw AppError.InvalidPlaylist()
        if (!info.isActive) throw AppError.AccountRejected(info.status.takeIf { info.auth })
        return info
    }

    suspend fun categories(account: XtreamAccount, kind: XtreamKind): LinkedHashMap<String, String> =
        downloader.download(account.apiUrl("get_${kind.action}_categories")) { input ->
            XtreamJson.categories(decodeSequence(input))
        }

    /**
     * Streams the `get_live_streams` / `get_vod_streams` / `get_series` list to [onItem] (on an IO thread).
     * Returns the number of bytes read.
     */
    suspend fun list(
        account: XtreamAccount,
        kind: XtreamKind,
        onBytes: (Long) -> Unit,
        onItem: (JsonObject) -> Unit,
    ): Long {
        val action = if (kind == XtreamKind.SERIES) "get_series" else "get_${kind.action}_streams"
        var bytes = 0L
        downloader.download(account.apiUrl(action), onBytes = { read, _ -> bytes = read; onBytes(read) }) { input ->
            for (item in decodeSequence(input)) (item as? JsonObject)?.let(onItem)
        }
        return bytes
    }

    suspend fun episodes(account: XtreamAccount, seriesId: String): List<XtreamEpisode> =
        downloader.download(account.apiUrl("get_series_info", "series_id" to seriesId)) { input ->
            XtreamJson.episodes(decodeObject(input))
        }

    private fun decodeObject(input: InputStream): JsonObject = try {
        json.decodeFromStream(JsonObject.serializer(), input)
    } catch (e: IllegalArgumentException) { // SerializationException included
        throw AppError.InvalidPlaylist(e)
    }

    /** Elements of a top-level array. A lone `{}` (how some panels say "nothing") yields one empty object. */
    private fun decodeSequence(input: InputStream): Sequence<JsonElement> {
        val elements = json.decodeToSequence(input, JsonElement.serializer(), DecodeSequenceMode.AUTO_DETECT).iterator()
        return sequence {
            while (true) {
                val next = try {
                    if (!elements.hasNext()) break
                    elements.next()
                } catch (e: IllegalArgumentException) { // SerializationException included
                    throw AppError.InvalidPlaylist(e)
                }
                yield(next)
            }
        }
    }
}
