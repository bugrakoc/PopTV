package pm.koc.poptv.domain

/** A saved position as seen by the continue-watching logic. */
data class SavedPosition(
    val streamKey: String,
    val seriesKey: String?,
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean,
    val updatedAt: Long,
)

/** One continue-watching row item: either resume [streamKey], or start the next episode (progress 0). */
data class ContinueItem(
    val streamKey: String,
    val seriesKey: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val isNextEpisode: Boolean,
)

object ContinueWatching {

    /**
     * Builds the row from positions sorted by most recent first: unfinished movies; for shows only the most
     * recent episode per show — resumed if unfinished, otherwise replaced by the next episode (if any).
     * Finished movies are dropped.
     *
     * @param nextEpisode returns the key of the episode after the given one in the same show, or null.
     */
    fun build(
        positions: List<SavedPosition>,
        limit: Int = 20,
        nextEpisode: (seriesKey: String, streamKey: String) -> String?,
    ): List<ContinueItem> {
        val out = ArrayList<ContinueItem>()
        val seenShows = HashSet<String>()
        for (p in positions.sortedByDescending { it.updatedAt }) {
            if (out.size >= limit) break
            val show = p.seriesKey
            if (show != null) {
                if (!seenShows.add(show)) continue
                val finished = p.watched || ResumePolicy.isWatched(p.positionMs, p.durationMs)
                if (!finished) {
                    out += ContinueItem(p.streamKey, show, p.positionMs, p.durationMs, p.updatedAt, false)
                } else {
                    val next = nextEpisode(show, p.streamKey) ?: continue
                    out += ContinueItem(next, show, 0, 0, p.updatedAt, true)
                }
            } else {
                val finished = p.watched || ResumePolicy.isWatched(p.positionMs, p.durationMs)
                if (finished || p.positionMs < ResumePolicy.MIN_RESUME_MS) continue
                out += ContinueItem(p.streamKey, null, p.positionMs, p.durationMs, p.updatedAt, false)
            }
        }
        return out
    }
}
