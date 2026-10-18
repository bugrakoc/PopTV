package pm.koc.poptv.ui.nav

import kotlinx.serialization.Serializable

@Serializable data object LoginRoute
@Serializable data object HomeRoute
@Serializable data class LiveRoute(val category: String? = null)
@Serializable data object MoviesRoute
@Serializable data object ShowsRoute
@Serializable data class MovieDetailRoute(val streamKey: String)
@Serializable data class ShowDetailRoute(val seriesKey: String)
@Serializable data object GuideRoute
@Serializable data object SearchRoute
@Serializable data object SettingsRoute
@Serializable data object CategorySettingsRoute
@Serializable data object AboutRoute

/**
 * @param category live category used for the zapping list (null = all channels / favorites context).
 * @param startOver for VOD: ignore the saved resume position.
 */
@Serializable
data class PlayerRoute(
    val streamKey: String,
    val category: String? = null,
    val startOver: Boolean = false,
)
