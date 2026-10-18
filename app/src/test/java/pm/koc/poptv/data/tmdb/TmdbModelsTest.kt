package pm.koc.poptv.data.tmdb

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class TmdbModelsTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }

    @Test
    fun `parses movie search with missing fields`() {
        val body = """
            {"page":1,"results":[{"id":603,"title":"Matrix","overview":"Bir hacker…","poster_path":"/p.jpg",
            "backdrop_path":null,"vote_average":8.2,"release_date":"1999-03-31","genre_ids":[28]},{"id":2}],"total_results":2}
        """.trimIndent()
        val result = json.decodeFromString(TmdbMovieSearch.serializer(), body)
        assertThat(result.results).hasSize(2)
        assertThat(result.results[0].title).isEqualTo("Matrix")
        assertThat(result.results[0].backdropPath).isNull()
        assertThat(result.results[1].overview).isNull()
    }

    @Test
    fun `parses details with credits`() {
        val body = """{"overview":"x","genres":[{"id":1,"name":"Dram"}],"credits":{"cast":[{"name":"A"},{"name":"B"}]}}"""
        val d = json.decodeFromString(TmdbDetails.serializer(), body)
        assertThat(d.genres.map { it.name }).containsExactly("Dram")
        assertThat(d.credits!!.cast.map { it.name }).containsExactly("A", "B").inOrder()
    }
}
