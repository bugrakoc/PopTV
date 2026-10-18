package pm.koc.poptv.domain

import pm.koc.poptv.core.Hashing
import pm.koc.poptv.core.UrlUtils

/**
 * Generates identifiers for playlist entries that survive playlist refreshes, so favorites, history and
 * resume positions keep working when the provider re-issues URLs.
 *
 * - `t:<tvg-id>` when the entry has a tvg-id that has not been used yet in this playlist.
 * - `u:<hash>` otherwise: a hash of the URL path with host, query, credentials and file extension removed.
 *   For Xtream-style URLs (`/live/<user>/<pass>/123.ts`) that leaves `/live/123`, which stays the same when
 *   the password, the server or the output format changes.
 * - Exact duplicates (the same stream listed in two categories) get a `#2`, `#3`, ... suffix.
 *
 * One generator instance must be used per import, because uniqueness is tracked across calls.
 */
class StableKeyGenerator(credentials: Set<String>) {

    private val credentials = credentials.filter { it.isNotBlank() }.toSet()
    private val used = HashSet<String>()

    fun keyFor(tvgId: String?, url: String): String {
        val id = tvgId?.trim()?.lowercase()
        if (!id.isNullOrEmpty()) {
            val key = "t:$id"
            if (used.add(key)) return key
        }
        val base = "u:" + Hashing.sha1Hex(normalizedPath(url))
        if (used.add(base)) return base
        var n = 2
        while (true) {
            val candidate = "$base#$n"
            if (used.add(candidate)) return candidate
            n++
        }
    }

    /** URL path without host, query, credential segments and the last segment's extension. */
    fun normalizedPath(url: String): String {
        val path = UrlUtils.pathOf(url.trim())
        if (path.isEmpty() || path == "/") return url.trim() // nothing better to key on
        val segments = path.split('/').filter { it.isNotEmpty() }.toMutableList()

        if (credentials.isNotEmpty()) {
            segments.removeAll { it in credentials }
        } else {
            // Unknown credentials: recognize the Xtream layouts.
            val typeIdx = segments.indexOfFirst { it.lowercase() in XTREAM_TYPES }
            if (typeIdx >= 0 && segments.size == typeIdx + 4) {
                // /live/<user>/<pass>/<id>
                segments.subList(typeIdx + 1, typeIdx + 3).clear()
            } else if (typeIdx < 0 && segments.size == 3 && segments[2].substringBefore('.').all { it.isDigit() }) {
                // /<user>/<pass>/<id>
                segments.subList(0, 2).clear()
            }
        }
        if (segments.isEmpty()) return path
        val last = segments.last()
        val dot = last.lastIndexOf('.')
        if (dot > 0) segments[segments.size - 1] = last.substring(0, dot)
        return "/" + segments.joinToString("/")
    }

    private companion object {
        val XTREAM_TYPES = setOf("live", "movie", "movies", "series", "timeshift")
    }
}
