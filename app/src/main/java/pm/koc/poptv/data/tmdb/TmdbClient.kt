package pm.koc.poptv.data.tmdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import pm.koc.poptv.data.net.AppError
import javax.inject.Inject
import javax.inject.Singleton

/** Minimal TMDB v3 client. Accepts either a v3 API key or a v4 read access token (Bearer). */
@Singleton
class TmdbClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {
    suspend fun searchMovie(key: String, query: String, year: Int?, language: String): TmdbMovieSearch =
        get(key, "search/movie", TmdbMovieSearch.serializer()) {
            addQueryParameter("query", query)
            addQueryParameter("language", language)
            addQueryParameter("include_adult", "false")
            if (year != null) addQueryParameter("year", year.toString())
        }

    suspend fun searchTv(key: String, query: String, year: Int?, language: String): TmdbTvSearch =
        get(key, "search/tv", TmdbTvSearch.serializer()) {
            addQueryParameter("query", query)
            addQueryParameter("language", language)
            addQueryParameter("include_adult", "false")
            if (year != null) addQueryParameter("first_air_date_year", year.toString())
        }

    suspend fun movieDetails(key: String, id: Long, language: String): TmdbDetails =
        get(key, "movie/$id", TmdbDetails.serializer()) {
            addQueryParameter("language", language)
            addQueryParameter("append_to_response", "credits")
        }

    suspend fun tvDetails(key: String, id: Long, language: String): TmdbDetails =
        get(key, "tv/$id", TmdbDetails.serializer()) {
            addQueryParameter("language", language)
            addQueryParameter("append_to_response", "credits")
        }

    private suspend fun <T> get(
        key: String,
        path: String,
        deserializer: DeserializationStrategy<T>,
        params: HttpUrl.Builder.() -> Unit,
    ): T = withContext(Dispatchers.IO) {
        val bearer = key.startsWith("eyJ")
        val url = BASE.toHttpUrl().newBuilder()
            .addPathSegments(path)
            .apply(params)
            .apply { if (!bearer) addQueryParameter("api_key", key) }
            .build()
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "PopTV")
            .apply { if (bearer) header("Authorization", "Bearer $key") }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw AppError.fromHttpCode(response.code)
            json.decodeFromString(deserializer, response.body.string())
        }
    }

    companion object {
        private const val BASE = "https://api.themoviedb.org/3/"
        const val IMAGE_BASE = "https://image.tmdb.org/t/p/"
    }
}
