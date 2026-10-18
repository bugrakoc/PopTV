package pm.koc.poptv.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
fun PopTvNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = HomeRoute) {
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
        composable<LiveRoute> { Placeholder() }
        composable<MoviesRoute> { Placeholder() }
        composable<ShowsRoute> { Placeholder() }
        composable<GuideRoute> { Placeholder() }
        composable<SearchRoute> { Placeholder() }
        composable<SettingsRoute> { Placeholder() }
    }
}

@Composable
private fun Placeholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.headlineMedium)
    }
}
