package pm.koc.poptv.data.epg

import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Parses XMLTV timestamps: `YYYYMMDDHHMMSS +ZZZZ` with optional seconds/minutes/hours, optional or
 * attached offset (`+0300`, `-05:30`, `Z`, `UTC`/`GMT`). Missing offset means UTC. Returns epoch millis
 * or null when the value is malformed.
 */
object XmltvTime {

    fun parse(raw: String?): Long? {
        val s = raw?.trim() ?: return null
        if (s.length < 8) return null
        var i = 0
        while (i < s.length && s[i].isDigit()) i++
        val digits = s.substring(0, i)
        if (digits.length < 8) return null
        val year = digits.substring(0, 4).toInt()
        val month = digits.substring(4, 6).toInt()
        val day = digits.substring(6, 8).toInt()
        val hour = digits.part(8)
        val minute = digits.part(10)
        val second = digits.part(12)
        if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59 || second !in 0..60) return null

        // Skip fractional seconds like ".000".
        if (i < s.length && s[i] == '.') {
            i++
            while (i < s.length && s[i].isDigit()) i++
        }
        val offsetSeconds = parseOffset(s.substring(i).trim()) ?: return null
        return try {
            val ldt = LocalDateTime.of(year, month, day, hour, minute, second.coerceAtMost(59))
            ldt.toEpochSecond(ZoneOffset.ofTotalSeconds(offsetSeconds)) * 1000
        } catch (_: Exception) {
            null
        }
    }

    private fun String.part(start: Int): Int =
        if (length >= start + 2) substring(start, start + 2).toInt() else 0

    /** Offset in seconds; null when present but malformed. */
    private fun parseOffset(raw: String): Int? {
        if (raw.isEmpty()) return 0
        val s = raw.uppercase()
        if (s == "Z" || s == "UTC" || s == "GMT") return 0
        val sign = when (s[0]) {
            '+' -> 1
            '-' -> -1
            else -> return null
        }
        val body = s.substring(1).replace(":", "")
        if (body.isEmpty() || body.length > 4 || !body.all { it.isDigit() }) return null
        val hours: Int
        val minutes: Int
        when (body.length) {
            1, 2 -> { hours = body.toInt(); minutes = 0 }
            3 -> { hours = body.substring(0, 1).toInt(); minutes = body.substring(1).toInt() }
            else -> { hours = body.substring(0, 2).toInt(); minutes = body.substring(2).toInt() }
        }
        if (hours > 14 || minutes > 59) return null
        return sign * (hours * 3600 + minutes * 60)
    }
}
