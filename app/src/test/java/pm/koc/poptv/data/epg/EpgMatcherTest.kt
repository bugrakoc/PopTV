package pm.koc.poptv.data.epg

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EpgMatcherTest {

    private val guide = listOf(
        EpgMatcher.GuideChannel("trt1.tr", listOf(EpgMatcher.nameKey("TRT 1"), EpgMatcher.nameKey("TRT 1 HD"))),
        EpgMatcher.GuideChannel("ShowTV.tr", listOf(EpgMatcher.nameKey("Show TV"))),
        EpgMatcher.GuideChannel("bbc1.uk", listOf(EpgMatcher.nameKey("BBC One"))),
        EpgMatcher.GuideChannel("kanald.tr", listOf(EpgMatcher.nameKey("Kanal D"))),
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
        val result2 = EpgMatcher.match(listOf(EpgMatcher.Channel("d", "KanalD.de", "zzz")), guide)
        assertThat(result2).containsExactly("d", "kanald.tr")
    }

    @Test
    fun `empty inputs`() {
        assertThat(EpgMatcher.match(emptyList(), guide)).isEmpty()
        assertThat(EpgMatcher.match(listOf(EpgMatcher.Channel("a", "x", "y")), emptyList())).isEmpty()
    }
}
