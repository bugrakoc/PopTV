package pm.koc.poptv.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ResumePolicyTest {
    private val hour = 3_600_000L

    @Test
    fun `watched near the end`() {
        assertThat(ResumePolicy.isWatched((hour * 0.95).toLong(), hour)).isTrue()
        assertThat(ResumePolicy.isWatched((hour * 0.90).toLong(), hour)).isFalse()
        assertThat(ResumePolicy.isWatched(10_000, 0)).isFalse()
    }

    @Test
    fun `start position rules`() {
        assertThat(ResumePolicy.startPosition(null, null, false)).isEqualTo(0)
        assertThat(ResumePolicy.startPosition(20_000, hour, false)).isEqualTo(0) // too early to resume
        assertThat(ResumePolicy.startPosition(600_000, hour, false)).isEqualTo(595_000) // small rewind
        assertThat(ResumePolicy.startPosition(600_000, hour, true)).isEqualTo(0) // watched -> start over
        assertThat(ResumePolicy.startPosition((hour * 0.97).toLong(), hour, false)).isEqualTo(0)
        assertThat(ResumePolicy.startPosition(600_000, 0, false)).isEqualTo(595_000) // unknown duration
    }

    @Test
    fun `store only meaningful positions`() {
        assertThat(ResumePolicy.shouldStore(5_000, hour)).isFalse()
        assertThat(ResumePolicy.shouldStore(45_000, hour)).isTrue()
        assertThat(ResumePolicy.shouldStore(hour, hour)).isTrue()
    }

    @Test
    fun `up next shows near the end of long content only`() {
        assertThat(ResumePolicy.shouldShowUpNext(hour - 20_000, hour)).isTrue()
        assertThat(ResumePolicy.shouldShowUpNext(hour - 60_000, hour)).isFalse()
        assertThat(ResumePolicy.shouldShowUpNext(40_000, 50_000)).isFalse()
    }

    @Test
    fun `progress fraction`() {
        assertThat(ResumePolicy.progress(30, 120)).isEqualTo(0.25f)
        assertThat(ResumePolicy.progress(10, 0)).isEqualTo(0f)
        assertThat(ResumePolicy.progress(200, 100)).isEqualTo(1f)
    }
}
