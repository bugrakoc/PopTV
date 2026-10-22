package pm.koc.poptv.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import pm.koc.poptv.data.prefs.AppSettings

class TrackDefaultsTest {

    private val defaults = AppSettings()

    @Test
    fun `movies start with the original language, else English`() {
        assertThat(TrackDefaults.startAudioLanguage(defaults, isLive = false, deviceLanguage = "tr", originalLanguage = "ja")).isEqualTo("ja")
        assertThat(TrackDefaults.startAudioLanguage(defaults, isLive = false, deviceLanguage = "tr")).isEqualTo("en")
        assertThat(TrackDefaults.startAudioLanguage(defaults.copy(audioLanguage = "tr"), isLive = false, deviceLanguage = "en"))
            .isEqualTo("tr")
    }

    @Test
    fun `live TV keeps the device language`() {
        assertThat(TrackDefaults.startAudioLanguage(defaults, isLive = true, deviceLanguage = "tr")).isEqualTo("tr")
    }

    @Test
    fun `subtitle languages follow the audio language`() {
        // English audio: English, then Turkish; Turkish audio: off; anything else: Turkish, then English.
        assertThat(TrackDefaults.subtitleLanguages("en", defaults)).containsExactly("en", "tr").inOrder()
        assertThat(TrackDefaults.subtitleLanguages("tr", defaults)).isEmpty()
        assertThat(TrackDefaults.subtitleLanguages("zh", defaults)).containsExactly("tr", "en").inOrder()
        assertThat(TrackDefaults.subtitleLanguages(null, defaults)).containsExactly("tr", "en").inOrder()
        assertThat(TrackDefaults.subtitleLanguages("en", defaults.copy(subtitlesForEnglish = ""))).isEmpty()
    }

    private fun sub(language: String?, label: String? = null, forced: Boolean = false, hearingImpaired: Boolean = false) =
        TrackDefaults.TextTrack(language, label, forced, hearingImpaired)

    @Test
    fun `full subtitles win over SDH and forced ones`() {
        val tracks = listOf(sub("en", "English [SDH]"), sub("en", "English"), sub("tr", "Turkish"))
        assertThat(TrackDefaults.pickSubtitles(tracks, listOf("en", "tr"))?.index).isEqualTo(1)
        assertThat(TrackDefaults.pickSubtitles(listOf(sub("en", hearingImpaired = true), sub("en")), listOf("en"))?.index).isEqualTo(1)
        assertThat(TrackDefaults.pickSubtitles(listOf(sub("en", "Forced", forced = true), sub("tr")), listOf("en", "tr"))?.index)
            .isEqualTo(1)
        // Only SDH available: still better than none.
        assertThat(TrackDefaults.pickSubtitles(listOf(sub("en", "SDH")), listOf("en"))?.index).isEqualTo(0)
    }

    @Test
    fun `subtitles fall back through the language list`() {
        val tracks = listOf(sub("de"), sub("en"))
        assertThat(TrackDefaults.pickSubtitles(tracks, listOf("tr", "en"))?.index).isEqualTo(1)
        assertThat(TrackDefaults.pickSubtitles(tracks, listOf("tr"))).isNull()
        assertThat(TrackDefaults.pickSubtitles(tracks, emptyList())).isNull()
    }

    private fun track(language: String?, label: String? = null, supported: Boolean = true) =
        TrackDefaults.AudioTrack(language, label, supported)

    private fun pick(vararg tracks: TrackDefaults.AudioTrack, original: String? = null, local: String = "tr", settings: AppSettings = defaults) =
        TrackDefaults.pickAudio(tracks.toList(), settings, original, local)?.index

    @Test
    fun `a single audio track is always played`() {
        assertThat(pick(track("tr"))).isEqualTo(0)
        assertThat(pick(track("tr"), track("en", supported = false))).isEqualTo(0)
        assertThat(pick()).isNull()
    }

    @Test
    fun `TMDB original language wins`() {
        assertThat(pick(track("tr"), track("en"), track("ja"), original = "ja")).isEqualTo(2)
        // A Turkish film keeps its Turkish track even when an English dub exists.
        assertThat(pick(track("en"), track("tr"), original = "tr")).isEqualTo(1)
    }

    @Test
    fun `a track labelled original comes next`() {
        assertThat(pick(track("tr"), track("en"), track(null, "Orijinal"))).isEqualTo(2)
        assertThat(pick(track("tr"), track("fr", "French (Original)"), track("en"))).isEqualTo(1)
    }

    @Test
    fun `the only language besides the local dub is the original`() {
        assertThat(pick(track("tr", "Dublaj"), track("zh"))).isEqualTo(1)
        assertThat(pick(track("tr"), track("ko"), track("ko", "Commentary"))).isEqualTo(1)
        // Two other languages: unknown which is original, so English (the fallback).
        assertThat(pick(track("tr"), track("fr"), track("en"))).isEqualTo(2)
        // A labelled dub in another language does not count.
        assertThat(pick(track("tr"), track("de", "German dub"), track("ja"))).isEqualTo(2)
    }

    @Test
    fun `no local dub or an English device falls back to English`() {
        assertThat(pick(track("fr"), track("en"))).isEqualTo(1)
        assertThat(pick(track("en"), track("tr"), local = "en")).isEqualTo(0)
        // Nothing usable: the player decides.
        assertThat(pick(track("fr"), track("de"))).isNull()
    }

    @Test
    fun `a fixed audio language ignores the original`() {
        val turkish = defaults.copy(audioLanguage = "tr")
        assertThat(pick(track("en"), track("tr"), original = "en", settings = turkish)).isEqualTo(1)
        assertThat(pick(track("en"), track("ja"), original = "en", settings = turkish)).isNull()
    }
}
