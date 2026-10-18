package pm.koc.poptv.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SeasonGrouperTest {

    private fun ep(key: String, title: String, season: Int?, episode: Int?, index: Int) = EpisodeRef(key, title, season, episode, index)

    private val episodes = listOf(
        ep("s2e1", "Show S02E01", 2, 1, 0),
        ep("s1e10", "Show S01E10", 1, 10, 1),
        ep("s1e2", "Show S01E02", 1, 2, 2),
        ep("s1e1", "Show S01E01", 1, 1, 3),
        ep("extra", "Show Special", null, null, 4),
        ep("s1e2b", "Show S01E02 SD", 1, 2, 5),
    )

    @Test
    fun `groups by season and sorts naturally`() {
        val seasons = SeasonGrouper.group(episodes) { it }
        assertThat(seasons.map { it.number }).containsExactly(1, 2).inOrder()
        assertThat(seasons[0].episodes.map { it.key })
            .containsExactly("s1e1", "s1e2", "s1e2b", "s1e10", "extra").inOrder()
        assertThat(seasons[1].episodes.map { it.key }).containsExactly("s2e1")
    }

    @Test
    fun `next episode crosses seasons and ends`() {
        val seasons = SeasonGrouper.group(episodes) { it }
        assertThat(SeasonGrouper.next(seasons, "s1e1") { it.key }?.key).isEqualTo("s1e2")
        assertThat(SeasonGrouper.next(seasons, "extra") { it.key }?.key).isEqualTo("s2e1")
        assertThat(SeasonGrouper.next(seasons, "s2e1") { it.key }).isNull()
        assertThat(SeasonGrouper.next(seasons, "missing") { it.key }).isNull()
    }
}
