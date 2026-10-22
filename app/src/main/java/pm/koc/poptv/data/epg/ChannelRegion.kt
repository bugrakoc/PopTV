package pm.koc.poptv.data.epg

import pm.koc.poptv.core.TextNormalizer
import pm.koc.poptv.data.classify.NameCleaner

/**
 * Country hints for EPG matching. Playlists often carry the same channel name for several countries
 * ("DE: Eurosport 1", "UK: Eurosport 1"), so a name match alone can pick another country's guide.
 *
 * Regions are lower-case ISO 3166 codes, with "uk" for the United Kingdom as most XMLTV ids use it.
 * A playlist code may cover several countries: "DE" groups usually include Austrian and Swiss channels,
 * "EN" any English-speaking country.
 */
object ChannelRegion {

    private val ARAB = setOf("ae", "sa", "qa", "kw", "bh", "om", "eg", "lb", "jo", "iq", "sy", "ps", "ye", "ma", "dz", "tn", "ly", "sd")
    private val EXYU = setOf("rs", "hr", "ba", "mk", "si", "me")
    private val LATAM = setOf("mx", "ar", "co", "cl", "pe", "ve", "ec", "uy", "py", "bo", "cr", "pa", "gt", "do", "pr")
    private val ENGLISH = setOf("uk", "ie", "us", "ca", "au", "nz")
    private val GERMAN = setOf("de", "at", "ch")
    private val FRENCH = setOf("fr", "be", "ch", "lu", "mc")

    /** Playlist prefix codes (see [NameCleaner.PREFIXES]) that name a country or language. */
    private val CODES: Map<String, Set<String>> = buildMap {
        for (c in listOf("TR", "TUR", "TURK")) put(c, setOf("tr"))
        for (c in listOf("DE", "GER")) put(c, GERMAN)
        for (c in listOf("UK", "GB")) put(c, setOf("uk", "ie"))
        for (c in listOf("US", "USA")) put(c, setOf("us"))
        for (c in listOf("EN", "ENG")) put(c, ENGLISH)
        for (c in listOf("FR", "FRA")) put(c, FRENCH)
        for (c in listOf("IT", "ITA")) put(c, setOf("it", "ch", "sm"))
        for (c in listOf("ES", "ESP")) put(c, setOf("es"))
        for (c in listOf("AR", "ARA", "ARAB")) put(c, ARAB)
        for (c in listOf("EXYU", "YU")) put(c, EXYU)
        for (c in listOf("LAT", "LATAM")) put(c, LATAM)
        put("NL", setOf("nl", "be"))
        put("GR", setOf("gr", "cy"))
        put("AL", setOf("al", "xk"))
        put("RO", setOf("ro", "md"))
        for (c in listOf(
            "BE", "PT", "BR", "PL", "BG", "RU", "UA", "AZ", "IN", "PK", "IR", "SE", "NO", "DK", "FI", "CA", "AU",
            "MX", "CH", "AT", "HU", "CZ", "SK", "HR", "RS", "BA", "MK", "SI", "IL", "JP", "KR", "CN",
        )) put(c, setOf(c.lowercase()))
    }

    /** Folded country / language words found in group titles ("GERMANY | SPORT", "Türkiye Ulusal"). */
    private val WORDS: Map<String, Set<String>> = buildMap {
        for (w in listOf("turkey", "turkiye", "turkish", "turk")) put(w, setOf("tr"))
        for (w in listOf("germany", "deutschland", "german", "almanya")) put(w, GERMAN)
        for (w in listOf("england", "britain", "british")) put(w, setOf("uk", "ie"))
        put("english", ENGLISH)
        put("usa", setOf("us"))
        for (w in listOf("france", "french")) put(w, FRENCH)
        for (w in listOf("italy", "italia", "italian")) put(w, setOf("it", "ch", "sm"))
        for (w in listOf("spain", "espana", "spanish")) put(w, setOf("es"))
        for (w in listOf("netherlands", "holland", "nederland", "dutch")) put(w, setOf("nl", "be"))
        for (w in listOf("arabic", "arab")) put(w, ARAB)
        for (w in listOf("exyu", "yugoslavia")) put(w, EXYU)
        for (w in listOf("latino", "latam")) put(w, LATAM)
        put("austria", setOf("at"))
        put("switzerland", setOf("ch"))
        put("belgium", setOf("be"))
        put("portugal", setOf("pt"))
        for (w in listOf("brazil", "brasil")) put(w, setOf("br"))
        for (w in listOf("poland", "polska")) put(w, setOf("pl"))
        put("greece", setOf("gr", "cy"))
        put("albania", setOf("al", "xk"))
        put("romania", setOf("ro", "md"))
        put("bulgaria", setOf("bg"))
        put("russia", setOf("ru"))
        put("ukraine", setOf("ua"))
        put("azerbaijan", setOf("az"))
        put("india", setOf("in"))
        put("pakistan", setOf("pk"))
        put("iran", setOf("ir"))
        put("sweden", setOf("se"))
        put("norway", setOf("no"))
        put("denmark", setOf("dk"))
        put("finland", setOf("fi"))
        put("hungary", setOf("hu"))
        put("czech", setOf("cz"))
        put("slovakia", setOf("sk"))
        put("croatia", setOf("hr"))
        put("serbia", setOf("rs"))
        put("bosnia", setOf("ba"))
        put("macedonia", setOf("mk"))
        put("slovenia", setOf("si"))
        put("israel", setOf("il"))
        put("japan", setOf("jp"))
        put("korea", setOf("kr"))
        put("china", setOf("cn"))
        put("mexico", setOf("mx"))
        put("canada", setOf("ca"))
        put("australia", setOf("au"))
    }

    /** Every country a guide id suffix may name; anything else (".tv", ".com", ".hd") is not a country. */
    private val KNOWN: Set<String> = CODES.values.flatten().toSet()

    private val idSuffix = Regex("""\.([a-z]{2})$""")
    private val multiWord = listOf("united kingdom" to setOf("uk", "ie"), "united states" to setOf("us"))
    private val tokenSplit = Regex("""[^\p{L}\p{N}]+""")
    private val upperCode = Regex("""[A-Z]{2,5}""")

    /** Regions of a playlist channel: from its name prefix or suffix ("Eurosport 1 DE"), else its group title. */
    fun ofPlaylistChannel(name: String, groupTitle: String): Set<String> =
        ofName(name).ifEmpty { ofGroup(groupTitle) }

    /** Region named by a tvg-id or guide id suffix: "Eurosport1.de" -> {de}, "BBC1.uk@HD" -> {uk}. */
    fun ofId(id: String?): Set<String> {
        if (id.isNullOrBlank()) return emptySet()
        val code = idSuffix.find(id.substringBefore('@').trim().lowercase())?.groupValues?.get(1) ?: return emptySet()
        val country = if (code == "gb") "uk" else code
        return if (country in KNOWN) setOf(country) else emptySet()
    }

    /** Regions of an XMLTV channel: from its id suffix, else its display name. */
    fun ofGuideChannel(id: String, displayName: String?): Set<String> =
        ofId(id).ifEmpty { displayName?.let(::ofName).orEmpty() }

    private fun ofName(name: String): Set<String> {
        NameCleaner.clean(name).prefix?.let { CODES[it] }?.let { return it }
        // A trailing upper-case code: "Eurosport 1 DE". Lower-case words ("Channel It") are not codes.
        val last = name.trim().split(' ').takeIf { it.size > 1 }?.last()
        if (last != null && upperCode.matches(last)) CODES[last]?.let { return it }
        return emptySet()
    }

    private fun ofGroup(group: String): Set<String> {
        NameCleaner.clean(group).prefix?.let { CODES[it] }?.let { return it }
        val first = group.trim().split(tokenSplit).firstOrNull { it.isNotEmpty() }
        if (first != null && upperCode.matches(first)) CODES[first]?.let { return it }
        val folded = TextNormalizer.fold(group)
        for ((phrase, regions) in multiWord) if (folded.contains(phrase)) return regions
        for (word in folded.split(tokenSplit)) WORDS[word]?.let { return it }
        return emptySet()
    }
}
