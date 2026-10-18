package pm.koc.poptv.data.classify

/**
 * Extracts show name, season and episode from episode titles such as
 * "TR - Show Name S01E03 FHD", "Show 1x03", "Show Season 1 Episode 3", "Dizi Sezon 1 Bölüm 3",
 * "Dizi 1. Sezon 3. Bölüm", "Dizi S1 B3" or "Dizi 12. Bölüm".
 */
object EpisodeParser {

    data class Episode(
        /** Show name without prefix, quality tags or year; may be empty ("S01E01 - Pilot"). */
        val showName: String,
        val season: Int,
        val episode: Int,
        val episodeTitle: String?,
        /** Language prefix that preceded the show name ("TR"), used to keep language versions apart. */
        val prefix: String?,
        val year: Int?,
        /** True when matched by an explicit season+episode pattern (reliable enough for classification). */
        val strong: Boolean,
    )

    private const val B = "[Bb][öÖoO][lL][üÜuU][mM]" // Bölüm / BÖLÜM / Bolum

    private data class Pattern(val regex: Regex, val strong: Boolean, val seasonGroup: Int, val episodeGroup: Int)

    private val patterns = listOf(
        // S01E03, S01 E03, S1.E3, S01-E03, s01e03
        Pattern(Regex("""(?<![\p{L}\d])S(\d{1,3})\s*[ ._-]?\s*E[Pp]?\s*(\d{1,4})(?!\d)""", RegexOption.IGNORE_CASE), true, 1, 2),
        // Season 1 Episode 3, Season 1 Ep 3, Season 1, Episode 3
        Pattern(Regex("""\bSeason\s*(\d{1,3})\s*[,.:\-]?\s*(?:Episode|Ep\.?)\s*(\d{1,4})(?!\d)""", RegexOption.IGNORE_CASE), true, 1, 2),
        // Sezon 1 Bölüm 3
        Pattern(Regex("""(?<![\p{L}])Sezon\s*(\d{1,3})\s*[,.:\-]?\s*$B\s*(\d{1,4})(?!\d)""", RegexOption.IGNORE_CASE), true, 1, 2),
        // 1. Sezon 3. Bölüm
        Pattern(Regex("""(?<!\d)(\d{1,3})\s*\.?\s*Sezon\s*[,.:\-]?\s*(\d{1,4})\s*\.?\s*$B""", RegexOption.IGNORE_CASE), true, 1, 2),
        // S1 B3 (Turkish short form)
        Pattern(Regex("""(?<![\p{L}\d])S(\d{1,3})\s*[ ._-]?\s*B(\d{1,4})(?!\d)""", RegexOption.IGNORE_CASE), true, 1, 2),
        // 1x03
        Pattern(Regex("""(?<![\p{L}\d])(\d{1,2})x(\d{2,3})(?![\p{L}\d])""", RegexOption.IGNORE_CASE), true, 1, 2),
        // Episode 3 / Ep. 3 / Bölüm 3  (season 1)
        Pattern(Regex("""(?<![\p{L}])(?:Episode|Ep\.?|$B)\s*(\d{1,4})(?!\d)""", RegexOption.IGNORE_CASE), false, 0, 1),
        // 12. Bölüm  (season 1)
        Pattern(Regex("""(?<!\d)(\d{1,4})\s*\.\s*$B""", RegexOption.IGNORE_CASE), false, 0, 1),
    )

    private val trimJunk = Regex("""^[\s\-–|:•.,_(\[]+|[\s\-–|:•.,_(\[]+$""")
    private val trailingYear = Regex("""[(\[]?\b((?:19|20)\d{2})\b[)\]]?\s*$""")

    fun parse(title: String): Episode? = parse(NameCleaner.clean(title))

    /** Same as [parse] for a title that was already run through [NameCleaner]. */
    fun parse(cleaned: NameCleaner.Cleaned): Episode? {
        val text = cleaned.name
        // Every pattern needs a number; most channel and movie names can skip the regexes entirely.
        if (text.none { it.isDigit() }) return null
        for (p in patterns) {
            val m = p.regex.find(text) ?: continue
            val season = if (p.seasonGroup == 0) 1 else m.groupValues[p.seasonGroup].toIntOrNull() ?: continue
            val episode = m.groupValues[p.episodeGroup].toIntOrNull() ?: continue
            if (season > 500 || episode > 5000) continue

            var show = text.substring(0, m.range.first)
            var year = cleaned.year
            val y = trailingYear.find(show.trimEnd(' ', '-', '|', ':', '.'))
            if (y != null && y.range.first > 0) {
                year = year ?: y.groupValues[1].toInt()
                show = show.trimEnd(' ', '-', '|', ':', '.').substring(0, y.range.first)
            }
            show = trimJunk.replace(show, "")
            val rest = trimJunk.replace(text.substring(m.range.last + 1), "").takeIf { it.isNotBlank() }
            return Episode(
                showName = show,
                season = season,
                episode = episode,
                episodeTitle = rest,
                prefix = cleaned.prefix,
                year = year,
                strong = p.strong,
            )
        }
        return null
    }

    /** True when the title contains an explicit season+episode marker. */
    fun hasStrongEpisodePattern(title: String): Boolean = parse(title)?.strong == true
}
