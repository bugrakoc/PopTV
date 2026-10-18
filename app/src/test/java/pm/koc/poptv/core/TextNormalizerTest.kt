package pm.koc.poptv.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class TextNormalizerTest {

    @Test
    fun `turkish i variants fold to plain i`() {
        assertThat(TextNormalizer.fold("İSTANBUL")).isEqualTo("istanbul")
        assertThat(TextNormalizer.fold("ISTANBUL")).isEqualTo("istanbul")
        assertThat(TextNormalizer.fold("ıstanbul")).isEqualTo("istanbul")
        assertThat(TextNormalizer.fold("Işık")).isEqualTo("isik")
    }

    @Test
    fun `diacritics are removed`() {
        assertThat(TextNormalizer.fold("Şöyle Güzel Çiçek Ğ")).isEqualTo("soyle guzel cicek g")
        assertThat(TextNormalizer.fold("Café Ñandú")).isEqualTo("cafe nandu")
    }

    @Test
    fun `independent of default locale`() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertThat(TextNormalizer.fold("IPTV")).isEqualTo("iptv")
            Locale.setDefault(Locale.ENGLISH)
            assertThat(TextNormalizer.fold("İPTV")).isEqualTo("iptv")
        } finally {
            Locale.setDefault(old)
        }
    }

    @Test
    fun `search key strips punctuation`() {
        assertThat(TextNormalizer.searchKey("Tom & Jerry: The Movie!")).isEqualTo("tom jerry the movie")
        assertThat(TextNormalizer.searchKey("  TR | Ülke  TV ")).isEqualTo("tr ulke tv")
    }

    @Test
    fun `compact key removes all separators`() {
        assertThat(TextNormalizer.compactKey("TRT 1 HD")).isEqualTo("trt1hd")
        assertThat(TextNormalizer.compactKey("Show-TV")).isEqualTo("showtv")
    }
}
