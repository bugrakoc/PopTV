package pm.koc.poptv.data.epg

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class XmltvParserTest {

    private val base = XmltvTime.parse("20240115120000 +0000")!!
    private val hour = 3_600_000L

    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE tv SYSTEM "xmltv.dtd">
        <tv generator-info-name="test">
          <channel id="trt1.tr">
            <display-name lang="en">TRT 1 HD</display-name>
            <display-name lang="tr">TRT 1</display-name>
            <icon src="http://example.com/trt1.png"/>
          </channel>
          <channel id="news.example"><display-name>News &amp; More</display-name></channel>
          <channel><display-name>No id</display-name></channel>
          <programme start="20240115110000 +0000" stop="20240115120000 +0000" channel="trt1.tr">
            <title lang="en">Morning Show</title><title lang="tr">Sabah Programı</title>
            <desc lang="tr">Açıklama</desc><category lang="en">Talk</category>
          </programme>
          <programme start="20240115120000 +0000" stop="20240115130000 +0000" channel="trt1.tr">
            <title>Noon News</title><sub-title>Edition 1</sub-title>
            <credits><director>Someone</director></credits>
          </programme>
          <programme start="20240101000000 +0000" stop="20240101010000 +0000" channel="trt1.tr"><title>Too old</title></programme>
          <programme start="20240125000000 +0000" stop="20240125010000 +0000" channel="trt1.tr"><title>Too far</title></programme>
          <programme start="20240115140000 +0300" channel="news.example"><title>Open ended A</title></programme>
          <programme start="20240115150000 +0300" channel="news.example"><title>Open ended B</title></programme>
          <programme start="20240115100000 +0000" channel="news.example"><title>Out of order</title></programme>
          <programme start="bad" stop="20240115130000 +0000" channel="trt1.tr"><title>Broken</title></programme>
          <programme start="20240115130000 +0000" stop="20240115140000 +0000" channel="trt1.tr"><title><![CDATA[Film <Gece>]]></title></programme>
        </tv>
    """.trimIndent()

    private fun parse(bytes: ByteArray, lang: String? = "tr"): Triple<List<XmltvChannel>, List<XmltvProgramme>, XmltvStats> {
        val channels = mutableListOf<XmltvChannel>()
        val programmes = mutableListOf<XmltvProgramme>()
        val stats = XmltvParser({ KXmlParser() }, lang).parse(
            ByteArrayInputStream(bytes),
            windowStart = base - 6 * hour,
            windowEnd = base + 3 * 24 * hour,
            onChannel = { channels += it },
            onProgramme = { programmes += it },
        )
        return Triple(channels, programmes, stats)
    }

    @Test
    fun `parses channels with preferred language first`() {
        val (channels, _, stats) = parse(sample.toByteArray())
        assertThat(channels.map { it.id }).containsExactly("trt1.tr", "news.example").inOrder()
        assertThat(channels[0].displayNames).containsExactly("TRT 1", "TRT 1 HD").inOrder()
        assertThat(channels[0].icon).isEqualTo("http://example.com/trt1.png")
        assertThat(channels[1].displayNames).containsExactly("News & More")
        assertThat(stats.malformed).isAtLeast(2) // channel without id, programme with bad start
    }

    @Test
    fun `parses programmes within the window and prefers language`() {
        val (_, programmes, stats) = parse(sample.toByteArray())
        val titles = programmes.map { it.title }
        assertThat(titles).containsAtLeast("Sabah Programı", "Noon News", "Film <Gece>")
        assertThat(titles).containsNoneOf("Too old", "Too far", "Broken")
        assertThat(stats.skippedOutsideWindow).isEqualTo(2)
        val morning = programmes.first { it.title == "Sabah Programı" }
        assertThat(morning.description).isEqualTo("Açıklama")
        assertThat(morning.category).isEqualTo("Talk")
        assertThat(morning.end - morning.start).isEqualTo(hour)
        assertThat(programmes.first { it.title == "Noon News" }.subtitle).isEqualTo("Edition 1")
    }

    @Test
    fun `open ended programmes end at the next start on the same channel`() {
        val (_, programmes, _) = parse(sample.toByteArray())
        val a = programmes.first { it.title == "Open ended A" }
        assertThat(a.end).isEqualTo(XmltvTime.parse("20240115150000 +0300"))
        // B is followed by a programme that starts earlier: it keeps the default duration instead of being dropped.
        val b = programmes.first { it.title == "Open ended B" }
        assertThat(b.end - b.start).isEqualTo(hour)
        // The last open-ended programme gets a default duration of one hour.
        val last = programmes.first { it.title == "Out of order" }
        assertThat(last.end - last.start).isEqualTo(hour)
    }

    @Test
    fun `handles gzip input`() {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(sample.toByteArray()) }
        val (channels, programmes, _) = parse(out.toByteArray())
        assertThat(channels).hasSize(2)
        assertThat(programmes).isNotEmpty()
    }

    @Test
    fun `truncated document keeps what was parsed`() {
        val cut = sample.substring(0, sample.indexOf("Noon News"))
        val (channels, programmes, _) = parse(cut.toByteArray())
        assertThat(channels).hasSize(2)
        assertThat(programmes.map { it.title }).contains("Sabah Programı")
    }

    @Test
    fun `garbage input does not crash`() {
        val (channels, programmes, stats) = parse("<html><body>error".toByteArray())
        assertThat(channels).isEmpty()
        assertThat(programmes).isEmpty()
        assertThat(stats.programmes).isEqualTo(0)
    }
}
