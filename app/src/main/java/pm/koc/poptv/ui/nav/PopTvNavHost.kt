package pm.koc.poptv.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.ui.MainViewModel
import pm.koc.poptv.ui.guide.GuideScreen
import pm.koc.poptv.ui.home.HomeScreen
import pm.koc.poptv.ui.live.LiveTvScreen
import pm.koc.poptv.ui.login.LoginScreen
import pm.koc.poptv.ui.player.PlayerScreen
import pm.koc.poptv.ui.search.SearchScreen
import pm.koc.poptv.ui.settings.AboutScreen
import pm.koc.poptv.ui.settings.CategorySettingsScreen
import pm.koc.poptv.ui.settings.SettingsScreen
import pm.koc.poptv.ui.vod.MovieDetailScreen
import pm.koc.poptv.ui.vod.ShowDetailScreen
import pm.koc.poptv.ui.vod.VodBrowseScreen

@Composable
fun PopTvNavHost(prefillUrl: String?, mainViewModel: MainViewModel = hiltViewModel()) {
    val start by mainViewModel.start.collectAsStateWithLifecycle()
    if (start == MainViewModel.Start.LOADING) {
        Box(Modifier.fillMaxSize())
        return
    }
    val nav = rememberNavController()
    val startDestination: Any = if (start == MainViewModel.Start.HOME) HomeRoute else LoginRoute
    NavHost(navController = nav, startDestination = startDestination) {
        composable<LoginRoute> {
            LoginScreen(
                prefillUrl = prefillUrl,
                onLoggedIn = {
                    nav.navigate(HomeRoute) { popUpTo(LoginRoute) { inclusive = true } }
                },
            )
        }
        composable<HomeRoute> {
            HomeScreen(
                onOpenLive = { nav.navigate(LiveRoute()) },
                onOpenMovies = { nav.navigate(MoviesRoute) },
                onOpenShows = { nav.navigate(ShowsRoute) },
                onOpenGuide = { nav.navigate(GuideRoute) },
                onOpenSearch = { nav.navigate(SearchRoute) },
                onOpenSettings = { nav.navigate(SettingsRoute) },
                onPlay = { key, context, startOver -> nav.navigate(PlayerRoute(key, context, startOver)) { launchSingleTop = true } },
                onOpenMovie = { nav.navigate(MovieDetailRoute(it)) },
                onOpenShow = { nav.navigate(ShowDetailRoute(it)) },
            )
        }
        composable<LiveRoute> {
            LiveTvScreen(
                onPlay = { stream, context -> nav.navigate(PlayerRoute(stream.key, context)) { launchSingleTop = true } },
                onSearch = { nav.navigate(SearchRoute) },
            )
        }
        composable<PlayerRoute> {
            PlayerScreen(onBack = { nav.popBackStack() })
        }
        composable<MoviesRoute> {
            VodBrowseScreen(
                type = ContentType.MOVIE,
                onOpenMovie = { nav.navigate(MovieDetailRoute(it)) },
                onOpenShow = { nav.navigate(ShowDetailRoute(it)) },
                onPlay = { key, startOver -> nav.navigate(PlayerRoute(key, startOver = startOver)) { launchSingleTop = true } },
                onSearch = { nav.navigate(SearchRoute) },
            )
        }
        composable<ShowsRoute> {
            VodBrowseScreen(
                type = ContentType.SERIES,
                onOpenMovie = { nav.navigate(MovieDetailRoute(it)) },
                onOpenShow = { nav.navigate(ShowDetailRoute(it)) },
                onPlay = { key, startOver -> nav.navigate(PlayerRoute(key, startOver = startOver)) { launchSingleTop = true } },
                onSearch = { nav.navigate(SearchRoute) },
            )
        }
        composable<MovieDetailRoute> {
            MovieDetailScreen(onPlay = { key, startOver -> nav.navigate(PlayerRoute(key, startOver = startOver)) { launchSingleTop = true } })
        }
        composable<ShowDetailRoute> {
            ShowDetailScreen(onPlay = { key, startOver -> nav.navigate(PlayerRoute(key, startOver = startOver)) { launchSingleTop = true } })
        }
        composable<GuideRoute> {
            GuideScreen(onPlay = { stream, context -> nav.navigate(PlayerRoute(stream.key, context)) { launchSingleTop = true } })
        }
        composable<SearchRoute> {
            SearchScreen(
                onPlayChannel = { stream, context -> nav.navigate(PlayerRoute(stream.key, context)) { launchSingleTop = true } },
                onOpenMovie = { nav.navigate(MovieDetailRoute(it)) },
                onOpenShow = { nav.navigate(ShowDetailRoute(it)) },
                onPlay = { key, startOver -> nav.navigate(PlayerRoute(key, startOver = startOver)) { launchSingleTop = true } },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onOpenCategories = { nav.navigate(CategorySettingsRoute) },
                onOpenAbout = { nav.navigate(AboutRoute) },
                onLoggedOut = {
                    nav.navigate(LoginRoute) { popUpTo(nav.graph.id) { inclusive = true } }
                },
            )
        }
        composable<CategorySettingsRoute> { CategorySettingsScreen() }
        composable<AboutRoute> { AboutScreen() }
    }
}
