package pm.koc.poptv.data.m3u

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Streaming M3U / M3U8 (extended M3U) parser.
 *
 * The input is consumed line by line and entries are handed to a callback as soon as their URL line is
 * read, so memory use is independent of playlist size. Handles:
 * - `#EXTM3U` header attributes (`url-tvg`, `x-tvg-url`, ...)
 * - `#EXTINF` with quoted, single-quoted and unquoted attributes and commas inside titles/values
 * - `#EXTGRP`, `#EXTVLCOPT` (user agent / referrer), `#EXTHTTP` and Kodi-style `url|User-Agent=...` headers
 * - LF, CRLF and CR line endings, a UTF-8 BOM, UTF-16 files with BOM, and lines that are not valid UTF-8
 *   (decoded with [fallbackCharset], Windows-1254 by default as it covers Turkish and Western text).
 */
class M3uParser(
    private val fallbackCharset: Charset = Charset.forName("windows-1254"),
    private val maxLineBytes: Int = 64 * 1024,
) {

    /**
     * Parses [input] (not closed by this method). [onEntry] may throw (e.g. on coroutine cancellation),
     * which aborts parsing.
     */
    fun parse(
        input: InputStream,
        onHeader: (M3uHeader) -> Unit = {},
        onEntry: (M3uEntry) -> Unit,
    ): M3uParseStats {
        val state = State(onHeader, onEntry)
        val pushback = PushbackInputStream(input, 2)
        val bom = ByteArray(2)
        val read = pushback.readFully(bom)
        val utf16 = read == 2 && ((bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte()) ||
            (bom[0] == 0xFE.toByte() && bom[1] == 0xFF.toByte()))
        if (read > 0) pushback.unread(bom, 0, read)

        if (utf16) {
            BufferedReader(InputStreamReader(pushback, Charsets.UTF_16)).use { reader ->
                while (true) state.onLine(reader.readLine() ?: break)
            }
        } else {
            ByteLineReader(pushback, maxLineBytes, fallbackCharset).forEachLine(state::onLine)
        }
        return state.stats()
    }

    private class State(
        private val onHeader: (M3uHeader) -> Unit,
        private val onEntry: (M3uEntry) -> Unit,
    ) {
        private var lines = 0
        private var entries = 0
        private var skipped = 0
        private var sawHeader = false
        private var first = true

        private var pendingInf: ExtInf? = null
        private var pendingGroup: String? = null
        private var pendingUa: String? = null
        private var pendingRef: String? = null

        fun stats() = M3uParseStats(entries, lines, sawHeader, skipped)

        fun onLine(raw: String) {
            lines++
            var line = raw
            if (first) {
                line = line.removePrefix("﻿")
            }
            line = line.trim()
            if (line.isEmpty()) return
            first = false

            if (line[0] == '#') {
                onDirective(line)
                return
            }
            onUrl(line)
        }

        private fun onDirective(line: String) {
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    if (!sawHeader) {
                        sawHeader = true
                        val attrs = parseAttributes(line, "#EXTM3U".length).attributes
                        onHeader(M3uHeader(attrs))
                    }
                }
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    if (pendingInf != null) skipped++ // previous #EXTINF had no URL
                    pendingInf = parseExtInf(line.substring("#EXTINF:".length))
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) ->
                    pendingGroup = line.substring("#EXTGRP:".length).trim()
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substring("#EXTVLCOPT:".length).trim()
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim().trim('"')
                    when (key) {
                        "http-user-agent" -> pendingUa = value.nullIfBlank()
                        "http-referrer", "http-referer" -> pendingRef = value.nullIfBlank()
                    }
                }
                line.startsWith("#EXTHTTP:", ignoreCase = true) -> {
                    val json = line.substring("#EXTHTTP:".length)
                    jsonValue(json, "user-agent")?.let { pendingUa = it }
                    (jsonValue(json, "referer") ?: jsonValue(json, "referrer"))?.let { pendingRef = it }
                }
                line.startsWith("#KODIPROP:", ignoreCase = true) -> {
                    val prop = line.substring("#KODIPROP:".length)
                    if (prop.contains("stream_headers", ignoreCase = true)) {
                        applyHeaderString(prop.substringAfter('=', ""))
                    }
                }
            }
        }

        private fun onUrl(line: String) {
            var url = line
            // Kodi style: http://host/stream.ts|User-Agent=Foo&Referer=http://bar
            val pipe = url.indexOf('|')
            if (pipe > 0) {
                applyHeaderString(url.substring(pipe + 1))
                url = url.substring(0, pipe).trim()
            }
            val inf = pendingInf
            if (!url.contains("://")) {
                // Garbage (HTML error page, relative path, ...). Keep pending metadata for the next line.
                skipped++
                return
            }
            val title = inf?.title.nullIfBlank()
                ?: inf?.attributes?.get("tvg-name").nullIfBlank()
                ?: url.substringBefore('?').substringAfterLast('/').ifBlank { url }
            onEntry(
                M3uEntry(
                    title = title,
                    url = url,
                    duration = inf?.duration,
                    attributes = inf?.attributes ?: emptyMap(),
                    extGroup = pendingGroup,
                    userAgent = pendingUa,
                    referrer = pendingRef,
                )
            )
            entries++
            pendingInf = null
            pendingGroup = null
            pendingUa = null
            pendingRef = null
        }

        private fun applyHeaderString(headers: String) {
            for (part in headers.split('&')) {
                val k = part.substringBefore('=').trim().lowercase()
                val rawValue = part.substringAfter('=', "").trim()
                val v = runCatching { java.net.URLDecoder.decode(rawValue, "UTF-8") }.getOrDefault(rawValue)
                when (k) {
                    "user-agent" -> pendingUa = v.nullIfBlank()
                    "referer", "referrer" -> pendingRef = v.nullIfBlank()
                }
            }
        }
    }

    internal data class ExtInf(val duration: Int?, val attributes: Map<String, String>, val title: String)

    internal data class Attributes(val attributes: Map<String, String>, val titleStart: Int)

    companion object {

        /** Parses the part of an `#EXTINF:` line after the colon. */
        internal fun parseExtInf(body: String): ExtInf {
            var i = 0
            val n = body.length
            while (i < n && body[i].isWhitespace()) i++
            val durStart = i
            while (i < n && !body[i].isWhitespace() && body[i] != ',') i++
            val duration = body.substring(durStart, i).toFloatOrNull()?.toInt()
            val attrs = parseAttributes(body, i)
            val title = if (attrs.titleStart in 0..n) body.substring(attrs.titleStart).trim() else ""
            return ExtInf(duration, attrs.attributes, title)
        }

        /**
         * Parses `key="value"` pairs starting at [start]. Stops at the first comma outside quotes;
         * [Attributes.titleStart] is the index after that comma, or -1 if there was none.
         */
        internal fun parseAttributes(s: String, start: Int): Attributes {
            val attrs = LinkedHashMap<String, String>()
            var i = start
            val n = s.length
            while (true) {
                while (i < n && s[i].isWhitespace()) i++
                if (i >= n) return Attributes(attrs, -1)
                if (s[i] == ',') return Attributes(attrs, i + 1)

                val keyStart = i
                while (i < n && s[i] != '=' && s[i] != ',' && !s[i].isWhitespace()) i++
                if (i >= n) return Attributes(attrs, -1)
                if (s[i] != '=') continue // bare token without a value: ignore it
                val key = s.substring(keyStart, i).lowercase()
                i++ // '='

                val value: String
                if (i < n && (s[i] == '"' || s[i] == '\'')) {
                    val quote = s[i]
                    i++
                    val close = s.indexOf(quote, i)
                    if (close < 0) {
                        // Unterminated quote: take the value up to the next comma.
                        val comma = s.indexOf(',', i)
                        val end = if (comma < 0) n else comma
                        value = s.substring(i, end)
                        i = end
                    } else {
                        value = s.substring(i, close)
                        i = close + 1
                    }
                } else {
                    val valueStart = i
                    while (i < n) {
                        val c = s[i]
                        if (c == ',') break
                        if (c.isWhitespace() && nextIsAttribute(s, i)) break
                        i++
                    }
                    value = s.substring(valueStart, i).trim()
                }
                if (key.isNotEmpty()) attrs[key] = value.trim()
            }
        }

        private fun nextIsAttribute(s: String, from: Int): Boolean {
            var i = from
            val n = s.length
            while (i < n && s[i].isWhitespace()) i++
            val start = i
            while (i < n && (s[i].isLetterOrDigit() || s[i] == '-' || s[i] == '_')) i++
            return i > start && i < n && s[i] == '='
        }

        private fun jsonValue(json: String, key: String): String? {
            val regex = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
            return regex.find(json)?.groupValues?.get(1).nullIfBlank()
        }
    }
}

/** Reads byte lines and decodes each one as UTF-8, falling back to another charset when invalid. */
private class ByteLineReader(
    private val input: InputStream,
    private val maxLineBytes: Int,
    private val fallback: Charset,
) {
    private val utf8 = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)

    fun forEachLine(action: (String) -> Unit) {
        val buf = ByteArray(64 * 1024)
        var line = ByteArray(1024)
        var len = 0
        var overflow = false
        var lastWasCr = false
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            for (i in 0 until n) {
                val b = buf[i]
                if (b == '\n'.code.toByte() || b == '\r'.code.toByte()) {
                    if (b == '\n'.code.toByte() && lastWasCr) {
                        lastWasCr = false
                        continue
                    }
                    lastWasCr = b == '\r'.code.toByte()
                    action(decode(line, len))
                    len = 0
                    overflow = false
                    continue
                }
                lastWasCr = false
                if (overflow) continue
                if (len == line.size) {
                    if (line.size >= maxLineBytes) {
                        overflow = true // absurdly long line (binary junk); drop the rest of it
                        continue
                    }
                    line = line.copyOf(minOf(line.size * 2, maxLineBytes))
                }
                line[len++] = b
            }
        }
        if (len > 0) action(decode(line, len))
    }

    private fun decode(bytes: ByteArray, len: Int): String {
        if (len == 0) return ""
        var ascii = true
        for (i in 0 until len) if (bytes[i] < 0) { ascii = false; break }
        if (ascii) return String(bytes, 0, len, Charsets.ISO_8859_1)
        return try {
            utf8.reset()
            utf8.decode(ByteBuffer.wrap(bytes, 0, len)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, 0, len, fallback)
        }
    }
}

private fun InputStream.readFully(target: ByteArray): Int {
    var total = 0
    while (total < target.size) {
        val n = read(target, total, target.size - total)
        if (n < 0) break
        total += n
    }
    return total
}
