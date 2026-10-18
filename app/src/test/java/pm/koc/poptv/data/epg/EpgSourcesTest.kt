package pm.koc.poptv.data.epg

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EpgSourcesTest {
    @Test
    fun `order is header, xtream, manual without duplicates`() {
        val candidates = EpgSources.candidates(
            headerUrls = listOf("http://example.com/epg.xml.gz", "not a url", "http://example.com/epg.xml.gz"),
            playlistUrl = "http://example.com:8080/get.php?username=u&password=p&type=m3u_plus",
            manualUrl = "https://guide.example.com/tr.xml",
        )
        assertThat(candidates).containsExactly(
            "http://example.com/epg.xml.gz",
            "http://example.com:8080/xmltv.php?username=u&password=p",
            "https://guide.example.com/tr.xml",
        ).inOrder()
    }

    @Test
    fun `nothing available`() {
        assertThat(EpgSources.candidates(emptyList(), "http://example.com/list.m3u", "")).isEmpty()
        assertThat(EpgSources.candidates(emptyList(), null, null)).isEmpty()
    }
}
