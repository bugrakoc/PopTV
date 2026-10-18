package pm.koc.poptv.data.classify

import pm.koc.poptv.core.TextNormalizer
import pm.koc.poptv.core.UrlUtils
import pm.koc.poptv.domain.ContentType

/**
 * Data-driven rules for [ContentClassifier]. Keywords are matched as whole words (or phrases) against the
 * folded group title, so "Filmler", "FİLMLER" and "filmler" are equivalent.
 */
data class ClassificationRules(
    val tvgTypeLive: Set<String> = setOf("live", "channel", "channels", "tv", "stream", "radio"),
    val tvgTypeMovie: Set<String> = setOf("movie", "movies", "vod", "film", "films"),
    val tvgTypeSeries: Set<String> = setOf("series", "serie", "show", "shows", "tvshow", "tvshows", "episode"),

    val urlLive: Set<String> = setOf("live", "livetv", "channels", "timeshift"),
    val urlMovie: Set<String> = setOf("movie", "movies", "vod", "film", "films"),
    val urlSeries: Set<String> = setOf("series", "serie", "tvshows", "tvshow", "shows", "episodes"),

    /** Checked first: anything that says "series" wins over "TV" or "film". */
    val groupSeries: Set<String> = setOf(
        "series", "serie", "tv series", "tv shows", "tv show", "shows", "show series", "season", "seasons",
        "episodes", "dizi", "diziler", "dizileri", "dizisi", "yerli dizi", "yabanci dizi", "sezon", "bolum",
        "bolumler", "netflix dizileri", "mini dizi", "docuseries",
    ),
    /** Strong live markers, checked before movie keywords ("Sinema Kanalları" is a channel group). */
    val groupLiveStrong: Set<String> = setOf(
        "kanal", "kanallar", "kanallari", "channel", "channels", "tv", "live", "canli", "24 7",
        "247", "ulusal", "radyo", "radio", "iptv", "yayin", "yayinlar",
    ),
    val groupMovie: Set<String> = setOf(
        "movie", "movies", "film", "filmler", "filmleri", "filmi", "vod", "sinema", "cinema", "box office",
        "boxoffice", "4k movies", "yerli film", "yabanci film", "peliculas", "filme", "cine", "vizyon",
    ),
    /** Weak live markers (genres that are usually channel groups). */
    val groupLiveWeak: Set<String> = setOf(
        "spor", "sport", "sports", "haber", "news", "muzik", "music", "cocuk", "kids", "belgesel",
        "documentary", "ppv", "events", "religious", "dini", "yerel", "local", "eglence", "entertainment",
    ),

    val vodExtensions: Set<String> = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "m4v", "flv", "webm", "mpg", "mpeg", "divx", "3gp", "ogv", "vob",
    ),
    val liveExtensions: Set<String> = setOf("ts", "m3u8", "m3u", "mpd", "php"),
)

/** Result of a classification with the layer that decided it (useful for debugging and tests). */
data class Classification(val type: ContentType, val reason: Reason) {
    enum class Reason { TVG_TYPE, URL, GROUP, EXTENSION, TITLE, DEFAULT }
}

/**
 * Layered heuristics, highest priority first:
 * 1. `tvg-type`  2. URL path segments  3. group-title keywords  4. file extension  5. title patterns.
 * A "movie" decision is upgraded to "series" when the title has an explicit SxxEyy-style marker, and a live
 * decision based only on the extension (or no information) is too.
 */
class ContentClassifier(private val rules: ClassificationRules = ClassificationRules()) {

    private val yearInTitle = Regex("""[(\[]\s*(19|20)\d{2}\s*[)\]]""")

    fun classify(
        title: String,
        url: String,
        groupTitle: String,
        tvgType: String?,
        episode: EpisodeParser.Episode? = EpisodeParser.parse(title),
    ): Classification {
        val strongEpisode = episode?.strong == true

        tvgType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { t ->
            when (t) {
                in rules.tvgTypeLive -> return Classification(ContentType.LIVE, Classification.Reason.TVG_TYPE)
                in rules.tvgTypeMovie -> return Classification(
                    if (strongEpisode) ContentType.SERIES else ContentType.MOVIE, Classification.Reason.TVG_TYPE,
                )
                in rules.tvgTypeSeries -> return Classification(ContentType.SERIES, Classification.Reason.TVG_TYPE)
            }
        }

        urlType(url)?.let { type ->
            return Classification(upgrade(type, strongEpisode), Classification.Reason.URL)
        }

        groupType(groupTitle)?.let { type ->
            return Classification(upgrade(type, strongEpisode), Classification.Reason.GROUP)
        }

        val ext = UrlUtils.extension(url)
        if (ext in rules.vodExtensions) {
            return Classification(
                if (strongEpisode) ContentType.SERIES else ContentType.MOVIE, Classification.Reason.EXTENSION,
            )
        }
        if (strongEpisode) return Classification(ContentType.SERIES, Classification.Reason.TITLE)
        if (ext in rules.liveExtensions) return Classification(ContentType.LIVE, Classification.Reason.EXTENSION)
        if (yearInTitle.containsMatchIn(title)) return Classification(ContentType.MOVIE, Classification.Reason.TITLE)
        return Classification(ContentType.LIVE, Classification.Reason.DEFAULT)
    }

    private fun upgrade(type: ContentType, strongEpisode: Boolean): ContentType =
        if (type == ContentType.MOVIE && strongEpisode) ContentType.SERIES else type

    /** Looks at every path segment except the file name. */
    fun urlType(url: String): ContentType? {
        val segments = UrlUtils.pathOf(url).split('/').filter { it.isNotEmpty() }
        if (segments.size < 2) return null
        for (segment in segments.dropLast(1)) {
            when (segment.lowercase()) {
                in rules.urlSeries -> return ContentType.SERIES
                in rules.urlMovie -> return ContentType.MOVIE
                in rules.urlLive -> return ContentType.LIVE
            }
        }
        return null
    }

    fun groupType(groupTitle: String): ContentType? {
        if (groupTitle.isBlank()) return null
        val words = " " + TextNormalizer.searchKey(groupTitle) + " "
        fun matches(keywords: Set<String>) = keywords.any { words.contains(" $it ") }
        return when {
            matches(rules.groupSeries) -> ContentType.SERIES
            matches(rules.groupLiveStrong) -> ContentType.LIVE
            matches(rules.groupMovie) -> ContentType.MOVIE
            matches(rules.groupLiveWeak) -> ContentType.LIVE
            else -> null
        }
    }
}
