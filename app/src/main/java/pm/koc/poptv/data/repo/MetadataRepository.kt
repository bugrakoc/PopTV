package pm.koc.poptv.data.repo

import android.util.Log
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
            if (now - cached.fetchedAt < ttl) return cached.toMetadata()
        }
        return try {
            val entity = fetch(apiKey, kind, title, year, lang, cacheKey, now)
            db.metadataDao().upsert(entity)
            entity.toMetadata()
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
            )
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
    )

    private companion object {
        const val TAG = "MetadataRepository"
        const val HIT_TTL = 30L * 24 * 60 * 60 * 1000
        const val MISS_TTL = 3L * 24 * 60 * 60 * 1000
    }
}
