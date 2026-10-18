package pm.koc.poptv.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReconnectPolicyTest {

    @Test
    fun `backs off exponentially up to a cap and gives up`() {
        val p = ReconnectPolicy(maxAttempts = 6, baseDelayMs = 1000, maxDelayMs = 8000)
        val delays = generateSequence { p.nextDelay() }.toList()
        assertThat(delays).containsExactly(1000L, 2000L, 4000L, 8000L, 8000L, 8000L).inOrder()
        assertThat(p.nextDelay()).isNull()
    }

    @Test
    fun `stable playback resets the counter`() {
        val p = ReconnectPolicy(maxAttempts = 3, stableResetMs = 30_000)
        p.nextDelay(); p.nextDelay()
        assertThat(p.attempts).isEqualTo(2)
        p.onPlaying(1_000)
        p.onPlaying(20_000)
        assertThat(p.attempts).isEqualTo(2)
        p.onPlaying(31_000)
        assertThat(p.attempts).isEqualTo(0)
    }

    @Test
    fun `interrupted playback does not reset`() {
        val p = ReconnectPolicy(maxAttempts = 3, stableResetMs = 30_000)
        p.nextDelay()
        p.onPlaying(0)
        p.onNotPlaying()
        p.onPlaying(25_000)
        p.onPlaying(40_000)
        assertThat(p.attempts).isEqualTo(1)
        p.onPlaying(56_000)
        assertThat(p.attempts).isEqualTo(0)
    }
}
