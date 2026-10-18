package pm.koc.poptv.data.epg

import pm.koc.poptv.core.UrlUtils

/** Decides which XMLTV URLs to try, in order. */
object EpgSources {

    /**
     * 1. URLs from the playlist header (`url-tvg` / `x-tvg-url`), 2. the Xtream `xmltv.php` link derived from
     * a `get.php` playlist URL, 3. the manual URL from Settings. Invalid and duplicate URLs are dropped.
     */
    fun candidates(headerUrls: List<String>, playlistUrl: String?, manualUrl: String?): List<String> {
        val out = LinkedHashSet<String>()
        headerUrls.map { it.trim() }.filter(UrlUtils::isValidHttpUrl).forEach { out.add(it) }
        playlistUrl?.let(UrlUtils::xtreamXmltvUrl)?.let { out.add(it) }
        manualUrl?.trim()?.takeIf(UrlUtils::isValidHttpUrl)?.let { out.add(it) }
        return out.toList()
    }

    /** Hours kept around "now": 6 h back, 3 days ahead. */
    const val WINDOW_BACK_MS = 6 * 60 * 60 * 1000L
    const val WINDOW_AHEAD_MS = 3 * 24 * 60 * 60 * 1000L
}
