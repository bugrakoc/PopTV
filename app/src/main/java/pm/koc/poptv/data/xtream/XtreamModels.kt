package pm.koc.poptv.data.xtream

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pm.koc.poptv.data.m3u.M3uEntry

/** The three Xtream catalogs; [action] is the `player_api.php` action suffix. */
enum class XtreamKind(val action: String, val tvgType: String) {
    LIVE("live", "live"),
    MOVIE("vod", "movie"),
    SERIES("series", "series"),
}

/** `user_info` of the login response. */
data class XtreamUserInfo(val auth: Boolean, val status: String?) {
    /** Panels report "Active" for usable accounts; anything else ("Expired", "Banned", "Disabled") is not. */
    val isActive: Boolean get() = auth && (status == null || status.equals("active", ignoreCase = true))
}

data class XtreamStream(
    val id: String,
    val name: String,
    val categoryId: String?,
    val icon: String?,
    val epgChannelId: String?,
    val number: Int?,
    val extension: String?,
)

data class XtreamSeries(
    val id: String,
    val name: String,
    val categoryId: String?,
    val cover: String?,
    val year: Int?,
)

data class XtreamEpisode(
    val id: String,
    val title: String?,
    val season: Int?,
    val episode: Int?,
    val extension: String?,
)

/**
 * Lenient readers for `player_api.php` JSON. Panels disagree on types (numbers as strings, `null` vs `""`,
 * `category_id` vs `category_ids`), so every field is read as text first.
 */
object XtreamJson {

    fun userInfo(root: JsonObject): XtreamUserInfo? {
        val info = root["user_info"] as? JsonObject ?: return null
        val auth = info.text("auth")
        return XtreamUserInfo(auth = auth == "1" || auth.equals("true", ignoreCase = true), status = info.text("status"))
    }

    /** Categories in provider order: id to name. */
    fun categories(items: Sequence<JsonElement>): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        for (item in items) {
            val obj = item as? JsonObject ?: continue
            val id = obj.text("category_id") ?: continue
            out.putIfAbsent(id, obj.text("category_name") ?: "")
        }
        return out
    }

    fun stream(obj: JsonObject): XtreamStream? {
        val id = obj.text("stream_id") ?: return null
        return XtreamStream(
            id = id,
            name = obj.text("name") ?: obj.text("title") ?: return null,
            categoryId = obj.categoryId(),
            icon = obj.text("stream_icon"),
            epgChannelId = obj.text("epg_channel_id"),
            number = obj.int("num"),
            extension = obj.text("container_extension"),
        )
    }

    fun series(obj: JsonObject): XtreamSeries? {
        val id = obj.text("series_id") ?: return null
        return XtreamSeries(
            id = id,
            name = obj.text("name") ?: obj.text("title") ?: return null,
            categoryId = obj.categoryId(),
            cover = obj.text("cover"),
            year = obj.int("year") ?: (obj.text("releaseDate") ?: obj.text("release_date"))?.let(::leadingYear),
        )
    }

    /** `episodes` of `get_series_info`: an object keyed by season or an array of per-season arrays. */
    fun episodes(root: JsonObject): List<XtreamEpisode> {
        val seasons: List<Pair<Int?, JsonElement>> = when (val episodes = root["episodes"]) {
            is JsonObject -> episodes.map { (season, list) -> season.trim().toIntOrNull() to list }
            is JsonArray -> episodes.map { null to it }
            else -> emptyList()
        }
        val out = ArrayList<XtreamEpisode>()
        for ((seasonKey, list) in seasons) {
            val items = when (list) {
                is JsonArray -> list
                is JsonObject -> list.values // some panels key episodes by number as well
                else -> continue
            }
            for (item in items) {
                val obj = item as? JsonObject ?: continue
                val id = obj.text("id") ?: continue
                out += XtreamEpisode(
                    id = id,
                    title = obj.text("title"),
                    season = obj.int("season") ?: seasonKey,
                    episode = obj.int("episode_num"),
                    extension = obj.text("container_extension"),
                )
            }
        }
        return out
    }

    private fun JsonObject.categoryId(): String? =
        text("category_id") ?: (this["category_ids"] as? JsonArray)?.firstNotNullOfOrNull { it.textOrNull() }

    private fun JsonObject.text(name: String): String? = this[name]?.textOrNull()

    private fun JsonObject.int(name: String): Int? = text(name)?.toDoubleOrNull()?.toInt()

    private fun JsonElement.textOrNull(): String? {
        if (this is JsonNull || this !is JsonPrimitive) return null
        return content.trim().takeIf { it.isNotEmpty() && it != "null" }
    }

    private fun leadingYear(date: String): Int? =
        date.take(4).toIntOrNull()?.takeIf { it in 1900..2100 }
}

/** Builds playlist entries equivalent to what the provider's `get.php` M3U would contain. */
object XtreamEntries {

    fun live(account: XtreamAccount, s: XtreamStream, group: String) = M3uEntry(
        title = s.name,
        url = account.liveUrl(s.id),
        duration = -1,
        attributes = attributes(s.name, group, XtreamKind.LIVE, logo = s.icon, tvgId = s.epgChannelId, number = s.number),
    )

    fun movie(account: XtreamAccount, s: XtreamStream, group: String) = M3uEntry(
        title = s.name,
        url = account.movieUrl(s.id, s.extension),
        duration = -1,
        attributes = attributes(s.name, group, XtreamKind.MOVIE, logo = s.icon),
    )

    fun episode(account: XtreamAccount, showName: String, e: XtreamEpisode, group: String, logo: String?): M3uEntry {
        val code = if (e.season != null && e.episode != null) "S%02dE%02d".format(e.season, e.episode) else null
        val title = e.title ?: listOfNotNull(showName, code).joinToString(" ")
        return M3uEntry(
            title = title,
            url = account.episodeUrl(e.id, e.extension),
            duration = -1,
            attributes = attributes(title, group, XtreamKind.SERIES, logo = logo),
        )
    }

    private fun attributes(
        name: String,
        group: String,
        kind: XtreamKind,
        logo: String? = null,
        tvgId: String? = null,
        number: Int? = null,
    ): Map<String, String> = buildMap {
        put("tvg-name", name)
        put("group-title", group)
        put("tvg-type", kind.tvgType)
        if (logo != null) put("tvg-logo", logo)
        if (tvgId != null) put("tvg-id", tvgId)
        if (number != null && number > 0) put("tvg-chno", number.toString())
    }
}
