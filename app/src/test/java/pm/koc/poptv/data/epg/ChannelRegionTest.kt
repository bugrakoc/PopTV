package pm.koc.poptv.data.epg

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChannelRegionTest {

    @Test
    fun `playlist channel regions come from the name prefix or suffix`() {
        assertThat(ChannelRegion.ofPlaylistChannel("TR: TRT 1", "Ulusal")).containsExactly("tr")
        assertThat(ChannelRegion.ofPlaylistChannel("|UK| Sky Sports", "Sports")).containsExactly("uk", "ie")
        assertThat(ChannelRegion.ofPlaylistChannel("Eurosport 1 DE", "Sports")).containsExactly("de", "at", "ch")
        // Lower-case words are not codes.
        assertThat(ChannelRegion.ofPlaylistChannel("Just Do It", "Sports")).isEmpty()
    }

    @Test
    fun `group title is the fallback`() {
        assertThat(ChannelRegion.ofPlaylistChannel("Eurosport 1", "DE | SPORT")).contains("de")
        assertThat(ChannelRegion.ofPlaylistChannel("Eurosport 1", "UK SPORTS")).contains("uk")
        assertThat(ChannelRegion.ofPlaylistChannel("Eurosport 1", "Türkiye Spor")).containsExactly("tr")
        assertThat(ChannelRegion.ofPlaylistChannel("Eurosport 1", "United Kingdom")).contains("uk")
        assertThat(ChannelRegion.ofPlaylistChannel("Eurosport 1", "VIP Sports")).isEmpty()
    }

    @Test
    fun `ids name a country by their suffix`() {
        assertThat(ChannelRegion.ofId("Eurosport1.de")).containsExactly("de")
        assertThat(ChannelRegion.ofId("BBCOne.gb")).containsExactly("uk")
        assertThat(ChannelRegion.ofId("France24.fr@English")).containsExactly("fr")
        assertThat(ChannelRegion.ofId("channel.tv")).isEmpty()
        assertThat(ChannelRegion.ofId("trt1")).isEmpty()
        assertThat(ChannelRegion.ofId(null)).isEmpty()
    }

    @Test
    fun `guide channels fall back to the display name`() {
        assertThat(ChannelRegion.ofGuideChannel("eurosport1", "DE: Eurosport 1")).contains("de")
        assertThat(ChannelRegion.ofGuideChannel("Eurosport1.uk", "DE: Eurosport 1")).containsExactly("uk")
        assertThat(ChannelRegion.ofGuideChannel("eurosport1", "Eurosport 1")).isEmpty()
    }
}
