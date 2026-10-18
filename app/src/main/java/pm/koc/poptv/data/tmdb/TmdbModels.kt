package pm.koc.poptv.data.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbMovieSearch(val results: List<TmdbMovieResult> = emptyList())

@Serializable
data class TmdbTvSearch(val results: List<TmdbTvResult> = emptyList())

@Serializable
data class TmdbMovieResult(
    val id: Long,
    val title: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("release_date") val releaseDate: String? = null,
)

@Serializable
data class TmdbTvResult(
    val id: Long,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
)

@Serializable
data class TmdbDetails(
    val overview: String? = null,
    val genres: List<TmdbGenre> = emptyList(),
    val credits: TmdbCredits? = null,
)

@Serializable
data class TmdbGenre(val name: String = "")

@Serializable
data class TmdbCredits(val cast: List<TmdbCast> = emptyList())

@Serializable
data class TmdbCast(val name: String = "")
