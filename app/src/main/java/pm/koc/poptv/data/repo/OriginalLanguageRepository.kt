package pm.koc.poptv.data.repo

import pm.koc.poptv.data.db.AppDatabase
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.prefs.SettingsRepository
import pm.koc.poptv.data.xtream.XtreamAccount
import pm.koc.poptv.data.xtream.XtreamClient
import pm.koc.poptv.domain.ContentType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The language a movie or show was made in, from TMDB (`original_language`). Needs a TMDB key.
 * The TMDB id the Xtream API gives (`get_vod_info` / `get_series_info`) is tried first, since it does not
 * depend on matching the title; then the same title search the detail screens use (and cache).
 */
@Singleton
class OriginalLanguageRepository @Inject constructor(
    private val db: AppDatabase,
    private val metadata: MetadataRepository,
    private val xtream: XtreamClient,
    private val settings: SettingsRepository,
) {
    /** ISO 639-1 code, or null when unknown or TMDB is off. Live channels have none. */
    suspend fun of(stream: StreamEntity): String? {
        if (stream.type == ContentType.LIVE || !metadata.isEnabled()) return null
        val account = settings.playlistUrl()?.let(XtreamAccount::fromPlaylistUrl)
        return if (stream.type == ContentType.MOVIE) {
            account?.movieId(stream.url)?.let { vodId ->
                metadata.originalLanguageById("movie", "xtream|movie|$vodId") { xtream.vodTmdbId(account, vodId) }
            } ?: metadata.movie(stream.displayName, stream.year)?.originalLanguage
        } else {
            val seriesKey = stream.seriesKey ?: return null
            val show = db.showDao().get(seriesKey)
            val seriesId = db.xtreamSeriesDao().forKey(seriesKey).firstOrNull()?.seriesId
            (if (account != null && seriesId != null) {
                metadata.originalLanguageById("tv", "xtream|series|$seriesId") { xtream.seriesTmdbId(account, seriesId) }
            } else {
                null
            }) ?: (show?.name ?: stream.seriesName)?.let { metadata.show(it, show?.year)?.originalLanguage }
        }
    }
}
