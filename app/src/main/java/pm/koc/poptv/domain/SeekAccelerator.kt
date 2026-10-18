package pm.koc.poptv.domain

/** Seek step for a held D-pad key: 10 s for a short press, growing while the key is held. */
object SeekAccelerator {
    fun stepMs(repeatCount: Int): Long = when {
        repeatCount < 6 -> 10_000L
        repeatCount < 16 -> 30_000L
        repeatCount < 30 -> 60_000L
        else -> 120_000L
    }

    /** New target position clamped to [0, duration] (duration <= 0 means unknown: no upper clamp). */
    fun target(current: Long, forward: Boolean, repeatCount: Int, durationMs: Long): Long {
        val step = stepMs(repeatCount)
        val t = if (forward) current + step else current - step
        val upper = if (durationMs > 0) durationMs else Long.MAX_VALUE
        return t.coerceIn(0L, upper)
    }
}
