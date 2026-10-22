package pm.koc.poptv.data.epg

import pm.koc.poptv.core.TextNormalizer
import pm.koc.poptv.data.classify.NameCleaner

/**
 * Matches playlist channels to XMLTV channels: by tvg-id first (case-insensitive), then by normalized
 * channel name ("TR: TRT 1 HD" and "TRT 1" both become "trt1"), then by the tvg-id treated as a name
 * ("TRT1.tr" -> "trt1").
 *
 * Names are not unique across countries ("DE: Eurosport 1" and "UK: Eurosport 1" are both "eurosport1"),
 * so name matches use [ChannelRegion] hints: a guide channel from the channel's country wins, one without
 * a known country is accepted, and one from another country is rejected. No guide beats the wrong guide.
 *
 * Some providers also put one placeholder tvg-id on many unrelated channels (beIN Sports, Euro Star and
 * TRT 4K all tagged "24.tr", which is a news channel's guide). A tvg-id shared by [PLACEHOLDER_NAMES]
 * or more differently named channels is ignored, both as an id and as a guide channel to match by name.
 */
object EpgMatcher {

    data class Channel(
        val key: String,
        val tvgId: String?,
        val nameKey: String,
        /** Countries from the channel's name or group title ([ChannelRegion.ofPlaylistChannel]). */
        val regions: Set<String> = emptySet(),
    )

    data class GuideChannel(
        val id: String,
        val nameKeys: List<String>,
        /** Countries from the guide id or display name ([ChannelRegion.ofGuideChannel]). */
        val regions: Set<String> = emptySet(),
    )

    /** Distinct channel names sharing one tvg-id that mark it as a placeholder (variants of one channel stay below). */
    const val PLACEHOLDER_NAMES = 8

    private val countrySuffix = Regex("""\.[a-z]{2,3}$""", RegexOption.IGNORE_CASE)

    /** Normalized name used for fallback matching. */
    fun nameKey(name: String): String = nameKey(NameCleaner.clean(name), name)

    /** Same as [nameKey] for a name already run through [NameCleaner]. */
    fun nameKey(cleaned: NameCleaner.Cleaned, original: String): String {
        val key = TextNormalizer.compactKey(cleaned.name)
        return key.ifEmpty { TextNormalizer.compactKey(original) }
    }

    /** Returns playlist channel key -> guide channel id. */
    fun match(channels: List<Channel>, guide: List<GuideChannel>): Map<String, String> {
        if (channels.isEmpty() || guide.isEmpty()) return emptyMap()
        val placeholders = placeholderIds(channels)
        val byId = HashMap<String, GuideChannel>(guide.size * 2)
        val byName = HashMap<String, MutableList<GuideChannel>>(guide.size * 2)
        for (g in guide) {
            val id = g.id.trim().lowercase()
            if (id in placeholders) continue
            byId.putIfAbsent(id, g)
            for (k in g.nameKeys.distinct()) if (k.isNotEmpty()) byName.getOrPut(k) { ArrayList(1) } += g
        }
        val out = HashMap<String, String>(channels.size)
        for (c in channels) {
            val id = normalizedId(c.tvgId)?.takeIf { it !in placeholders }
            val idMatch = id?.let { byId[it] }
            // An explicit tvg-id is trusted unless it names another country than the channel's own.
            if (idMatch != null && (c.regions.isEmpty() || idMatch.regions.isEmpty() || idMatch.regions.overlaps(c.regions))) {
                out[c.key] = idMatch.id
                continue
            }
            val regions = c.regions.ifEmpty { ChannelRegion.ofId(id) }
            val match = c.nameKey.takeIf { it.isNotEmpty() }?.let { pick(byName[it], regions) }
                ?: id?.let { pick(byName[TextNormalizer.compactKey(countrySuffix.replace(it, ""))], regions) }
                ?: idMatch
            if (match != null) out[c.key] = match.id
        }
        return out
    }

    private fun normalizedId(tvgId: String?) = tvgId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    private fun placeholderIds(channels: List<Channel>): Set<String> {
        val names = HashMap<String, MutableSet<String>>()
        for (c in channels) {
            val id = normalizedId(c.tvgId) ?: continue
            if (c.nameKey.isNotEmpty()) names.getOrPut(id) { HashSet() } += c.nameKey
        }
        return names.filterValues { it.size >= PLACEHOLDER_NAMES }.keys
    }

    private fun pick(candidates: List<GuideChannel>?, regions: Set<String>): GuideChannel? {
        if (candidates.isNullOrEmpty()) return null
        if (regions.isEmpty()) return candidates.first()
        return candidates.firstOrNull { it.regions.overlaps(regions) }
            ?: candidates.firstOrNull { it.regions.isEmpty() }
    }

    private fun Set<String>.overlaps(other: Set<String>) = any { it in other }
}
