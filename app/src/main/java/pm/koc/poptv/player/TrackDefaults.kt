package pm.koc.poptv.player

import pm.koc.poptv.data.prefs.AppSettings
import java.util.Locale

/** Default audio and subtitle choice for a new stream, from the user's settings. */
object TrackDefaults {

    /** An audio track as the picker sees it; [language] is a normalized ISO 639-1 code when known. */
    data class AudioTrack(val language: String?, val label: String?, val supported: Boolean = true)

    /** A subtitle track; [hearingImpaired] is the container's flag (labels like "SDH" are checked too). */
    data class TextTrack(
        val language: String?,
        val label: String?,
        val forced: Boolean = false,
        val hearingImpaired: Boolean = false,
        val supported: Boolean = true,
    )

    data class Pick(val index: Int, val reason: String)

    private val originalLabel = Regex("""\b(original|orijinal|orig)\b""", RegexOption.IGNORE_CASE)
    private val hearingImpairedLabel = Regex("""\b(SDH|HI|CC|hearing|işitme)\b""", RegexOption.IGNORE_CASE)
    private val dubLabel = Regex("""\b(dub|dubbed|dublaj|dublajl[ıi])\b""", RegexOption.IGNORE_CASE)

    /**
     * Audio language the player starts with (null: the player's own choice). Live TV keeps the device
     * language; movies and episodes follow the settings, with [originalLanguage] (TMDB) for "original".
     */
    fun startAudioLanguage(settings: AppSettings, isLive: Boolean, deviceLanguage: String, originalLanguage: String? = null): String? {
        if (isLive) return deviceLanguage
        val audio = if (settings.audioLanguage == AppSettings.ORIGINAL) originalLanguage ?: settings.fallbackAudioLanguage
        else settings.audioLanguage
        return audio.ifEmpty { null }
    }

    /**
     * The audio track to play once a movie's or episode's tracks are known, or null to keep the player's
     * choice. "Original" is resolved in order: [originalLanguage] from TMDB, a track labelled original,
     * the only language besides a dub in the device language ([localLanguage]: "tr" + "ja" means the
     * Japanese track is the original), then [AppSettings.fallbackAudioLanguage].
     */
    fun pickAudio(tracks: List<AudioTrack>, settings: AppSettings, originalLanguage: String?, localLanguage: String): Pick? {
        val usable = tracks.withIndex().filter { it.value.supported }
        if (usable.size <= 1) return usable.firstOrNull()?.let { Pick(it.index, "only track") }
        fun inLanguage(language: String?) = language?.let { l -> usable.firstOrNull { primary(it.value.language) == l } }

        if (settings.audioLanguage != AppSettings.ORIGINAL) {
            return inLanguage(settings.audioLanguage)?.let { Pick(it.index, "preferred language") }
        }
        inLanguage(originalLanguage)?.let { return Pick(it.index, "TMDB original language") }
        usable.firstOrNull { it.value.label?.let(originalLabel::containsMatchIn) == true }
            ?.let { return Pick(it.index, "labelled original") }
        // Dubbing into English is rare, so on an English device the other track is not likely the original.
        if (localLanguage != "en" && usable.any { primary(it.value.language) == localLanguage }) {
            val others = usable.filter {
                val language = primary(it.value.language)
                language != null && language != localLanguage && it.value.label?.let(dubLabel::containsMatchIn) != true
            }
            if (others.map { primary(it.value.language) }.distinct().size == 1) {
                return Pick(others.first().index, "only language besides the $localLanguage dub")
            }
        }
        return inLanguage(settings.fallbackAudioLanguage)?.let { Pick(it.index, "fallback language") }
    }

    /**
     * Subtitle languages to try, best first, for audio in [audioLanguage]; empty means subtitles off.
     * The setting for that audio language comes first, then [AppSettings.subtitlesForOther], then English.
     */
    fun subtitleLanguages(audioLanguage: String?, settings: AppSettings): List<String> {
        val choice = when (primary(audioLanguage)) {
            "en" -> settings.subtitlesForEnglish
            "tr" -> settings.subtitlesForTurkish
            else -> settings.subtitlesForOther
        }
        if (choice.isEmpty()) return emptyList()
        return listOf(choice, settings.subtitlesForOther, "en").filter { it.isNotEmpty() }.distinct()
    }

    /**
     * The subtitle track in the first of [languages] that has one, or null for none. Full subtitles win over
     * SDH (hearing-impaired) ones; forced tracks only cover foreign-language parts and are never picked.
     */
    fun pickSubtitles(tracks: List<TextTrack>, languages: List<String>): Pick? {
        for (language in languages) {
            val candidates = tracks.withIndex().filter {
                it.value.supported && !it.value.forced && primary(it.value.language) == language
            }
            val best = candidates.firstOrNull { !isHearingImpaired(it.value) } ?: candidates.firstOrNull() ?: continue
            return Pick(best.index, "$language subtitles")
        }
        return null
    }

    private fun isHearingImpaired(track: TextTrack) =
        track.hearingImpaired || track.label?.let(hearingImpairedLabel::containsMatchIn) == true

    private fun primary(language: String?): String? =
        language?.takeIf { it.isNotBlank() }?.substringBefore('-')?.lowercase(Locale.ROOT)
}
