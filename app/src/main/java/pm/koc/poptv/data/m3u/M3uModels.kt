package pm.koc.poptv.data.m3u

/** Attributes of the `#EXTM3U` header line. */
data class M3uHeader(
    val attributes: Map<String, String>,
) {
    /** XMLTV guide URLs from `url-tvg`, `x-tvg-url` or `tvg-url` (each may hold a comma-separated list). */
    val epgUrls: List<String>
        get() = listOf("url-tvg", "x-tvg-url", "tvg-url")
            .mapNotNull { attributes[it] }
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
}

/** One playlist entry: the `#EXTINF` metadata plus the stream URL line that follows it. */
data class M3uEntry(
    val title: String,
    val url: String,
    /** Duration from `#EXTINF:<duration>`; -1 or null for live streams. */
    val duration: Int?,
    /** All `key="value"` attributes with lower-cased keys. */
    val attributes: Map<String, String>,
    /** Group from a preceding `#EXTGRP:` line. */
    val extGroup: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
) {
    val tvgId: String? get() = attributes["tvg-id"].nullIfBlank()
    val tvgName: String? get() = attributes["tvg-name"].nullIfBlank()
    val tvgLogo: String? get() = (attributes["tvg-logo"] ?: attributes["logo"]).nullIfBlank()
    val tvgChno: Int? get() = (attributes["tvg-chno"] ?: attributes["channel-number"])?.trim()?.toIntOrNull()
    val tvgType: String? get() = attributes["tvg-type"].nullIfBlank()
    val subtitleUrl: String?
        get() = (attributes["subtitles"] ?: attributes["subtitle"] ?: attributes["tvg-subtitle"]).nullIfBlank()

    /** `group-title` wins over `#EXTGRP`; empty string when neither is present. */
    val group: String get() = attributes["group-title"].nullIfBlank() ?: extGroup.nullIfBlank() ?: ""
}

data class M3uParseStats(
    val entries: Int,
    val lines: Int,
    val sawHeader: Boolean,
    val skippedLines: Int,
)

internal fun String?.nullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
