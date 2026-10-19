package pm.koc.poptv.data.classify

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import pm.koc.poptv.data.m3u.M3uEntry
import pm.koc.poptv.domain.CategoryOverride
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.domain.StableKeyGenerator

class EntryMapperTest {

    private fun mapper(overrides: Map<String, CategoryOverride> = emptyMap()) =
        EntryMapper(StableKeyGenerator(setOf("user", "pass")), overrides, generation = 3)

    private fun entry(title: String, url: String, group: String = "", attrs: Map<String, String> = emptyMap()) =
        M3uEntry(title, url, -1, attrs + mapOf("group-title" to group))

    @Test
    fun `episodes of the same show share a series key`() {
        val m = mapper()
        val a = m.map(entry("TR - Show Name S01E01 FHD", "http://example.com/series/user/pass/1.mp4", "Diziler"))
        val b = m.map(entry("TR - Show Name S01E02", "http://example.com/series/user/pass/2.mp4", "Diziler"))
        val c = m.map(entry("TR - Show Name S02E01 HD", "http://example.com/series/user/pass/3.mp4", "Diziler"))
        assertThat(a.type).isEqualTo(ContentType.SERIES)
        assertThat(a.seriesKey).isEqualTo("tr:show name")
        assertThat(listOf(b.seriesKey, c.seriesKey)).containsExactly(a.seriesKey, a.seriesKey)
        assertThat(a.seriesName).isEqualTo("Show Name")
        assertThat(c.season).isEqualTo(2)
        assertThat(c.episode).isEqualTo(1)
    }

    @Test
    fun `language versions stay separate shows`() {
        val m = mapper()
        val tr = m.map(entry("TR - Friends S01E01", "http://example.com/series/user/pass/1.mp4"))
        val en = m.map(entry("EN - Friends S01E01", "http://example.com/series/user/pass/2.mp4"))
        assertThat(tr.seriesKey).isNotEqualTo(en.seriesKey)
    }

    @Test
    fun `titles without show name use the group title`() {
        val m = mapper()
        val e = m.map(entry("Bölüm 5", "http://example.com/series/user/pass/5.mp4", "Kuruluş Osman"))
        assertThat(e.seriesName).isEqualTo("Kuruluş Osman")
        assertThat(e.episode).isEqualTo(5)
        assertThat(e.season).isEqualTo(1)
    }

    @Test
    fun `channel numbers use tvg-chno or live order`() {
        val m = mapper()
        val a = m.map(entry("A", "http://example.com/live/user/pass/1.ts", "News", mapOf("tvg-chno" to "101")))
        val b = m.map(entry("B", "http://example.com/live/user/pass/2.ts", "News"))
        val movie = m.map(entry("Film (2020)", "http://example.com/movie/user/pass/3.mkv", "Movies"))
        val c = m.map(entry("C", "http://example.com/live/user/pass/4.ts", "News"))
        assertThat(a.number).isEqualTo(101)
        assertThat(b.number).isEqualTo(2)
        assertThat(movie.number).isEqualTo(0)
        assertThat(c.number).isEqualTo(3)
        assertThat(listOf(a.sortIndex, b.sortIndex, movie.sortIndex, c.sortIndex)).containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun `overrides change type or hide while keeping detected type`() {
        val m = mapper(mapOf("Odd Group" to CategoryOverride.MOVIE, "Adult" to CategoryOverride.HIDDEN))
        val moved = m.map(entry("Thing", "http://example.com/live/user/pass/1.ts", "Odd Group"))
        assertThat(moved.type).isEqualTo(ContentType.MOVIE)
        assertThat(moved.detectedType).isEqualTo(ContentType.LIVE)
        val hidden = m.map(entry("X", "http://example.com/live/user/pass/2.ts", "Adult"))
        assertThat(hidden.hidden).isTrue()
    }

    @Test
    fun `display name cleaned, search name folded, year extracted, headers kept`() {
        val e = mapper().map(
            M3uEntry(
                "TR: İnci Taneleri (2023) 4K",
                "http://example.com/movie/user/pass/9.mkv",
                -1,
                mapOf("group-title" to "Filmler", "tvg-logo" to "http://example.com/p.jpg"),
                userAgent = "UA",
                referrer = "http://example.com/r",
            ),
        )
        assertThat(e.displayName).isEqualTo("İnci Taneleri")
        assertThat(e.searchName).isEqualTo("tr inci taneleri 2023 4k")
        assertThat(e.year).isEqualTo(2023)
        assertThat(e.logo).isEqualTo("http://example.com/p.jpg")
        assertThat(e.userAgent).isEqualTo("UA")
        assertThat(e.referrer).isEqualTo("http://example.com/r")
        assertThat(e.generation).isEqualTo(3)
    }

    @Test
    fun `epg name key normalizes the channel name`() {
        val e = mapper().map(entry("TR: TRT 1 HD", "http://example.com/live/user/pass/1.ts", "Ulusal"))
        assertThat(e.epgNameKey).isEqualTo("trt1")
    }

    @Test
    fun `known show membership wins over the title`() {
        val e = mapper().map(
            entry("Pilot", "http://example.com/series/user/pass/9.mkv", "Diziler", mapOf("tvg-type" to "series")),
            sortIndex = 500,
            show = EntryMapper.ShowRef("tr:friends", "Friends", season = 1, episode = 2),
        )
        assertThat(e.type).isEqualTo(ContentType.SERIES)
        assertThat(e.seriesKey).isEqualTo("tr:friends")
        assertThat(e.seriesName).isEqualTo("Friends")
        assertThat(e.season).isEqualTo(1)
        assertThat(e.episode).isEqualTo(2)
        assertThat(e.sortIndex).isEqualTo(500)
    }

    @Test
    fun `show titles get the key their episodes are grouped by`() {
        val episode = mapper().map(entry("TR - Show Name S01E01 FHD", "http://example.com/series/user/pass/1.mp4", "Diziler"))
        val show = EntryMapper.showIdentity("TR - Show Name (2019)")
        assertThat(show.key).isEqualTo(episode.seriesKey)
        assertThat(show.name).isEqualTo("Show Name")
        assertThat(show.year).isEqualTo(2019)
    }

    @Test
    fun `explicit sort indices do not disturb arrival order`() {
        val m = mapper()
        m.map(entry("A", "http://example.com/live/user/pass/1.ts"), sortIndex = 900)
        assertThat(m.map(entry("B", "http://example.com/live/user/pass/2.ts")).sortIndex).isEqualTo(1)
    }
}
