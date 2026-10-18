package pm.koc.poptv.domain

/** Rules for resume positions and "watched" state of movies and episodes. */
object ResumePolicy {
    /** Content counts as watched from this fraction on. */
    const val WATCHED_FRACTION = 0.93
    /** Positions before this are not worth resuming. */
    const val MIN_RESUME_MS = 30_000L
    /** Resume slightly before the saved position so the viewer regains context. */
    const val REWIND_ON_RESUME_MS = 5_000L
    /** How often to save while playing. */
    const val SAVE_INTERVAL_MS = 10_000L
    /** Show the "up next" prompt this long before the end of an episode. */
    const val UP_NEXT_BEFORE_END_MS = 25_000L
    const val UP_NEXT_COUNTDOWN_SECONDS = 10

    fun isWatched(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= durationMs * WATCHED_FRACTION

    /** Whether a position is meaningful enough to store (unknown durations are stored too). */
    fun shouldStore(positionMs: Long, durationMs: Long): Boolean =
        positionMs >= MIN_RESUME_MS || isWatched(positionMs, durationMs)

    /** Where to start playback for a saved state; 0 = from the beginning. */
    fun startPosition(savedPositionMs: Long?, savedDurationMs: Long?, watched: Boolean): Long {
        if (savedPositionMs == null || watched) return 0
        if (savedPositionMs < MIN_RESUME_MS) return 0
        if (savedDurationMs != null && isWatched(savedPositionMs, savedDurationMs)) return 0
        return (savedPositionMs - REWIND_ON_RESUME_MS).coerceAtLeast(0)
    }

    fun progress(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    /** True when the "up next" prompt should appear. */
    fun shouldShowUpNext(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 60_000 && durationMs - positionMs <= UP_NEXT_BEFORE_END_MS
}
