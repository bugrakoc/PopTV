package pm.koc.poptv.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContinueWatchingTest {
    private val hour = 3_600_000L

    private fun pos(key: String, series: String?, position: Long, at: Long, watched: Boolean = false, duration: Long = hour) =
        SavedPosition(key, series, position, duration, watched, at)

    private val next = mapOf("ep1" to "ep2", "ep2" to "ep3", "ep3" to null)

    @Test
    fun `movies in progress are listed most recent first, finished ones dropped`() {
        val items = ContinueWatching.build(
            listOf(
                pos("m1", null, 600_000, at = 1),
                pos("m2", null, 1_200_000, at = 3),
                pos("m3", null, hour, at = 2, watched = true),
                pos("m4", null, 10_000, at = 4), // too short to resume
            ),
        ) { _, k -> next[k] }
        assertThat(items.map { it.streamKey }).containsExactly("m2", "m1").inOrder()
    }

    @Test
    fun `one entry per show, unfinished episode resumed`() {
        val items = ContinueWatching.build(
            listOf(
                pos("ep1", "show", hour, at = 1, watched = true),
                pos("ep2", "show", 300_000, at = 2),
            ),
        ) { _, k -> next[k] }
        assertThat(items).hasSize(1)
        assertThat(items[0].streamKey).isEqualTo("ep2")
        assertThat(items[0].isNextEpisode).isFalse()
    }

    @Test
    fun `finished episode is replaced by the next one`() {
        val items = ContinueWatching.build(listOf(pos("ep2", "show", hour, at = 5, watched = true))) { _, k -> next[k] }
        assertThat(items.single().streamKey).isEqualTo("ep3")
        assertThat(items.single().isNextEpisode).isTrue()
        assertThat(items.single().positionMs).isEqualTo(0)
    }

    @Test
    fun `finished last episode disappears`() {
        val items = ContinueWatching.build(listOf(pos("ep3", "show", hour, at = 5, watched = true))) { _, k -> next[k] }
        assertThat(items).isEmpty()
    }

    @Test
    fun `respects limit`() {
        val many = (1..30).map { pos("m$it", null, 600_000, at = it.toLong()) }
        assertThat(ContinueWatching.build(many, limit = 5) { _, _ -> null }).hasSize(5)
    }
}
