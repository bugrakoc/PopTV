package pm.koc.poptv.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NaturalOrderTest {
    @Test
    fun `numbers compare numerically`() {
        val sorted = listOf("Episode 10", "Episode 2", "episode 1", "Episode 02b", "Bölüm 3", "bolum 20")
            .sortedWith(NaturalOrder)
        assertThat(sorted).containsExactly("Bölüm 3", "bolum 20", "episode 1", "Episode 2", "Episode 02b", "Episode 10").inOrder()
    }

    @Test
    fun `equal strings compare equal`() {
        assertThat(NaturalOrder.compare("İzmir 1", "izmir 01")).isEqualTo(0)
    }
}
