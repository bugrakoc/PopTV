package pm.koc.poptv.data.classify

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NameCleanerTest {

    private fun name(s: String) = NameCleaner.clean(s).name

    @Test
    fun `strips country prefixes in common styles`() {
        assertThat(name("TR - Kanal D")).isEqualTo("Kanal D")
        assertThat(name("TR: Kanal D")).isEqualTo("Kanal D")
        assertThat(name("|TR| Kanal D")).isEqualTo("Kanal D")
        assertThat(name("[TR] Kanal D")).isEqualTo("Kanal D")
        assertThat(name("TR | Kanal D")).isEqualTo("Kanal D")
        assertThat(name("(UK) BBC One")).isEqualTo("BBC One")
        assertThat(NameCleaner.clean("TR: VIP: Kanal D").prefix).isEqualTo("TR")
        assertThat(name("TR: VIP: Kanal D")).isEqualTo("Kanal D")
    }

    @Test
    fun `does not strip words that only look like prefixes`() {
        assertThat(name("TRT 1")).isEqualTo("TRT 1")
        assertThat(name("TRT-1")).isEqualTo("TRT-1")
        assertThat(name("Kanal 7")).isEqualTo("Kanal 7")
        assertThat(name("TR")).isEqualTo("TR")
    }

    @Test
    fun `removes quality tags`() {
        assertThat(name("TR: TRT 1 FHD")).isEqualTo("TRT 1")
        assertThat(name("Show TV HD")).isEqualTo("Show TV")
        assertThat(name("Sports 4K HEVC")).isEqualTo("Sports")
        assertThat(name("Movie Name 1080p x265")).isEqualTo("Movie Name")
        assertThat(name("ATV ᴴᴰ")).isEqualTo("ATV")
        assertThat(name("Kanal (HD)")).isEqualTo("Kanal")
        assertThat(name("[VIP] Channel [Backup]")).isEqualTo("Channel")
    }

    @Test
    fun `extracts year`() {
        val c = NameCleaner.clean("EN - The Matrix (1999) [4K]")
        assertThat(c.name).isEqualTo("The Matrix")
        assertThat(c.year).isEqualTo(1999)
        assertThat(c.prefix).isEqualTo("EN")
        assertThat(NameCleaner.clean("Inception - 2010").year).isEqualTo(2010)
        // A number in the title is not a year.
        assertThat(NameCleaner.clean("Blade Runner 2049").year).isNull()
        assertThat(name("Blade Runner 2049")).isEqualTo("Blade Runner 2049")
    }

    @Test
    fun `never returns empty`() {
        assertThat(name("HD")).isEqualTo("HD")
        assertThat(name("  [x]  ")).isEqualTo("[x]")
    }

    @Test
    fun `keeps meaningful parentheses`() {
        assertThat(name("Show (US)")).isEqualTo("Show (US)")
    }
}
