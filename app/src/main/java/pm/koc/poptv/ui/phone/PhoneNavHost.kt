package pm.koc.poptv.ui.phone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pm.koc.poptv.R
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.ui.MainViewModel
import pm.koc.poptv.ui.nav.AboutRoute
import pm.koc.poptv.ui.nav.CategorySettingsRoute
import pm.koc.poptv.ui.nav.GuideRoute
import pm.koc.poptv.ui.nav.HomeRoute
import pm.koc.poptv.ui.nav.LiveRoute
import pm.koc.poptv.ui.nav.LoginRoute
import pm.koc.poptv.ui.nav.MovieDetailRoute
import pm.koc.poptv.ui.nav.MoviesRoute
import pm.koc.poptv.ui.nav.PlayerRoute
import pm.koc.poptv.ui.nav.SearchRoute
import pm.koc.poptv.ui.nav.SettingsRoute
import pm.koc.poptv.ui.nav.ShowDetailRoute
import pm.koc.poptv.ui.nav.ShowsRoute
import pm.koc.poptv.ui.settings.AboutScreen
import pm.koc.poptv.ui.settings.CategorySettingsScreen
import pm.koc.poptv.ui.settings.SettingsScreen
import kotlin.reflect.KClass

private data class Tab(val route: Any, val type: KClass<*>, val icon: ImageVector, val label: Int)

private val Tabs = listOf(
    Tab(HomeRoute, HomeRoute::class, Icons.Filled.Home, R.string.nav_home),
    Tab(LiveRoute(), LiveRoute::class, Icons.Filled.LiveTv, R.string.nav_live),
    Tab(MoviesRoute, MoviesRoute::class, Icons.Filled.Movie, R.string.nav_movies),
    Tab(ShowsRoute, ShowsRoute::class, Icons.Filled.Tv, R.string.nav_shows),
    Tab(GuideRoute, GuideRoute::class, Icons.Filled.CalendarViewDay, R.string.nav_guide),
)

/** Phone navigation: the same routes and view models as on TV, with touch screens and a bottom bar. */
@Composable
fun PhoneNavHost(prefillUrl: String?, mainViewModel: MainViewModel = hiltViewModel()) {
    val start by mainViewModel.start.collectAsStateWithLifecycle()
    if (start == MainViewModel.Start.LOADING) {
        Box(Modifier.fillMaxSize())
        return
    }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val currentTab = Tabs.firstOrNull { tab -> destination?.hierarchy?.any { it.hasRoute(tab.type) } == true }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentTab != null) {
                NavigationBar {
                    Tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == currentTab,
                            onClick = { nav.openTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label), maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(selectedTextColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val startDestination: Any = if (start == MainViewModel.Start.HOME) HomeRoute else LoginRoute
        NavHost(
            navController = nav,
            startDestination = startDestination,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            val playVod: (String, Boolean) -> Unit = { key, startOver ->
                nav.navigate(PlayerRoute(key, startOver = startOver)) { launchSingleTop = true }
            }
            composable<LoginRoute> {
                PhoneLoginScreen(
                    prefillUrl = prefillUrl,
                    onLoggedIn = { nav.navigate(HomeRoute) { popUpTo(LoginRoute) { inclusive = true } } },
                )
            }
            composable<HomeRoute> {
                PhoneHomeScreen(
                    onOpenSearch = { nav.navigate(SearchRoute) },
                    onOpenSettings = { nav.navigate(SettingsRoute) },
                    onPlay = { key, context, startOver -> nav.navigate(PlayerRoute(key, context, startOver)) { launchSingleTop = true } },
                    onOpenMovie = { nav.navigate(MovieDetailRoute(it)) },
                    onOpenShow = { nav.navigate(ShowDetailRoute(it)) },
                )
            }
            composable<LiveRoute> {
                PhoneLiveScreen(
                    onPlay = { stream, context -> nav.navigate(PlayerRoute(stream.key, context)) { launchSingleTop = true } },
                    onSearch = { nav.navigate(SearchRoute) },
                )
            }
            composable<MoviesRoute> {
                PhoneVodBrowseScreen(
                    type = ContentType.MOVIE,
                    onOpenMovie = { nav.navigate(MovieDetailRoute(it)) },
                    onOpenShow = { nav.navigate(ShowDetailRoute(it)) },
                    onPlay = playVod,
                    onSearch = { nav.navigate(SearchRoute) },
                )
            }
            composable<ShowsRoute> {
                PhoneVodBrowseScreen(
                    type = ContentType.SERIES,
                    onOpenMovie = { nav.navigate(MovieDetailRoute(it)) },
                    onOpenShow = { nav.navigate(ShowDetailRoute(it)) },
                    onPlay = playVod,
                    onSearch = { nav.navigate(SearchRoute) },
                )
            }
            composable<GuideRoute> {
                PhoneGuideScreen(
                    onPlay = { stream, context -> nav.navigate(PlayerRoute(stream.key, context)) { launchSingleTop = true } },
                    onSearch = { nav.navigate(SearchRoute) },
                )
            }
            composable<PlayerRoute> {
                PhonePlayerScreen(onBack = { nav.popBackStack() })
            }
            composable<MovieDetailRoute> { PhoneMovieDetailScreen(onPlay = playVod) }
            composable<ShowDetailRoute> { PhoneShowDetailScreen(onPlay = playVod) }
            composable<SearchRoute> {
                PhoneSearchScreen(
                    onPlayChannel = { stream, context -> nav.navigate(PlayerRoute(stream.key, context)) { launchSingleTop = true } },
                    onOpenMovie = { nav.navigate(MovieDetailRoute(it)) },
                    onOpenShow = { nav.navigate(ShowDetailRoute(it)) },
                    onPlay = playVod,
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onOpenCategories = { nav.navigate(CategorySettingsRoute) },
                    onOpenAbout = { nav.navigate(AboutRoute) },
                    onLoggedOut = { nav.navigate(LoginRoute) { popUpTo(nav.graph.id) { inclusive = true } } },
                )
            }
            composable<CategorySettingsRoute> { CategorySettingsScreen() }
            composable<AboutRoute> { AboutScreen() }
        }
    }
}

/**
 * Bottom bar navigation: one back stack entry per tab, state kept when switching. Home is the root after
 * login (the graph's start destination may be the login screen, which is no longer on the stack).
 */
private fun NavHostController.openTab(route: Any) {
    navigate(route) {
        popUpTo<HomeRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
