package pm.koc.poptv.core

import java.net.URI
import java.net.URLDecoder

/** URL helpers that never need Android classes (so they are unit-testable). */
object UrlUtils {

    private val credentialParams = setOf("username", "password", "user", "pass", "pwd", "token")

    /** True for absolute http(s) URLs with a host. */
    fun isValidHttpUrl(raw: String): Boolean {
        val url = raw.trim()
        if (url.isEmpty() || url.any { it.isWhitespace() }) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        return !uri.host.isNullOrBlank()
    }

    /** Query parameters (decoded); later duplicates win. Returns empty on malformed input. */
    fun queryParams(url: String): Map<String, String> {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (part in query.split('&')) {
            if (part.isEmpty()) continue
            val k = part.substringBefore('=')
            val v = part.substringAfter('=', "")
            out[decode(k).lowercase()] = decode(v)
        }
        return out
    }

    /** Credential-like values found in the playlist URL (Xtream `username`/`password` and friends). */
    fun credentials(playlistUrl: String): Set<String> =
        queryParams(playlistUrl)
            .filterKeys { it in credentialParams }
            .values
            .filter { it.length >= 2 }
            .toSet()

    /** Replaces credential values in a URL so it can be logged safely. */
    fun redact(url: String): String {
        var out = url
        for (secret in credentials(url)) out = out.replace(secret, "***")
        val uri = runCatching { URI(url) }.getOrNull()
        val userInfo = uri?.rawUserInfo
        if (!userInfo.isNullOrEmpty()) out = out.replace("$userInfo@", "***@")
        return out
    }

    /**
     * For Xtream Codes playlist links (`.../get.php?username=U&password=P&type=m3u_plus`) returns the
     * matching XMLTV link (`.../xmltv.php?username=U&password=P`) on the same host, otherwise null.
     */
    fun xtreamXmltvUrl(playlistUrl: String): String? {
        val uri = runCatching { URI(playlistUrl.trim()) }.getOrNull() ?: return null
        val path = uri.rawPath ?: return null
        if (!path.endsWith("/get.php", ignoreCase = true)) return null
        val params = queryParams(playlistUrl)
        val user = params["username"] ?: return null
        val pass = params["password"] ?: return null
        val base = playlistUrl.trim().substringBefore('?')
        val prefix = base.substring(0, base.length - "get.php".length)
        return "${prefix}xmltv.php?username=${encode(user)}&password=${encode(pass)}"
    }

    /** Lower-cased file extension of the URL path ("mkv", "ts", "m3u8"), or "" when there is none. */
    fun extension(url: String): String {
        val path = pathOf(url)
        val last = path.substringAfterLast('/')
        val dot = last.lastIndexOf('.')
        if (dot <= 0 || dot == last.length - 1) return ""
        val ext = last.substring(dot + 1).lowercase()
        return if (ext.length <= 5 && ext.all { it.isLetterOrDigit() }) ext else ""
    }

    /** Path part of a URL without scheme, host, query and fragment. Tolerates malformed URLs. */
    fun pathOf(url: String): String {
        val noFragment = url.substringBefore('#').substringBefore('?')
        val afterScheme = noFragment.substringAfter("://", noFragment)
        val slash = afterScheme.indexOf('/')
        return if (slash < 0) "" else afterScheme.substring(slash)
    }

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
    private fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
