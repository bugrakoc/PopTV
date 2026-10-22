package pm.koc.poptv.ui.phone

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pm.koc.poptv.R
import pm.koc.poptv.data.db.ShowEntity
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.repo.ContinueEntry
import pm.koc.poptv.data.repo.EpgRefreshState
import pm.koc.poptv.data.repo.ImportState
import pm.koc.poptv.data.repo.LiveContext
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.ui.components.ContextMenu
import pm.koc.poptv.ui.components.MenuAction
import pm.koc.poptv.ui.home.HomeViewModel
import pm.koc.poptv.ui.menu.ShowMenu
import pm.koc.poptv.ui.menu.StreamMenu

@Composable
fun PhoneHomeScreen(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlay: (streamKey: String, context: String?, startOver: Boolean) -> Unit,
    onOpenMovie: (String) -> Unit,
    onOpenShow: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val favoriteChannels by viewModel.favoriteChannels.collectAsStateWithLifecycle()
    val recentChannels by viewModel.recentChannels.collectAsStateWithLifecycle()
    val favoriteMovies by viewModel.favoriteMovies.collectAsStateWithLifecycle()
    val favoriteShows by viewModel.favoriteShows.collectAsStateWithLifecycle()
    val original by viewModel.showOriginalNames.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val epgState by viewModel.epgState.collectAsStateWithLifecycle()
    val showEpgNotice by viewModel.showEpgNotice.collectAsStateWithLifecycle()

    var menuFor by remember { mutableStateOf<HomeMenu?>(null) }
    var epgNoticeVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(showEpgNotice) {
        if (showEpgNotice) {
            epgNoticeVisible = true
            viewModel.epgNoticeSeen()
        }
    }

    fun title(s: StreamEntity) = if (original) s.name else s.displayName

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Image(painterResource(R.drawable.banner), contentDescription = stringResource(R.string.app_name), modifier = Modifier.size(width = 80.dp, height = 45.dp))
                },
                actions = {
                    IconButton(onClick = onOpenSearch) { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.nav_search)) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val status = when {
                importState is ImportState.Running -> R.string.home_updating_playlist
                epgState is EpgRefreshState.Running -> R.string.home_updating_guide
                else -> null
            }
            if (status != null) {
                item(key = "status") {
                    Column(Modifier.padding(horizontal = PhoneHorizontalPadding)) {
                        Text(stringResource(status), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
            }
            if (epgNoticeVisible) {
                item(key = "epg-notice") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.padding(horizontal = PhoneHorizontalPadding).fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.epg_unavailable_notice), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { epgNoticeVisible = false }, modifier = Modifier.align(Alignment.End)) {
                                Text(stringResource(R.string.action_ok))
                            }
                        }
                    }
                }
            }

            cardRow("continue", R.string.home_continue_watching, continueWatching, { it.stream.key }) { entry ->
                val s = entry.stream
                val subtitle = when {
                    entry.item.isNextEpisode -> stringResource(R.string.home_next_episode)
                    s.type == ContentType.SERIES && s.season != null && s.episode != null -> stringResource(R.string.episode_code, s.season, s.episode)
                    else -> null
                }
                PhonePosterCard(
                    title = if (s.type == ContentType.SERIES) s.seriesName ?: title(s) else title(s),
                    imageUrl = s.logo,
                    subtitle = subtitle,
                    progress = entry.progress,
                    onClick = { onPlay(s.key, null, false) },
                    onLongClick = { menuFor = HomeMenu.Continue(entry) },
                )
            }
            cardRow("fav-channels", R.string.home_favorite_channels, favoriteChannels, { it.key }) { s ->
                PhoneChannelCard(
                    title = title(s),
                    logo = s.logo,
                    subtitle = s.number.takeIf { it > 0 }?.toString(),
                    onClick = { onPlay(s.key, LiveContext.FAVORITES, false) },
                    onLongClick = { menuFor = HomeMenu.Channel(s, LiveContext.FAVORITES, recent = false) },
                )
            }
            cardRow("recent-channels", R.string.home_recent_channels, recentChannels, { it.key }) { s ->
                PhoneChannelCard(
                    title = title(s),
                    logo = s.logo,
                    subtitle = s.number.takeIf { it > 0 }?.toString(),
                    onClick = { onPlay(s.key, LiveContext.ALL, false) },
                    onLongClick = { menuFor = HomeMenu.Channel(s, LiveContext.ALL, recent = true) },
                )
            }
            cardRow("fav-movies", R.string.home_favorite_movies, favoriteMovies, { it.key }) { s ->
                PhonePosterCard(
                    title = title(s),
                    imageUrl = s.logo,
                    subtitle = s.year?.toString(),
                    onClick = { onOpenMovie(s.key) },
                    onLongClick = { menuFor = HomeMenu.Movie(s) },
                )
            }
            cardRow("fav-shows", R.string.home_favorite_shows, favoriteShows, { it.seriesKey }) { sh ->
                PhonePosterCard(
                    title = sh.name,
                    imageUrl = sh.logo,
                    onClick = { onOpenShow(sh.seriesKey) },
                    onLongClick = { menuFor = HomeMenu.Show(sh) },
                )
            }
            if (continueWatching.isEmpty() && favoriteChannels.isEmpty() && recentChannels.isEmpty()) {
                item(key = "hint") {
                    Text(
                        stringResource(R.string.home_empty_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = PhoneHorizontalPadding, vertical = 24.dp),
                    )
                }
            }
        }
    }

    when (val menu = menuFor) {
        null -> Unit
        is HomeMenu.Continue -> {
            val entry = menu.entry
            val s = entry.stream
            val isEpisode = s.type == ContentType.SERIES
            ContextMenu(
                title = if (isEpisode) s.seriesName ?: s.displayName else s.displayName,
                actions = listOfNotNull(
                    MenuAction(stringResource(R.string.player_play), Icons.Filled.PlayArrow) { onPlay(s.key, null, false) },
                    MenuAction(stringResource(R.string.detail_start_over), Icons.Filled.Replay) { onPlay(s.key, null, true) },
                    s.seriesKey?.takeIf { isEpisode }?.let { key ->
                        MenuAction(stringResource(R.string.home_go_to_show), Icons.Filled.Tv) { onOpenShow(key) }
                    },
                    MenuAction(stringResource(R.string.detail_mark_watched), Icons.Filled.Visibility) { viewModel.setWatched(entry, true) },
                    MenuAction(stringResource(R.string.home_remove_from_continue), Icons.Filled.Close) { viewModel.removeFromContinue(entry) },
                ),
                onDismiss = { menuFor = null },
            )
        }
        is HomeMenu.Channel -> StreamMenu(
            stream = menu.stream,
            title = title(menu.stream),
            onDismiss = { menuFor = null },
            onPlay = { onPlay(menu.stream.key, menu.context, false) },
            extraActions = if (menu.recent) {
                listOf(MenuAction(stringResource(R.string.menu_remove_recent), Icons.Filled.Close) { viewModel.removeFromRecent(menu.stream) })
            } else {
                emptyList()
            },
        )
        is HomeMenu.Movie -> StreamMenu(
            stream = menu.stream,
            title = title(menu.stream),
            onDismiss = { menuFor = null },
            onPlay = { startOver -> onPlay(menu.stream.key, null, startOver) },
            onOpenDetails = { onOpenMovie(menu.stream.key) },
        )
        is HomeMenu.Show -> ShowMenu(menu.show, onDismiss = { menuFor = null }, onOpen = { onOpenShow(menu.show.seriesKey) })
    }
}

private sealed interface HomeMenu {
    data class Continue(val entry: ContinueEntry) : HomeMenu
    data class Channel(val stream: StreamEntity, val context: String, val recent: Boolean) : HomeMenu
    data class Movie(val stream: StreamEntity) : HomeMenu
    data class Show(val show: ShowEntity) : HomeMenu
}

/** A titled horizontal row of cards; nothing is emitted for an empty list. */
private fun <T> LazyListScope.cardRow(
    key: String,
    titleRes: Int,
    items: List<T>,
    itemKey: (T) -> String,
    content: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = key) {
        Column {
            SectionTitle(stringResource(titleRes))
            LazyRow(horizontalArrangement = CardRowArrangement, contentPadding = CardRowPadding) {
                items(items, key = itemKey) { content(it) }
            }
        }
    }
}
