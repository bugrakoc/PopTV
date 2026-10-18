package pm.koc.poptv.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UrlUtilsTest {

    private val xtream = "http://example.com:8080/get.php?username=demo_user&password=demo_pass&type=m3u_plus&output=ts"

    @Test
    fun `validates http urls`() {
        assertThat(UrlUtils.isValidHttpUrl(xtream)).isTrue()
        assertThat(UrlUtils.isValidHttpUrl("https://example.com/list.m3u")).isTrue()
        assertThat(UrlUtils.isValidHttpUrl("ftp://example.com/list.m3u")).isFalse()
        assertThat(UrlUtils.isValidHttpUrl("example.com/list.m3u")).isFalse()
        assertThat(UrlUtils.isValidHttpUrl("http://")).isFalse()
        assertThat(UrlUtils.isValidHttpUrl("http://exa mple.com")).isFalse()
        assertThat(UrlUtils.isValidHttpUrl("")).isFalse()
    }

    @Test
    fun `extracts credentials`() {
        assertThat(UrlUtils.credentials(xtream)).containsExactly("demo_user", "demo_pass")
        assertThat(UrlUtils.credentials("http://example.com/list.m3u")).isEmpty()
    }

    @Test
    fun `redacts credentials`() {
        val redacted = UrlUtils.redact(xtream)
        assertThat(redacted).doesNotContain("demo_user")
        assertThat(redacted).doesNotContain("demo_pass")
        assertThat(UrlUtils.redact("http://u1:p1@example.com/x")).doesNotContain("u1:p1")
    }

    @Test
    fun `derives xtream xmltv url`() {
        assertThat(UrlUtils.xtreamXmltvUrl(xtream))
            .isEqualTo("http://example.com:8080/xmltv.php?username=demo_user&password=demo_pass")
        assertThat(UrlUtils.xtreamXmltvUrl("http://example.com/sub/get.php?username=a%40b&password=p"))
            .isEqualTo("http://example.com/sub/xmltv.php?username=a%40b&password=p")
        assertThat(UrlUtils.xtreamXmltvUrl("http://example.com/list.m3u")).isNull()
        assertThat(UrlUtils.xtreamXmltvUrl("http://example.com/get.php?username=a")).isNull()
    }

    @Test
    fun `extension and path`() {
        assertThat(UrlUtils.extension("http://example.com/movie/u/p/123.MKV?x=1")).isEqualTo("mkv")
        assertThat(UrlUtils.extension("http://example.com/live/u/p/123")).isEmpty()
        assertThat(UrlUtils.extension("http://example.com/a.b/c")).isEmpty()
        assertThat(UrlUtils.pathOf("http://example.com:80/a/b.ts?q#f")).isEqualTo("/a/b.ts")
        assertThat(UrlUtils.pathOf("http://example.com")).isEmpty()
    }
}
