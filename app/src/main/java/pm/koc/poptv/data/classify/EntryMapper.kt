package pm.koc.poptv.data.classify

import pm.koc.poptv.core.TextNormalizer
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.epg.EpgMatcher
import pm.koc.poptv.data.m3u.M3uEntry
import pm.koc.poptv.domain.AdultContent
import pm.koc.poptv.domain.CategoryOverride
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.domain.StableKeyGenerator
import pm.koc.poptv.domain.toContentType

/**
 * Turns parsed playlist entries into database rows: stable key, classification (respecting user overrides),
 * cleaned display name, series grouping fields, channel number and EPG match key.
 * One instance per import (it tracks list order and key uniqueness).
 */
class EntryMapper(
    private val keys: StableKeyGenerator,
    private val overrides: Map<String, CategoryOverride>,
    val generation: Int,
    private val hideAdult: Boolean = false,
    private val classifier: ContentClassifier = ContentClassifier(),
) {
    private var index = 0
    private var liveCount = 0

    /** Show membership known from the source (Xtream API episodes); wins over what the title implies. */
    data class ShowRef(val key: String, val name: String, val season: Int?, val episode: Int?)

    /**
     * @param sortIndex explicit position (Xtream imports order by category); defaults to arrival order.
     * @param show for episodes whose show, season and episode number are already known.
     */
    fun map(entry: M3uEntry, sortIndex: Int? = null, show: ShowRef? = null): StreamEntity {
        val group = entry.group
        val cleaned = NameCleaner.clean(entry.title)
        val episode = EpisodeParser.parse(cleaned)
        val detected = classifier.classify(entry.title, entry.url, group, entry.tvgType, episode).type
        val override = overrides[group]
        val type = override?.toContentType() ?: detected
        val hidden = AdultContent.hidden(group, override, hideAdult)

        val series = show?.let { Series(it.key, it.name) } ?: seriesInfo(entry, cleaned, episode, group)
        val number = if (type == ContentType.LIVE) {
            liveCount++
            entry.tvgChno?.takeIf { it > 0 } ?: liveCount
        } else {
            0
        }

        return StreamEntity(
            key = keys.keyFor(entry.tvgId, entry.url),
            name = entry.title,
            displayName = cleaned.name,
            searchName = TextNormalizer.searchKey(entry.title),
            url = entry.url,
            logo = entry.tvgLogo,
            groupTitle = group,
            tvgId = entry.tvgId,
            tvgName = entry.tvgName,
            number = number,
            type = type,
            detectedType = detected,
            hidden = hidden,
            sortIndex = sortIndex ?: index,
            userAgent = entry.userAgent,
            referrer = entry.referrer,
            subtitleUrl = entry.subtitleUrl,
            seriesKey = series.key,
            seriesName = series.name,
            season = show?.season ?: episode?.season,
            episode = show?.episode ?: episode?.episode,
            year = episode?.year ?: cleaned.year,
            epgNameKey = entry.tvgName?.let(EpgMatcher::nameKey) ?: EpgMatcher.nameKey(cleaned, entry.title),
            epgChannelId = null,
            generation = generation,
        ).also { index++ }
    }

    private data class Series(val key: String, val name: String)

    companion object {
        data class ShowIdentity(val key: String, val name: String, val year: Int?)

        /** Key, name and year for a show title ("TR - Friends (1994)"), by the rule episodes are grouped by. */
        fun showIdentity(title: String): ShowIdentity {
            val cleaned = NameCleaner.clean(title)
            return ShowIdentity(showKey(cleaned.prefix, cleaned.name, title), cleaned.name, cleaned.year)
        }

        private fun showKey(prefix: String?, name: String, fallback: String): String = buildString {
            if (prefix != null) append(prefix.lowercase()).append(':')
            append(TextNormalizer.searchKey(name).ifEmpty { TextNormalizer.searchKey(fallback) })
        }
    }

    /**
     * Show grouping key: the language prefix plus the folded show name, so "EN - Friends" and "TR - Friends"
     * stay separate shows. Titles without a show name ("Bölüm 5") use the group title as the show name.
     */
    private fun seriesInfo(
        entry: M3uEntry,
        cleaned: NameCleaner.Cleaned,
        episode: EpisodeParser.Episode?,
        group: String,
    ): Series {
        val prefix = episode?.prefix ?: cleaned.prefix
        val name = when {
            episode == null -> cleaned.name
            episode.showName.isNotBlank() -> episode.showName
            group.isNotBlank() -> NameCleaner.clean(group).name
            else -> cleaned.name
        }
        return Series(showKey(prefix, name, entry.title), name)
    }
}
