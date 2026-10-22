package pm.koc.poptv.data.xtream

import pm.koc.poptv.core.UrlUtils
import java.net.URI
import java.net.URLEncoder

/**
 * Xtream Codes account derived from a `get.php?username=U&password=P&type=m3u_plus&output=ts` playlist link.
 * Builds `player_api.php` requests and stream URLs on the same server (and path prefix) as the link.
 */
data class XtreamAccount(
    /** Everything before `get.php`, ending with '/'. */
    val baseUrl: String,
    val username: String,
    val password: String,
    /** "ts" or "m3u8", from the link's `output` parameter. */
    val liveExtension: String,
) {
    fun apiUrl(action: String? = null, vararg params: Pair<String, String>): String = buildString {
        append(baseUrl).append("player_api.php?username=").append(query(username))
        append("&password=").append(query(password))
        if (action != null) append("&action=").append(query(action))
        for ((k, v) in params) append('&').append(query(k)).append('=').append(query(v))
    }

    fun liveUrl(streamId: String): String = streamUrl("live", streamId, liveExtension)
    fun movieUrl(streamId: String, extension: String?): String = streamUrl("movie", streamId, extension ?: "mp4")
    fun episodeUrl(episodeId: String, extension: String?): String = streamUrl("series", episodeId, extension ?: "mp4")

    private fun streamUrl(kind: String, id: String, extension: String): String =
        "$baseUrl$kind/${path(username)}/${path(password)}/${path(id)}.$extension"

    /** The stream id of a [movieUrl] on this account, or null for any other URL. */
    fun movieId(url: String): String? {
        val prefix = "${baseUrl}movie/${path(username)}/${path(password)}/"
        if (!url.startsWith(prefix)) return null
        return url.substring(prefix.length).substringBefore('.').takeIf { it.isNotEmpty() && '/' !in it }
    }

    override fun toString(): String = "XtreamAccount(${UrlUtils.redact(apiUrl())})"

    companion object {
        /** Returns the account for an Xtream `get.php` link with username and password, otherwise null. */
        fun fromPlaylistUrl(playlistUrl: String): XtreamAccount? {
            val url = playlistUrl.trim()
            val path = runCatching { URI(url).rawPath }.getOrNull() ?: return null
            if (!path.endsWith("/get.php", ignoreCase = true)) return null
            val params = UrlUtils.queryParams(url)
            val user = params["username"]?.takeIf { it.isNotEmpty() } ?: return null
            val pass = params["password"]?.takeIf { it.isNotEmpty() } ?: return null
            val base = url.substringBefore('?').substringBefore('#')
            val output = params["output"]?.lowercase()
            return XtreamAccount(
                baseUrl = base.substring(0, base.length - "get.php".length),
                username = user,
                password = pass,
                liveExtension = if (output == "m3u8" || output == "hls") "m3u8" else "ts",
            )
        }

        private fun query(s: String): String = URLEncoder.encode(s, "UTF-8")
        private fun path(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}
