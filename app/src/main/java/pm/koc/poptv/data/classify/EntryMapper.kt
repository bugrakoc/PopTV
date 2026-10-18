package pm.koc.poptv.data.classify

import pm.koc.poptv.core.TextNormalizer
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.epg.EpgMatcher
import pm.koc.poptv.data.m3u.M3uEntry
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
    private val generation: Int,
    private val classifier: ContentClassifier = ContentClassifier(),
) {
    private var index = 0
    private var liveCount = 0

    fun map(entry: M3uEntry): StreamEntity {
        val group = entry.group
        val cleaned = NameCleaner.clean(entry.title)
        val episode = EpisodeParser.parse(cleaned)
        val detected = classifier.classify(entry.title, entry.url, group, entry.tvgType, episode).type
        val override = overrides[group]
        val type = override?.toContentType() ?: detected
        val hidden = override == CategoryOverride.HIDDEN

        val series = seriesInfo(entry, cleaned, episode, group)
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
            sortIndex = index++,
            userAgent = entry.userAgent,
            referrer = entry.referrer,
            seriesKey = series.key,
            seriesName = series.name,
            season = episode?.season,
            episode = episode?.episode,
            year = episode?.year ?: cleaned.year,
            epgNameKey = EpgMatcher.nameKey(entry.tvgName ?: entry.title),
            epgChannelId = null,
            generation = generation,
        )
    }

    private data class Series(val key: String, val name: String)

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
        val key = buildString {
            if (prefix != null) append(prefix.lowercase()).append(':')
            append(TextNormalizer.searchKey(name).ifEmpty { TextNormalizer.searchKey(entry.title) })
        }
        return Series(key, name)
    }
}
