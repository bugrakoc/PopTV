package pm.koc.poptv.domain

/**
 * Limited, exponentially backed-off retries for stream errors and stalls. The attempt counter resets after
 * playback has been stable for [stableResetMs], so a stream that drops once an hour keeps reconnecting.
 */
class ReconnectPolicy(
    private val maxAttempts: Int = 6,
    private val baseDelayMs: Long = 1_000,
    private val maxDelayMs: Long = 16_000,
    private val stableResetMs: Long = 30_000,
) {
    var attempts: Int = 0
        private set
    private var playingSince: Long = -1

    val maxRetries: Int get() = maxAttempts

    /** Returns the delay before the next retry, or null when retries are exhausted. */
    fun nextDelay(): Long? {
        if (attempts >= maxAttempts) return null
        val delay = (baseDelayMs shl attempts.coerceAtMost(20)).coerceAtMost(maxDelayMs)
        attempts++
        playingSince = -1
        return delay
    }

    /** Call regularly while playing; resets the counter after stable playback. */
    fun onPlaying(now: Long) {
        if (playingSince < 0) playingSince = now
        if (attempts > 0 && now - playingSince >= stableResetMs) attempts = 0
    }

    fun onNotPlaying() {
        playingSince = -1
    }

    fun reset() {
        attempts = 0
        playingSince = -1
    }
}
