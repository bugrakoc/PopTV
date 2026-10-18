package pm.koc.poptv.data.m3u

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset

class M3uParserTest {

    private fun parse(text: String, charset: Charset = Charsets.UTF_8) = parseBytes(text.toByteArray(charset))

    private fun parseBytes(bytes: ByteArray): Triple<M3uHeader?, List<M3uEntry>, M3uParseStats> {
        var header: M3uHeader? = null
        val entries = mutableListOf<M3uEntry>()
        val stats = M3uParser().parse(ByteArrayInputStream(bytes), { header = it }) { entries += it }
        return Triple(header, entries, stats)
    }

    @Test
    fun `parses header epg urls and basic entries`() {
        val (header, entries, stats) = parse(
            """
            #EXTM3U url-tvg="http://example.com/epg.xml.gz,http://example.com/epg2.xml" x-tvg-url="http://example.com/epg.xml.gz"
            #EXTINF:-1 tvg-id="ch1.tr" tvg-name="Channel One" tvg-logo="http://example.com/1.png" group-title="News" tvg-chno="7",Channel One HD
            http://example.com/live/user/pass/1.ts
            #EXTINF:-1 tvg-id="" group-title="Movies",Some Movie (2019)
            http://example.com/movie/user/pass/2.mkv
            """.trimIndent()
        )
        assertThat(stats.sawHeader).isTrue()
        assertThat(stats.entries).isEqualTo(2)
        assertThat(header!!.epgUrls).containsExactly("http://example.com/epg.xml.gz", "http://example.com/epg2.xml").inOrder()

        val first = entries[0]
        assertThat(first.title).isEqualTo("Channel One HD")
        assertThat(first.url).isEqualTo("http://example.com/live/user/pass/1.ts")
        assertThat(first.tvgId).isEqualTo("ch1.tr")
        assertThat(first.tvgName).isEqualTo("Channel One")
        assertThat(first.tvgLogo).isEqualTo("http://example.com/1.png")
        assertThat(first.group).isEqualTo("News")
        assertThat(first.tvgChno).isEqualTo(7)
        assertThat(first.duration).isEqualTo(-1)

        assertThat(entries[1].tvgId).isNull() // blank attribute
        assertThat(entries[1].group).isEqualTo("Movies")
    }

    @Test
    fun `commas inside quoted attributes and titles`() {
        val (_, entries, _) = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-name="A, B" group-title="Action, Adventure",Title, with, commas
            http://example.com/a.mp4
            """.trimIndent()
        )
        assertThat(entries.single().title).isEqualTo("Title, with, commas")
        assertThat(entries.single().group).isEqualTo("Action, Adventure")
        assertThat(entries.single().tvgName).isEqualTo("A, B")
    }

    @Test
    fun `unquoted and single quoted attributes`() {
        val (_, entries, _) = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id=abc.tr tvg-logo='http://example.com/x.png' group-title=Sports Extra,Sport 1
            http://example.com/s1
            """.trimIndent()
        )
        val e = entries.single()
        assertThat(e.tvgId).isEqualTo("abc.tr")
        assertThat(e.tvgLogo).isEqualTo("http://example.com/x.png")
        assertThat(e.group).isEqualTo("Sports Extra")
        assertThat(e.title).isEqualTo("Sport 1")
    }

    @Test
    fun `attribute keys are case insensitive`() {
        val (_, entries, _) = parse("#EXTM3U\n#EXTINF:-1 TVG-ID=\"X\" Group-Title=\"G\",T\nhttp://example.com/1")
        assertThat(entries.single().tvgId).isEqualTo("X")
        assertThat(entries.single().group).isEqualTo("G")
    }

    @Test
    fun `handles CRLF, CR and BOM`() {
        val text = "﻿#EXTM3U\r\n#EXTINF:-1,One\r\nhttp://example.com/1\r#EXTINF:-1,Two\rhttp://example.com/2\n"
        val (_, entries, stats) = parse(text)
        assertThat(stats.sawHeader).isTrue()
        assertThat(entries.map { it.title }).containsExactly("One", "Two").inOrder()
        assertThat(entries.map { it.url }).containsExactly("http://example.com/1", "http://example.com/2").inOrder()
    }

    @Test
    fun `falls back to windows-1254 for non utf8 lines`() {
        val text = "#EXTM3U\n#EXTINF:-1 group-title=\"Ulusal\",Şov TV İzle ğüç\nhttp://example.com/1\n"
        val (_, entries, _) = parse(text, Charset.forName("windows-1254"))
        assertThat(entries.single().title).isEqualTo("Şov TV İzle ğüç")
    }

    @Test
    fun `utf8 turkish text stays intact`() {
        val (_, entries, _) = parse("#EXTM3U\n#EXTINF:-1 group-title=\"Çocuk\",İstanbul Işık\nhttp://example.com/1\n")
        assertThat(entries.single().title).isEqualTo("İstanbul Işık")
        assertThat(entries.single().group).isEqualTo("Çocuk")
    }

    @Test
    fun `utf16 with bom`() {
        val bytes = "#EXTM3U\n#EXTINF:-1,Ünlü\nhttp://example.com/1\n".toByteArray(Charsets.UTF_16)
        val (_, entries, _) = parseBytes(bytes)
        assertThat(entries.single().title).isEqualTo("Ünlü")
    }

    @Test
    fun `extgrp and extvlcopt apply to the next entry only`() {
        val (_, entries, _) = parse(
            """
            #EXTM3U
            #EXTINF:-1,With Options
            #EXTGRP:Group From Extgrp
            #EXTVLCOPT:http-user-agent=Mozilla/5.0 Test
            #EXTVLCOPT:http-referrer=http://example.com/ref
            http://example.com/1
            #EXTINF:-1,Plain
            http://example.com/2
            """.trimIndent()
        )
        assertThat(entries[0].group).isEqualTo("Group From Extgrp")
        assertThat(entries[0].userAgent).isEqualTo("Mozilla/5.0 Test")
        assertThat(entries[0].referrer).isEqualTo("http://example.com/ref")
        assertThat(entries[1].group).isEmpty()
        assertThat(entries[1].userAgent).isNull()
        assertThat(entries[1].referrer).isNull()
    }

    @Test
    fun `group-title wins over extgrp`() {
        val (_, entries, _) = parse("#EXTM3U\n#EXTINF:-1 group-title=\"A\",T\n#EXTGRP:B\nhttp://example.com/1")
        assertThat(entries.single().group).isEqualTo("A")
    }

    @Test
    fun `kodi style header suffix and exthttp`() {
        val (_, entries, _) = parse(
            """
            #EXTM3U
            #EXTINF:-1,Kodi
            http://example.com/1.m3u8|User-Agent=Agent%201&Referer=http://example.com/r
            #EXTINF:-1,Json
            #EXTHTTP:{"User-Agent":"Agent 2","Referer":"http://example.com/r2"}
            http://example.com/2.m3u8
            """.trimIndent()
        )
        assertThat(entries[0].url).isEqualTo("http://example.com/1.m3u8")
        assertThat(entries[0].userAgent).isEqualTo("Agent 1")
        assertThat(entries[0].referrer).isEqualTo("http://example.com/r")
        assertThat(entries[1].userAgent).isEqualTo("Agent 2")
        assertThat(entries[1].referrer).isEqualTo("http://example.com/r2")
    }

    @Test
    fun `missing title uses tvg-name then url`() {
        val (_, entries, _) = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-name="Named"
            http://example.com/1
            http://example.com/path/file.ts?token=1
            """.trimIndent()
        )
        assertThat(entries[0].title).isEqualTo("Named")
        assertThat(entries[1].title).isEqualTo("file.ts")
    }

    @Test
    fun `garbage lines and dangling extinf are skipped`() {
        val (_, entries, stats) = parse(
            """
            #EXTM3U
            #EXTINF:-1,Lost
            #EXTINF:-1,Kept
            not a url
            http://example.com/1
            """.trimIndent()
        )
        assertThat(entries.map { it.title }).containsExactly("Kept")
        assertThat(stats.skippedLines).isEqualTo(2)
    }

    @Test
    fun `html error page yields no entries and no header`() {
        val (header, entries, stats) = parse("<html><body>Account expired</body></html>")
        assertThat(header).isNull()
        assertThat(entries).isEmpty()
        assertThat(stats.sawHeader).isFalse()
    }

    @Test
    fun `unterminated quote does not swallow the title`() {
        val (_, entries, _) = parse("#EXTM3U\n#EXTINF:-1 tvg-name=\"Broken,Title Here\nhttp://example.com/1")
        assertThat(entries.single().title).isEqualTo("Title Here")
    }

    @Test
    fun `streams a large playlist without holding it in memory`() {
        val count = 200_000
        val input = object : InputStream() {
            private var index = 0
            private var current: ByteArray = "#EXTM3U\n".toByteArray()
            private var pos = 0
            override fun read(): Int {
                while (pos >= current.size) {
                    if (index >= count) return -1
                    current = "#EXTINF:-1 tvg-id=\"c$index\" group-title=\"G${index % 50}\",Channel $index\nhttp://example.com/live/u/p/$index.ts\n".toByteArray()
                    pos = 0
                    index++
                }
                return current[pos++].toInt() and 0xFF
            }
        }
        var seen = 0
        var last: M3uEntry? = null
        val stats = M3uParser().parse(input) { seen++; last = it }
        assertThat(seen).isEqualTo(count)
        assertThat(stats.entries).isEqualTo(count)
        assertThat(last!!.title).isEqualTo("Channel ${count - 1}")
    }

    @Test
    fun `parseExtInf duration and attributes`() {
        val inf = M3uParser.parseExtInf("123.5 tvg-type=\"movie\" bare-token other=\"x\",Name")
        assertThat(inf.duration).isEqualTo(123)
        assertThat(inf.attributes).containsEntry("tvg-type", "movie")
        assertThat(inf.attributes).containsEntry("other", "x")
        assertThat(inf.title).isEqualTo("Name")
    }
}
