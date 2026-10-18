package pm.koc.poptv.data.epg

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

data class XmltvChannel(val id: String, val displayNames: List<String>, val icon: String?)

data class XmltvProgramme(
    val channelId: String,
    val start: Long,
    val end: Long,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val category: String?,
)

data class XmltvStats(
    val channels: Int,
    val programmes: Int,
    val skippedOutsideWindow: Int,
    val malformed: Int,
    val truncated: Boolean,
)

/**
 * Streaming XMLTV parser built on [XmlPullParser]. Gzip input is detected by its magic bytes, so both
 * `.xml` and `.xml.gz` work regardless of the URL. Only programmes overlapping [windowStart, windowEnd)
 * are emitted. Programmes without a `stop` attribute end when the next programme on the same channel
 * starts. A malformed document stops parsing but keeps everything emitted so far.
 *
 * @param newParser creates the pull parser (Android: `Xml.newPullParser()`; tests: kxml2).
 * @param preferredLanguage two-letter language preferred for titles/descriptions (e.g. "tr").
 */
class XmltvParser(
    private val newParser: () -> XmlPullParser,
    private val preferredLanguage: String? = null,
) {

    fun parse(
        input: InputStream,
        windowStart: Long,
        windowEnd: Long,
        onChannel: (XmltvChannel) -> Unit,
        onProgramme: (XmltvProgramme) -> Unit,
    ): XmltvStats {
        val stream = maybeGunzip(input)
        val parser = newParser()
        runCatching { parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false) }
        runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
        parser.setInput(stream, null)

        var channels = 0
        var programmes = 0
        var skipped = 0
        var malformed = 0
        var truncated = false
        // Programmes lacking a stop time wait here for the next programme on their channel.
        val openEnded = HashMap<String, PendingProgramme>()

        fun emit(p: PendingProgramme, end: Long) {
            if (end <= p.start) {
                malformed++
                return
            }
            if (end <= windowStart || p.start >= windowEnd) {
                skipped++
                return
            }
            onProgramme(XmltvProgramme(p.channelId, p.start, end, p.title, p.subtitle, p.description, p.category))
            programmes++
        }

        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "channel" -> readChannel(parser)?.let { onChannel(it); channels++ } ?: malformed++
                        "programme" -> {
                            val p = readProgramme(parser)
                            if (p == null) {
                                malformed++
                            } else {
                                openEnded.remove(p.channelId)?.let { prev ->
                                    // Out-of-order input: fall back to the default duration instead of dropping.
                                    emit(prev, if (p.start > prev.start) p.start else prev.start + DEFAULT_DURATION_MS)
                                }
                                if (p.end != null) emit(p, p.end) else openEnded[p.channelId] = p
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: XmlPullParserException) {
            truncated = true
        } catch (e: java.io.EOFException) {
            truncated = true
        }
        for (p in openEnded.values) emit(p, p.start + DEFAULT_DURATION_MS)
        return XmltvStats(channels, programmes, skipped, malformed, truncated)
    }

    private fun readChannel(parser: XmlPullParser): XmltvChannel? {
        val id = parser.getAttributeValue(null, "id")?.trim()
        val names = ArrayList<Pair<String?, String>>()
        var icon: String? = null
        val depth = parser.depth
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.depth == depth) break
            if (event != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "display-name" -> {
                    val lang = parser.getAttributeValue(null, "lang")
                    readText(parser).trim().takeIf { it.isNotEmpty() }?.let { names.add(lang to it) }
                }
                "icon" -> icon = icon ?: parser.getAttributeValue(null, "src")?.trim()?.takeIf { it.isNotEmpty() }
                else -> skip(parser)
            }
        }
        if (id.isNullOrEmpty()) return null
        val ordered = names.sortedByDescending { it.first.equals(preferredLanguage, ignoreCase = true) }.map { it.second }
        return XmltvChannel(id, ordered.distinct(), icon)
    }

    private class PendingProgramme(
        val channelId: String,
        val start: Long,
        val end: Long?,
        val title: String,
        val subtitle: String?,
        val description: String?,
        val category: String?,
    )

    private fun readProgramme(parser: XmlPullParser): PendingProgramme? {
        val channel = parser.getAttributeValue(null, "channel")?.trim()
        val start = XmltvTime.parse(parser.getAttributeValue(null, "start"))
        val stop = XmltvTime.parse(parser.getAttributeValue(null, "stop"))
        val titles = ArrayList<Pair<String?, String>>()
        val subtitles = ArrayList<Pair<String?, String>>()
        val descs = ArrayList<Pair<String?, String>>()
        var category: String? = null
        val depth = parser.depth
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.depth == depth) break
            if (event != XmlPullParser.START_TAG) continue
            val lang = parser.getAttributeValue(null, "lang")
            when (parser.name) {
                "title" -> readText(parser).trim().takeIf { it.isNotEmpty() }?.let { titles.add(lang to it) }
                "sub-title" -> readText(parser).trim().takeIf { it.isNotEmpty() }?.let { subtitles.add(lang to it) }
                "desc" -> readText(parser).trim().takeIf { it.isNotEmpty() }?.let { descs.add(lang to it) }
                "category" -> {
                    val c = readText(parser).trim()
                    if (category == null && c.isNotEmpty()) category = c
                }
                else -> skip(parser)
            }
        }
        if (channel.isNullOrEmpty() || start == null) return null
        val title = pick(titles) ?: return null
        return PendingProgramme(channel, start, stop, title, pick(subtitles), pick(descs)?.take(MAX_DESC), category)
    }

    private fun pick(values: List<Pair<String?, String>>): String? =
        values.firstOrNull { it.first.equals(preferredLanguage, ignoreCase = true) }?.second ?: values.firstOrNull()?.second

    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        val depth = parser.depth
        while (true) {
            val event = parser.next()
            when {
                event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT -> sb.append(parser.text)
                event == XmlPullParser.END_TAG && parser.depth == depth -> return sb.toString()
                event == XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
    }

    private fun skip(parser: XmlPullParser) {
        var level = 1
        while (level > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> level++
                XmlPullParser.END_TAG -> level--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    companion object {
        private const val DEFAULT_DURATION_MS = 60 * 60 * 1000L
        private const val MAX_DESC = 2000

        /** Wraps the stream in a [GZIPInputStream] when it starts with the gzip magic bytes. */
        fun maybeGunzip(input: InputStream): InputStream {
            val buffered = if (input.markSupported()) input else BufferedInputStream(input, 64 * 1024)
            buffered.mark(4)
            val b1 = buffered.read()
            val b2 = buffered.read()
            buffered.reset()
            return if (b1 == 0x1f && b2 == 0x8b) BufferedInputStream(GZIPInputStream(buffered, 64 * 1024), 64 * 1024) else buffered
        }
    }
}
