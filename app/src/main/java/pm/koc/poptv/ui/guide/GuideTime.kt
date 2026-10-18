package pm.koc.poptv.ui.guide

import pm.koc.poptv.data.epg.EpgSources

/** Pure time arithmetic for the guide grid (unit-tested). */
object GuideTime {
    const val SLOT_MS = 30 * 60 * 1000L
    const val SPAN_MS = 2 * 60 * 60 * 1000L + SLOT_MS // 2.5 h visible
    const val BLOCK_MS = 6 * 60 * 60 * 1000L

    fun floorToSlot(t: Long): Long = t - Math.floorMod(t, SLOT_MS)

    /** Window starts at the half hour before now. */
    fun initialWindowStart(now: Long): Long = floorToSlot(now)

    /** Keeps the window inside the stored guide range (6 h back, 3 days ahead). */
    fun clampWindow(start: Long, now: Long): Long {
        val min = floorToSlot(now - EpgSources.WINDOW_BACK_MS)
        val max = floorToSlot(now + EpgSources.WINDOW_AHEAD_MS - SPAN_MS)
        return start.coerceIn(min, max)
    }

    /** Cache block starts covering [from, to). */
    fun blocks(from: Long, to: Long): List<Long> {
        val first = from - Math.floorMod(from, BLOCK_MS)
        val out = ArrayList<Long>()
        var b = first
        while (b < to) {
            out += b
            b += BLOCK_MS
        }
        return out
    }

    /** Visible part of a programme inside the window, as fractions [0..1] of the window width. */
    fun clip(start: Long, end: Long, windowStart: Long, windowEnd: Long): Pair<Float, Float>? {
        val s = maxOf(start, windowStart)
        val e = minOf(end, windowEnd)
        if (e <= s) return null
        val span = (windowEnd - windowStart).toFloat()
        return (s - windowStart) / span to (e - windowStart) / span
    }
}
