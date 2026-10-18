package pm.koc.poptv.data.epg

import pm.koc.poptv.core.TextNormalizer
import pm.koc.poptv.data.classify.NameCleaner

/**
 * Matches playlist channels to XMLTV channels: by tvg-id first (case-insensitive), then by normalized
 * channel name ("TR: TRT 1 HD" and "TRT 1" both become "trt1"), then by the tvg-id treated as a name
 * ("TRT1.tr" -> "trt1").
 */
object EpgMatcher {

    data class Channel(val key: String, val tvgId: String?, val nameKey: String)
    data class GuideChannel(val id: String, val nameKeys: List<String>)

    private val countrySuffix = Regex("""\.[a-z]{2,3}$""", RegexOption.IGNORE_CASE)

    /** Normalized name used for fallback matching. */
    fun nameKey(name: String): String {
        val cleaned = NameCleaner.clean(name).name
        val key = TextNormalizer.compactKey(cleaned)
        return key.ifEmpty { TextNormalizer.compactKey(name) }
    }

    /** Returns playlist channel key -> guide channel id. */
    fun match(channels: List<Channel>, guide: List<GuideChannel>): Map<String, String> {
        if (channels.isEmpty() || guide.isEmpty()) return emptyMap()
        val byId = HashMap<String, String>(guide.size * 2)
        val byName = HashMap<String, String>(guide.size * 2)
        for (g in guide) {
            byId.putIfAbsent(g.id.trim().lowercase(), g.id)
            for (k in g.nameKeys) if (k.isNotEmpty()) byName.putIfAbsent(k, g.id)
        }
        val out = HashMap<String, String>(channels.size)
        for (c in channels) {
            val id = c.tvgId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            val match = id?.let { byId[it] }
                ?: c.nameKey.takeIf { it.isNotEmpty() }?.let { byName[it] }
                ?: id?.let { byName[TextNormalizer.compactKey(countrySuffix.replace(it, ""))] }
            if (match != null) out[c.key] = match
        }
        return out
    }
}
