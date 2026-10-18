package pm.koc.poptv.ui.guide

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class GuideTimeTest {
    private fun t(s: String) = Instant.parse(s).toEpochMilli()

    @Test
    fun `window starts at the previous half hour`() {
        assertThat(GuideTime.initialWindowStart(t("2024-01-15T17:44:10Z"))).isEqualTo(t("2024-01-15T17:30:00Z"))
        assertThat(GuideTime.initialWindowStart(t("2024-01-15T17:00:00Z"))).isEqualTo(t("2024-01-15T17:00:00Z"))
    }

    @Test
    fun `window is clamped to the stored range`() {
        val now = t("2024-01-15T12:00:00Z")
        assertThat(GuideTime.clampWindow(t("2024-01-14T00:00:00Z"), now)).isEqualTo(t("2024-01-15T06:00:00Z"))
        assertThat(GuideTime.clampWindow(t("2024-01-30T00:00:00Z"), now)).isEqualTo(t("2024-01-18T09:30:00Z"))
        assertThat(GuideTime.clampWindow(t("2024-01-15T14:00:00Z"), now)).isEqualTo(t("2024-01-15T14:00:00Z"))
    }

    @Test
    fun `blocks cover the range`() {
        val blocks = GuideTime.blocks(t("2024-01-15T05:00:00Z"), t("2024-01-15T13:00:00Z"))
        assertThat(blocks).containsExactly(t("2024-01-15T00:00:00Z"), t("2024-01-15T06:00:00Z"), t("2024-01-15T12:00:00Z")).inOrder()
    }

    @Test
    fun `clip returns visible fractions`() {
        val ws = t("2024-01-15T12:00:00Z")
        val we = ws + GuideTime.SPAN_MS // 2.5 h
        assertThat(GuideTime.clip(t("2024-01-15T11:00:00Z"), t("2024-01-15T12:30:00Z"), ws, we)).isEqualTo(0f to 0.2f)
        assertThat(GuideTime.clip(t("2024-01-15T14:00:00Z"), t("2024-01-15T16:00:00Z"), ws, we)).isEqualTo(0.8f to 1f)
        assertThat(GuideTime.clip(t("2024-01-15T10:00:00Z"), t("2024-01-15T11:00:00Z"), ws, we)).isNull()
    }
}
