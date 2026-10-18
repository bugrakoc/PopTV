package pm.koc.poptv.data.classify

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EpisodeParserTest {

    private fun ep(s: String) = EpisodeParser.parse(s)

    @Test
    fun `SxxEyy variants`() {
        listOf("Show Name S01E03", "Show Name s01e03", "Show Name S01 E03", "Show Name S1.E3", "Show Name - S01-E03")
            .forEach { title ->
                val e = ep(title)!!
                assertThat(e.showName).isEqualTo("Show Name")
                assertThat(e.season).isEqualTo(1)
                assertThat(e.episode).isEqualTo(3)
                assertThat(e.strong).isTrue()
            }
    }

    @Test
    fun `1x03`() {
        val e = ep("Show Name 2x05")!!
        assertThat(e.showName).isEqualTo("Show Name")
        assertThat(e.season).isEqualTo(2)
        assertThat(e.episode).isEqualTo(5)
    }

    @Test
    fun `season episode words in english and turkish`() {
        ep("Show Name Season 2 Episode 10")!!.let {
            assertThat(it.showName).isEqualTo("Show Name"); assertThat(it.season).isEqualTo(2); assertThat(it.episode).isEqualTo(10)
        }
        ep("Kurtlar Vadisi Sezon 3 Bölüm 12")!!.let {
            assertThat(it.showName).isEqualTo("Kurtlar Vadisi"); assertThat(it.season).isEqualTo(3); assertThat(it.episode).isEqualTo(12)
        }
        ep("KURTLAR VADİSİ SEZON 3 BÖLÜM 12")!!.let {
            assertThat(it.season).isEqualTo(3); assertThat(it.episode).isEqualTo(12)
        }
        ep("Dizi Adı 1. Sezon 4. Bölüm")!!.let {
            assertThat(it.showName).isEqualTo("Dizi Adı"); assertThat(it.season).isEqualTo(1); assertThat(it.episode).isEqualTo(4)
        }
        ep("Dizi Adı S2 B7")!!.let { assertThat(it.season).isEqualTo(2); assertThat(it.episode).isEqualTo(7) }
    }

    @Test
    fun `episode only patterns default to season 1 and are weak`() {
        ep("Dizi Adı 12. Bölüm")!!.let {
            assertThat(it.showName).isEqualTo("Dizi Adı"); assertThat(it.season).isEqualTo(1); assertThat(it.episode).isEqualTo(12)
            assertThat(it.strong).isFalse()
        }
        ep("Some Show Episode 4")!!.let { assertThat(it.episode).isEqualTo(4); assertThat(it.strong).isFalse() }
        ep("Bölüm 5")!!.let { assertThat(it.showName).isEmpty(); assertThat(it.episode).isEqualTo(5) }
    }

    @Test
    fun `prefixes, quality tags, year and episode title`() {
        val e = ep("TR - Breaking Bad (2008) S02E03 - Bit by a Dead Bee FHD")!!
        assertThat(e.prefix).isEqualTo("TR")
        assertThat(e.showName).isEqualTo("Breaking Bad")
        assertThat(e.year).isEqualTo(2008)
        assertThat(e.season).isEqualTo(2)
        assertThat(e.episode).isEqualTo(3)
        assertThat(e.episodeTitle).isEqualTo("Bit by a Dead Bee")

        val bare = ep("EN: The Office 2005 S01E01")!!
        assertThat(bare.showName).isEqualTo("The Office")
        assertThat(bare.year).isEqualTo(2005)
    }

    @Test
    fun `numeric show names are kept`() {
        assertThat(ep("1923 S01E02")!!.showName).isEqualTo("1923")
        assertThat(ep("9-1-1 S05E01")!!.showName).isEqualTo("9-1-1")
    }

    @Test
    fun `non episodes return null`() {
        assertThat(ep("The Matrix (1999)")).isNull()
        assertThat(ep("TRT 1 HD")).isNull()
        assertThat(ep("Kanal 7")).isNull()
        assertThat(ep("Sky Sports 1")).isNull()
        assertThat(ep("Fox 1080p")).isNull()
    }
}
