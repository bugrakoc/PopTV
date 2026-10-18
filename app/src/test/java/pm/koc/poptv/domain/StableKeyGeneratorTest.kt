package pm.koc.poptv.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StableKeyGeneratorTest {

    @Test
    fun `uses tvg-id when present and unique`() {
        val gen = StableKeyGenerator(emptySet())
        assertThat(gen.keyFor("TRT1.tr", "http://example.com/live/u/p/1.ts")).isEqualTo("t:trt1.tr")
        // Duplicate tvg-id falls back to the url key.
        assertThat(gen.keyFor("trt1.tr", "http://example.com/live/u/p/2.ts")).startsWith("u:")
    }

    @Test
    fun `url key ignores credentials host query and extension`() {
        val a = StableKeyGenerator(setOf("olduser", "oldpass"))
            .keyFor(null, "http://old.example.com:8080/live/olduser/oldpass/12345.ts?token=abc")
        val b = StableKeyGenerator(setOf("newuser", "newpass"))
            .keyFor(null, "https://new.example.com/live/newuser/newpass/12345.m3u8")
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `xtream layouts are recognized without known credentials`() {
        val gen = StableKeyGenerator(emptySet())
        assertThat(gen.normalizedPath("http://example.com/movie/someone/secret/777.mkv")).isEqualTo("/movie/777")
        assertThat(gen.normalizedPath("http://example.com/someone/secret/888")).isEqualTo("/888")
        assertThat(gen.normalizedPath("http://cdn.example.com/hls/channel1/index.m3u8"))
            .isEqualTo("/hls/channel1/index")
    }

    @Test
    fun `different streams get different keys and duplicates get suffixes`() {
        val gen = StableKeyGenerator(emptySet())
        val k1 = gen.keyFor(null, "http://example.com/live/u/p/1.ts")
        val k2 = gen.keyFor(null, "http://example.com/live/u/p/2.ts")
        val k1dup = gen.keyFor(null, "http://example.com/live/u/p/1.ts")
        assertThat(k1).isNotEqualTo(k2)
        assertThat(k1dup).isEqualTo("$k1#2")
    }

    @Test
    fun `keys are deterministic across imports`() {
        val urls = (1..50).map { "http://example.com/series/u/p/$it.mp4" }
        val first = StableKeyGenerator(setOf("u", "p")).let { g -> urls.map { g.keyFor(null, it) } }
        val second = StableKeyGenerator(setOf("u", "p")).let { g -> urls.map { g.keyFor(null, it) } }
        assertThat(first).isEqualTo(second)
    }
}
