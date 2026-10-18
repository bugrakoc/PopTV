package pm.koc.poptv.data.repo

import pm.koc.poptv.core.SearchQuery
import pm.koc.poptv.data.db.AppDatabase
import pm.koc.poptv.data.db.ShowEntity
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.domain.ContentType
import javax.inject.Inject
import javax.inject.Singleton

data class SearchResults(
    val channels: List<StreamEntity> = emptyList(),
    val movies: List<StreamEntity> = emptyList(),
    val shows: List<ShowEntity> = emptyList(),
) {
    val isEmpty: Boolean get() = channels.isEmpty() && movies.isEmpty() && shows.isEmpty()
}

/** Global, accent-insensitive and Turkish-aware search over channels, movies and shows. */
@Singleton
class SearchRepository @Inject constructor(private val db: AppDatabase) {

    suspend fun search(query: String, limit: Int = 40): SearchResults? {
        val pattern = SearchQuery.likePattern(query) ?: return null
        return SearchResults(
            channels = db.streamDao().searchList(ContentType.LIVE, pattern, limit),
            movies = db.streamDao().searchList(ContentType.MOVIE, pattern, limit),
            shows = db.showDao().searchList(pattern, limit),
        )
    }
}
