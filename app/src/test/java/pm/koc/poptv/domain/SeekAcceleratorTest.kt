package pm.koc.poptv.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SeekAcceleratorTest {
    @Test
    fun `short press seeks 10 seconds and holding accelerates`() {
        assertThat(SeekAccelerator.stepMs(0)).isEqualTo(10_000)
        assertThat(SeekAccelerator.stepMs(5)).isEqualTo(10_000)
        assertThat(SeekAccelerator.stepMs(6)).isEqualTo(30_000)
        assertThat(SeekAccelerator.stepMs(20)).isEqualTo(60_000)
        assertThat(SeekAccelerator.stepMs(100)).isEqualTo(120_000)
    }

    @Test
    fun `targets are clamped`() {
        assertThat(SeekAccelerator.target(5_000, forward = false, repeatCount = 0, durationMs = 60_000)).isEqualTo(0)
        assertThat(SeekAccelerator.target(55_000, forward = true, repeatCount = 0, durationMs = 60_000)).isEqualTo(60_000)
        assertThat(SeekAccelerator.target(55_000, forward = true, repeatCount = 0, durationMs = 0)).isEqualTo(65_000)
    }
}
