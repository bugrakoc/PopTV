package pm.koc.poptv.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pm.koc.poptv.ui.MainViewModel
import pm.koc.poptv.ui.live.LiveTvScreen
import pm.koc.poptv.ui.login.LoginScreen
import pm.koc.poptv.ui.player.PlayerScreen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import pm.koc.poptv.R
import pm.koc.poptv.ui.home.HomeScreen

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
            )
        }
        composable<LiveRoute> {
            LiveTvScreen(
                onPlay = { stream, context -> nav.navigate(PlayerRoute(stream.key, context)) },
                onSearch = { nav.navigate(SearchRoute) },
            )
        }
        composable<PlayerRoute> {
            PlayerScreen(onBack = { nav.popBackStack() })
        }
        composable<MoviesRoute> { Placeholder() }
        composable<ShowsRoute> { Placeholder() }
        composable<GuideRoute> { Placeholder() }
        composable<SearchRoute> { Placeholder() }
        composable<SettingsRoute> { Placeholder() }
        composable<CategorySettingsRoute> { pm.koc.poptv.ui.settings.CategorySettingsScreen() }
    }
}

@Composable
private fun Placeholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.headlineMedium)
    }
}
