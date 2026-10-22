package pm.koc.poptv.data.prefs

enum class DecoderMode { HARDWARE, SOFTWARE, AUTO }
enum class BufferMode { SMALL, NORMAL, LARGE }
enum class SubtitleSize { SMALL, NORMAL, LARGE, XLARGE }
enum class SubtitleColor { WHITE, YELLOW }
enum class EpgStatus { UNKNOWN, OK, UNAVAILABLE }

data class AppSettings(
    val userAgent: String = DEFAULT_USER_AGENT,
    /** 0 = automatic refresh disabled. */
    val playlistRefreshHours: Int = 24,
    val epgRefreshHours: Int = 12,
    val epgOffsetMinutes: Int = 0,
    val decoderMode: DecoderMode = DecoderMode.HARDWARE,
    val tunneling: Boolean = false,
    val bufferMode: BufferMode = BufferMode.NORMAL,
    val subtitleSize: SubtitleSize = SubtitleSize.NORMAL,
    val subtitleColor: SubtitleColor = SubtitleColor.WHITE,
    val subtitleBackground: Boolean = true,
    /**
     * Preferred audio language (ISO 639-1) for movies and episodes, or [ORIGINAL]; live TV uses the
     * device language.
     */
    val audioLanguage: String = ORIGINAL,
    /** Audio language when [audioLanguage] is [ORIGINAL] and the original cannot be determined. */
    val fallbackAudioLanguage: String = "en",
    /**
     * Subtitle language for movies and episodes, by the language of the audio being played; empty = off.
     * When that language is missing, [subtitlesForOther] and then English are tried.
     */
    val subtitlesForEnglish: String = "en",
    val subtitlesForTurkish: String = "",
    val subtitlesForOther: String = "tr",
    val autoplayNext: Boolean = true,
    val showOriginalNames: Boolean = false,
    /** false: D-pad down = next channel (list order); true: D-pad up = next channel. */
    val invertChannelKeys: Boolean = false,
    /** Hides adult categories ([pm.koc.poptv.domain.AdultContent]) everywhere. */
    val hideAdult: Boolean = true,
) {
    companion object {
        const val DEFAULT_USER_AGENT = "VLC/3.0.21 LibVLC/3.0.21"
        val PLAYLIST_REFRESH_CHOICES = listOf(0, 6, 12, 24, 48, 168)
        val EPG_REFRESH_CHOICES = listOf(0, 6, 12, 24, 48)
        val EPG_OFFSET_CHOICES = (-12..12).map { it * 60 } + listOf(-30, 30)
        /** [audioLanguage] value: the language the title was made in ([pm.koc.poptv.player.TrackDefaults]). */
        const val ORIGINAL = "original"
        val TRACK_LANGUAGES = listOf("en", "tr", "de", "fr", "es", "it", "ar", "ru")
    }
}

/** Non-setting state persisted between launches. */
data class AppState(
    val lastPlaylistRefresh: Long = 0,
    val lastEpgRefresh: Long = 0,
    val epgStatus: EpgStatus = EpgStatus.UNKNOWN,
    val epgNoticeShown: Boolean = false,
    val playlistEntryCount: Int = 0,
    /** The adult filter the library rows currently reflect (libraries from before the setting show all). */
    val adultFilterApplied: Boolean = false,
)
