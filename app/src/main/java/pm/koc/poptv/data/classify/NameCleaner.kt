package pm.koc.poptv.data.classify

/**
 * Removes display noise from playlist titles: country/language prefixes ("TR - ", "|EN| ", "[DE] "),
 * quality tags (FHD, 4K, HEVC, 1080p, ᴴᴰ, ...), square-bracket tags and a trailing year.
 * The original title is kept elsewhere for search and the "show original names" option.
 */
object NameCleaner {

    data class Cleaned(
        /** Cleaned display name (never empty: falls back to the trimmed original). */
        val name: String,
        /** Upper-cased country/language prefix if one was removed, e.g. "TR". */
        val prefix: String?,
        /** Year found in the title, e.g. 2019 for "Movie (2019)". */
        val year: Int?,
    )

    /** Known country / language / provider prefixes. Only these are stripped, so "TRT 1" stays intact. */
    val PREFIXES: Set<String> = setOf(
        "TR", "TUR", "TURK", "EN", "ENG", "UK", "GB", "US", "USA", "DE", "GER", "FR", "FRA", "IT", "ITA", "ES",
        "ESP", "NL", "BE", "AR", "ARA", "ARAB", "RU", "PL", "PT", "BR", "GR", "AL", "RO", "BG", "KU", "KURD",
        "AZ", "IN", "PK", "IR", "SE", "NO", "DK", "FI", "CA", "AU", "MX", "LAT", "LATAM", "EXYU", "YU", "CH",
        "AT", "HU", "CZ", "SK", "HR", "RS", "BA", "MK", "SI", "UA", "IL", "JP", "KR", "CN", "AF", "AFR",
        "VIP", "MULTI", "INT", "4K", "UHD", "FHD", "HD", "SD", "NF", "NETFLIX", "AMZ", "DSNP", "PRIME",
        "DE+", "TR+", "EN+",
    )

    private val QUALITY_TAGS: Set<String> = setOf(
        "FHD", "UHD", "HD", "SD", "HQ", "LQ", "4K", "8K", "HEVC", "H265", "H.265", "H264", "H.264", "X264",
        "X265", "AVC", "1080P", "1080I", "720P", "2160P", "480P", "576P", "HDR",
        "HDR10", "HDR10+", "DV", "60FPS", "50FPS", "25FPS", "FPS", "BACKUP", "HD+", "FHD+",
        "UHD+", "ᴴᴰ", "ᶠᴴᴰ", "ᵁᴴᴰ", "ˢᴰ", "⁴ᴷ", "ᴿᴬᵂ", "ᴴᴱⱽᶜ", "³⁸⁴⁰ᴾ", "¹⁰⁸⁰ᴾ", "⁷²⁰ᴾ", "ᵛᴵᴾ", "ᴴᴰ⁺",
    )

    private val prefixRegex = Regex(
        """^\s*[\[(|]?\s*([A-Za-z0-9+]{2,7})\s*(?:[\])|:]|\s[-–|•]|-)\s*""",
    )
    private val bracketTag = Regex("""\[[^\]]*]""")
    private val parenYear = Regex("""[(\[]\s*((?:19|20)\d{2})\s*[)\]]""")
    private val dashYear = Regex("""\s[-–|]\s*((?:19|20)\d{2})\s*$""")
    private val parenQuality = Regex("""\(\s*([^()]{1,12})\s*\)""")
    private val tokenSplit = Regex("""\s+""")
    private val edgeJunk = Regex("""^[\s\-–|:•.,_*]+|[\s\-–|:•.,_*]+$""")
    private val multiSpace = Regex("""\s{2,}""")

    fun clean(title: String): Cleaned {
        val original = title.trim()
        var s = original
        var prefix: String? = null

        // Up to two stacked prefixes: "TR: VIP: Name".
        repeat(2) {
            val m = prefixRegex.find(s) ?: return@repeat
            val code = m.groupValues[1].uppercase()
            if (code in PREFIXES && m.range.last + 1 < s.length) {
                if (prefix == null && code !in QUALITY_TAGS) prefix = code
                s = s.substring(m.range.last + 1)
            }
        }

        var year: Int? = null
        parenYear.find(s)?.let { m ->
            year = m.groupValues[1].toInt()
            s = s.removeRange(m.range)
        }
        if (year == null) {
            dashYear.find(s)?.let { m ->
                year = m.groupValues[1].toInt()
                s = s.removeRange(m.range)
            }
        }

        s = bracketTag.replace(s, " ")
        s = parenQuality.replace(s) { m ->
            if (m.groupValues[1].uppercase().split(tokenSplit).all { it in QUALITY_TAGS }) " " else m.value
        }
        s = s.split(tokenSplit)
            .filterNot { token -> token.trim('|', '-', '.', ',').uppercase().let { it.isNotEmpty() && it in QUALITY_TAGS } }
            .joinToString(" ")
        s = multiSpace.replace(s, " ")
        s = edgeJunk.replace(s, "")

        if (s.isBlank()) s = original
        return Cleaned(s, prefix, year)
    }
}
