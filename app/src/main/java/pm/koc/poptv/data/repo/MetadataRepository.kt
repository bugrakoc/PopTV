package pm.koc.poptv.data.repo

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pm.koc.poptv.BuildConfig
import pm.koc.poptv.core.TextNormalizer
import pm.koc.poptv.data.db.AppDatabase
import pm.koc.poptv.data.db.MetadataEntity
import pm.koc.poptv.data.net.AppError
import pm.koc.poptv.data.prefs.SettingsRepository
import pm.koc.poptv.data.tmdb.TmdbClient
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class Metadata(
    val title: String?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: Double?,
    val year: Int?,
    val genres: List<String>,
    val cast: List<String>,
    /** ISO 639-1 language the title was made in, e.g. "en", "ja", "tr". */
    val originalLanguage: String?,
)

/**
 * Optional TMDB enrichment. Active only when a key is set in Settings (or provided at build time via
 * `tmdb.apiKey` in local.properties). Results — including misses — are cached in Room.
 */
@Singleton
class MetadataRepository @Inject constructor(
    private val db: AppDatabase,
    private val tmdb: TmdbClient,
    private val settings: SettingsRepository,
) {
    val enabled: Flow<Boolean> = settings.hasTmdbKey.map { it || BuildConfig.TMDB_API_KEY.isNotBlank() }

    private suspend fun key(): String? = settings.tmdbKey().ifBlank { BuildConfig.TMDB_API_KEY }.takeIf { it.isNotBlank() }

    suspend fun isEnabled(): Boolean = key() != null

    private fun language(): String = if (Locale.getDefault().language == "tr") "tr-TR" else "en-US"

    suspend fun movie(title: String, year: Int?): Metadata? = lookup("movie", title, year)

    suspend fun show(name: String, year: Int?): Metadata? = lookup("tv", name, year)

    private suspend fun lookup(kind: String, title: String, year: Int?): Metadata? {
        val apiKey = key() ?: return null
        val lang = language()
        val cacheKey = "$kind|${TextNormalizer.searchKey(title)}|${year ?: ""}|$lang"
        val now = System.currentTimeMillis()
        db.metadataDao().get(cacheKey)?.let { cached ->
            val ttl = if (cached.tmdbId == null) MISS_TTL else HIT_TTL
            // Hits cached before the original language was stored are refetched once.
            val complete = cached.tmdbId == null || cached.originalLanguage != null
            if (now - cached.fetchedAt < ttl && complete) return cached.toMetadata()
        }
        return try {
            val entity = fetch(apiKey, kind, title, year, lang, cacheKey, now)
            db.metadataDao().upsert(entity)
            entity.toMetadata()
        } catch (e: CancellationException) {
            throw e // the player gives up waiting (timeout); not a failed lookup
        } catch (e: AppError.Unauthorized) {
            Log.w(TAG, "TMDB rejected the API key")
            null
        } catch (e: Exception) {
            Log.w(TAG, "TMDB lookup failed: ${e.javaClass.simpleName}")
            db.metadataDao().get(cacheKey)?.toMetadata()
        }
    }

    private suspend fun fetch(
        apiKey: String,
        kind: String,
        title: String,
        year: Int?,
        lang: String,
        cacheKey: String,
        now: Long,
    ): MetadataEntity {
        val miss = MetadataEntity(cacheKey, null, null, null, null, null, null, null, null, null, now)
        if (kind == "movie") {
            val hit = tmdb.searchMovie(apiKey, title, year, lang).results.firstOrNull()
                ?: if (year != null) tmdb.searchMovie(apiKey, title, null, lang).results.firstOrNull() else null
            hit ?: return miss
            val details = runCatching { tmdb.movieDetails(apiKey, hit.id, lang) }.getOrNull()
            var overview = hit.overview?.takeIf { it.isNotBlank() } ?: details?.overview
            if (overview.isNullOrBlank() && lang != "en-US") {
                overview = runCatching { tmdb.movieDetails(apiKey, hit.id, "en-US").overview }.getOrNull()
            }
            return MetadataEntity(
                cacheKey, hit.id, hit.title, overview, image(hit.posterPath, "w500"), image(hit.backdropPath, "w1280"),
                hit.voteAverage, hit.releaseDate?.take(4)?.toIntOrNull(),
                details?.genres?.joinToString("|") { it.name }, details?.credits?.cast?.take(8)?.joinToString("|") { it.name }, now,
                originalLanguage(hit.originalLanguage),
            )
        } else {
            val hit = tmdb.searchTv(apiKey, title, year, lang).results.firstOrNull()
                ?: if (year != null) tmdb.searchTv(apiKey, title, null, lang).results.firstOrNull() else null
            hit ?: return miss
            val details = runCatching { tmdb.tvDetails(apiKey, hit.id, lang) }.getOrNull()
            var overview = hit.overview?.takeIf { it.isNotBlank() } ?: details?.overview
            if (overview.isNullOrBlank() && lang != "en-US") {
                overview = runCatching { tmdb.tvDetails(apiKey, hit.id, "en-US").overview }.getOrNull()
            }
            return MetadataEntity(
                cacheKey, hit.id, hit.name, overview, image(hit.posterPath, "w500"), image(hit.backdropPath, "w1280"),
                hit.voteAverage, hit.firstAirDate?.take(4)?.toIntOrNull(),
                details?.genres?.joinToString("|") { it.name }, details?.credits?.cast?.take(8)?.joinToString("|") { it.name }, now,
                originalLanguage(hit.originalLanguage),
            )
        }
    }

    /** TMDB uses "cn" for Cantonese (tracks are tagged Chinese) and "xx" for no language. */
    private fun originalLanguage(code: String?): String? = when (val c = code?.trim()?.lowercase(Locale.ROOT)) {
        null, "", "xx" -> null
        "cn" -> "zh"
        else -> c
    }

    /**
     * Original language of a title whose TMDB id another source knows (the Xtream API), cached under
     * [cacheKey]. [tmdbId] is only called on a cache miss; misses are cached too.
     */
    suspend fun originalLanguageById(kind: String, cacheKey: String, tmdbId: suspend () -> Long?): String? {
        val apiKey = key() ?: return null
        val now = System.currentTimeMillis()
        db.metadataDao().get(cacheKey)?.let { cached ->
            val ttl = if (cached.originalLanguage == null) MISS_TTL else HIT_TTL
            if (now - cached.fetchedAt < ttl) return cached.originalLanguage
        }
        return try {
            val id = tmdbId()
            val details = id?.let { if (kind == "movie") tmdb.movieDetails(apiKey, it, "en-US") else tmdb.tvDetails(apiKey, it, "en-US") }
            val language = originalLanguage(details?.originalLanguage)
            db.metadataDao().upsert(MetadataEntity(cacheKey, id, null, null, null, null, null, null, null, null, now, language))
            language
        } catch (e: CancellationException) {
            throw e // the player gives up waiting (timeout); not a failed lookup
        } catch (e: AppError.Unauthorized) {
            Log.w(TAG, "TMDB rejected the API key")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Original language lookup failed: ${e.javaClass.simpleName}")
            null
        }
    }

    suspend fun clearCache() = db.metadataDao().clear()

    private fun image(path: String?, size: String): String? = path?.let { TmdbClient.IMAGE_BASE + size + it }

    private fun MetadataEntity.toMetadata(): Metadata? = if (tmdbId == null) null else Metadata(
        title = title,
        overview = overview,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        rating = rating?.takeIf { it > 0 },
        year = year,
        genres = genres?.split('|')?.filter { it.isNotBlank() }.orEmpty(),
        cast = cast?.split('|')?.filter { it.isNotBlank() }.orEmpty(),
        originalLanguage = originalLanguage,
    )

    private companion object {
        const val TAG = "MetadataRepository"
        const val HIT_TTL = 30L * 24 * 60 * 60 * 1000
        const val MISS_TTL = 3L * 24 * 60 * 60 * 1000
    }
}
