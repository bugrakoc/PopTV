package pm.koc.poptv.data.epg

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EpgMatcherTest {

    private val guide = listOf(
        EpgMatcher.GuideChannel("trt1.tr", listOf(EpgMatcher.nameKey("TRT 1"), EpgMatcher.nameKey("TRT 1 HD")), setOf("tr")),
        EpgMatcher.GuideChannel("ShowTV.tr", listOf(EpgMatcher.nameKey("Show TV")), setOf("tr")),
        EpgMatcher.GuideChannel("bbc1.uk", listOf(EpgMatcher.nameKey("BBC One")), setOf("uk")),
        EpgMatcher.GuideChannel("kanald.tr", listOf(EpgMatcher.nameKey("Kanal D")), setOf("tr")),
    )

    @Test
    fun `name keys normalize prefixes, quality tags, case and accents`() {
        assertThat(EpgMatcher.nameKey("TR: TRT 1 FHD")).isEqualTo("trt1")
        assertThat(EpgMatcher.nameKey("TRT 1")).isEqualTo("trt1")
        assertThat(EpgMatcher.nameKey("|TR| SHOW TV ᴴᴰ")).isEqualTo("showtv")
        assertThat(EpgMatcher.nameKey("Kanal D")).isEqualTo(EpgMatcher.nameKey("KANAL D HD"))
    }

    @Test
    fun `matches by tvg-id first, case-insensitive`() {
        val result = EpgMatcher.match(
            listOf(EpgMatcher.Channel("a", "SHOWTV.TR", EpgMatcher.nameKey("Something else"))),
            guide,
        )
        assertThat(result).containsExactly("a", "ShowTV.tr")
    }

    @Test
    fun `falls back to normalized name`() {
        val result = EpgMatcher.match(
            listOf(
                EpgMatcher.Channel("a", null, EpgMatcher.nameKey("TR: TRT 1 HD")),
                EpgMatcher.Channel("b", "unknown.id", EpgMatcher.nameKey("UK: BBC One FHD")),
                EpgMatcher.Channel("c", null, EpgMatcher.nameKey("Unmatched Channel")),
            ),
            guide,
        )
        assertThat(result).containsExactly("a", "trt1.tr", "b", "bbc1.uk")
    }

    @Test
    fun `tvg-id used as a name when it is not a guide id`() {
        val result = EpgMatcher.match(listOf(EpgMatcher.Channel("d", "KanalD.tr2", "zzz")), guide)
        // "KanalD.tr2" is not an id; stripping the suffix gives "kanald" which matches the "Kanal D" name.
        assertThat(result).isEmpty()
        val result2 = EpgMatcher.match(listOf(EpgMatcher.Channel("d", "Kanal-D.tr", "zzz")), guide)
        assertThat(result2).containsExactly("d", "kanald.tr")
        // The suffix is also a country hint: a German Kanal D must not get the Turkish guide.
        val result3 = EpgMatcher.match(listOf(EpgMatcher.Channel("d", "KanalD.de", "zzz")), guide)
        assertThat(result3).isEmpty()
    }

    private val eurosport = listOf(
        guideChannel("Eurosport1.de", "Eurosport 1"),
        guideChannel("Eurosport1.uk", "Eurosport 1"),
        guideChannel("Eurosport1.fr", "Eurosport 1"),
        guideChannel("ORF1.at", "ORF 1"),
    )

    private fun guideChannel(id: String, name: String) =
        EpgMatcher.GuideChannel(id, listOf(EpgMatcher.nameKey(name)), ChannelRegion.ofGuideChannel(id, name))

    private fun channel(key: String, name: String, group: String, tvgId: String? = null) =
        EpgMatcher.Channel(key, tvgId, EpgMatcher.nameKey(name), ChannelRegion.ofPlaylistChannel(name, group))

    @Test
    fun `same name in several countries matches each channel to its own country`() {
        val result = EpgMatcher.match(
            listOf(
                channel("de", "DE: Eurosport 1 HD", "DE | SPORT"),
                channel("uk", "UK: Eurosport 1", "UK SPORTS"),
                channel("fr", "Eurosport 1", "FRANCE"),
            ),
            eurosport,
        )
        assertThat(result).containsExactly("de", "Eurosport1.de", "uk", "Eurosport1.uk", "fr", "Eurosport1.fr")
    }

    @Test
    fun `channel from a country without its own guide gets none`() {
        val result = EpgMatcher.match(listOf(channel("tr", "TR: Eurosport 1", "TR SPOR")), eurosport)
        assertThat(result).isEmpty()
    }

    @Test
    fun `channel without a country hint keeps the first name match`() {
        val result = EpgMatcher.match(listOf(channel("x", "Eurosport 1", "Sports")), eurosport)
        assertThat(result).containsExactly("x", "Eurosport1.de")
    }

    @Test
    fun `guide channel without a country is accepted, own country preferred`() {
        val guide = listOf(guideChannel("eurosport1", "Eurosport 1"), guideChannel("Eurosport1.uk", "Eurosport 1"))
        val result = EpgMatcher.match(
            listOf(channel("de", "DE: Eurosport 1", "DE SPORT"), channel("uk", "UK: Eurosport 1", "UK SPORT")),
            guide,
        )
        assertThat(result).containsExactly("de", "eurosport1", "uk", "Eurosport1.uk")
    }

    @Test
    fun `German groups include Austrian channels`() {
        val result = EpgMatcher.match(listOf(channel("orf", "DE: ORF 1", "DE: Österreich")), eurosport)
        assertThat(result).containsExactly("orf", "ORF1.at")
    }

    @Test
    fun `tvg-id of another country is replaced by the channel's own country`() {
        val result = EpgMatcher.match(
            listOf(
                channel("uk", "UK: Eurosport 1", "UK SPORT", tvgId = "Eurosport1.de"),
                channel("tr", "TR: Eurosport 1", "TR SPOR", tvgId = "Eurosport1.de"),
            ),
            eurosport,
        )
        // With no Turkish guide to switch to, the provider's explicit tvg-id is kept.
        assertThat(result).containsExactly("uk", "Eurosport1.uk", "tr", "Eurosport1.de")
    }

    @Test
    fun `tvg-id suffix is a country hint for the name match`() {
        val result = EpgMatcher.match(listOf(channel("x", "Eurosport 1", "Sports", tvgId = "eurosport1hd.uk")), eurosport)
        assertThat(result).containsExactly("x", "Eurosport1.uk")
    }

    @Test
    fun `empty inputs`() {
        assertThat(EpgMatcher.match(emptyList(), guide)).isEmpty()
        assertThat(EpgMatcher.match(listOf(EpgMatcher.Channel("a", "x", "y")), emptyList())).isEmpty()
    }

    @Test
    fun `a tvg-id shared by many different channels is a placeholder and ignored`() {
        val guide = listOf(
            guideChannel("24.tr", "TR: b**N SP*RTS MAX 2 FHD"),
            guideChannel("beinsportsmax2.tr", "TR: b**N SP*RTS MAX 2 HD"),
        )
        val names = listOf("EURO STAR", "EURO D", "ON4 TV", "TV 5", "TV 5 Haber", "KIBRIS KANAL T", "TRT 4K", "b**N SP*RTS MAX 2 FHD")
        val channels = names.mapIndexed { i, n -> channel("c$i", "TR: $n", "TR ULUSAL", tvgId = "24.tr") }
        // The beIN channel finds its own guide by name; the others get none instead of the news channel's.
        assertThat(EpgMatcher.match(channels, guide)).containsExactly("c7", "beinsportsmax2.tr")
        // Variants of one channel (below the threshold) keep their shared id.
        assertThat(EpgMatcher.match(channels.take(EpgMatcher.PLACEHOLDER_NAMES - 1), guide)).hasSize(EpgMatcher.PLACEHOLDER_NAMES - 1)
    }
}
