package pm.koc.poptv.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import pm.koc.poptv.R
import pm.koc.poptv.data.db.ShowEntity
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.repo.ContinueEntry
import pm.koc.poptv.data.repo.EpgRefreshState
import pm.koc.poptv.data.repo.ImportState
import pm.koc.poptv.data.repo.LiveContext
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.ui.components.ChannelCard
import pm.koc.poptv.ui.components.ConfirmDialog
import pm.koc.poptv.ui.components.ContextMenu
import pm.koc.poptv.ui.components.MenuAction
import pm.koc.poptv.ui.components.PosterCard
import pm.koc.poptv.ui.components.ScreenHorizontalPadding
import pm.koc.poptv.ui.components.safeRequestFocus
import pm.koc.poptv.ui.menu.ShowMenu
import pm.koc.poptv.ui.menu.StreamMenu
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    onOpenLive: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenShows: () -> Unit,
    onOpenGuide: () -> Unit,
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

    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<HomeMenu?>(null) }
    var epgNoticeVisible by rememberSaveable { mutableStateOf(false) }
    var lastFocused by rememberSaveable { mutableStateOf<String?>(null) }
    val activity = LocalActivity.current
    val restoreFocus = remember { FocusRequester() }
    val firstTileFocus = remember { FocusRequester() }
    BackHandler { confirmExit = true }

    // Show the "no guide" notice once per playlist.
    LaunchedEffect(showEpgNotice) {
        if (showEpgNotice) {
            epgNoticeVisible = true
            viewModel.epgNoticeSeen()
        }
    }

    fun Modifier.track(id: String): Modifier = this
        .then(if (id == lastFocused) Modifier.focusRequester(restoreFocus) else Modifier)
        .onFocusChanged { if (it.isFocused) lastFocused = id }

    fun title(s: StreamEntity) = if (original) s.name else s.displayName

    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(horizontal = ScreenHorizontalPadding)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.banner), contentDescription = null, modifier = Modifier.size(width = 128.dp, height = 72.dp))
                    Spacer(Modifier.weight(1f))
                    val status = when {
                        importState is ImportState.Running -> stringResource(R.string.home_updating_playlist)
                        epgState is EpgRefreshState.Running -> stringResource(R.string.home_updating_guide)
                        else -> null
                    }
                    if (status != null) {
                        Text(status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(24.dp))
                    }
                    Clock()
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.focusRestorer(firstTileFocus),
                ) {
                    item { NavTile(Icons.Filled.LiveTv, stringResource(R.string.nav_live), onOpenLive, Modifier.focusRequester(firstTileFocus).track("nav:live")) }
                    item { NavTile(Icons.Filled.Movie, stringResource(R.string.nav_movies), onOpenMovies, Modifier.track("nav:movies")) }
                    item { NavTile(Icons.Filled.Tv, stringResource(R.string.nav_shows), onOpenShows, Modifier.track("nav:shows")) }
                    item { NavTile(Icons.Filled.CalendarViewDay, stringResource(R.string.nav_guide), onOpenGuide, Modifier.track("nav:guide")) }
                    item { NavTile(Icons.Filled.Search, stringResource(R.string.nav_search), onOpenSearch, Modifier.track("nav:search")) }
                    item { NavTile(Icons.Filled.Settings, stringResource(R.string.nav_settings), onOpenSettings, Modifier.track("nav:settings")) }
                }
            }
        }

        if (epgNoticeVisible) {
            item(key = "epg-notice") {
                Row(
                    Modifier
                        .padding(horizontal = ScreenHorizontalPadding)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.epg_unavailable_notice), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(16.dp))
                    OutlinedButton(onClick = { epgNoticeVisible = false }) { Text(stringResource(R.string.action_ok)) }
                }
            }
        }

        row(this, "continue", R.string.home_continue_watching, continueWatching, { it.stream.key }) { entry ->
            val s = entry.stream
            val subtitle = when {
                entry.item.isNextEpisode -> stringResource(R.string.home_next_episode)
                s.type == ContentType.SERIES && s.season != null && s.episode != null -> stringResource(R.string.episode_code, s.season, s.episode)
                else -> null
            }
            PosterCard(
                title = if (s.type == ContentType.SERIES) s.seriesName ?: title(s) else title(s),
                imageUrl = s.logo,
                subtitle = subtitle,
                progress = entry.progress,
                onClick = { onPlay(s.key, null, false) },
                onLongClick = { menuFor = HomeMenu.Continue(entry) },
                modifier = Modifier.track("continue:${s.key}"),
            )
        }
        row(this, "fav-channels", R.string.home_favorite_channels, favoriteChannels, { it.key }) { s ->
            ChannelCard(
                title = title(s),
                logo = s.logo,
                subtitle = s.number.takeIf { it > 0 }?.toString(),
                onClick = { onPlay(s.key, LiveContext.FAVORITES, false) },
                onLongClick = { menuFor = HomeMenu.Channel(s, LiveContext.FAVORITES, recent = false) },
                modifier = Modifier.track("fav:${s.key}"),
            )
        }
        row(this, "recent-channels", R.string.home_recent_channels, recentChannels, { it.key }) { s ->
            ChannelCard(
                title = title(s),
                logo = s.logo,
                subtitle = s.number.takeIf { it > 0 }?.toString(),
                onClick = { onPlay(s.key, LiveContext.ALL, false) },
                onLongClick = { menuFor = HomeMenu.Channel(s, LiveContext.ALL, recent = true) },
                modifier = Modifier.track("recent:${s.key}"),
            )
        }
        row(this, "fav-movies", R.string.home_favorite_movies, favoriteMovies, { it.key }) { s ->
            PosterCard(
                title = title(s),
                imageUrl = s.logo,
                subtitle = s.year?.toString(),
                onClick = { onOpenMovie(s.key) },
                onLongClick = { menuFor = HomeMenu.Movie(s) },
                modifier = Modifier.track("favm:${s.key}"),
            )
        }
        row(this, "fav-shows", R.string.home_favorite_shows, favoriteShows, { it.seriesKey }) { sh ->
            PosterCard(
                title = sh.name,
                imageUrl = sh.logo,
                onClick = { onOpenShow(sh.seriesKey) },
                onLongClick = { menuFor = HomeMenu.Show(sh) },
                modifier = Modifier.track("favs:${sh.seriesKey}"),
            )
        }
        if (continueWatching.isEmpty() && favoriteChannels.isEmpty() && recentChannels.isEmpty()) {
            item(key = "hint") {
                Text(
                    stringResource(R.string.home_empty_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        repeat(10) {
            delay(80)
            val ok = if (lastFocused != null) restoreFocus.safeRequestFocus() || firstTileFocus.safeRequestFocus() else firstTileFocus.safeRequestFocus()
            if (ok) return@LaunchedEffect
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

    if (confirmExit) {
        ConfirmDialog(
            title = stringResource(R.string.exit_title),
            confirmLabel = stringResource(R.string.exit_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { activity?.finish() },
            onDismiss = { confirmExit = false },
        )
    }
}

/** What a long press on the home screen opened a menu for. */
private sealed interface HomeMenu {
    data class Continue(val entry: ContinueEntry) : HomeMenu
    data class Channel(val stream: StreamEntity, val context: String, val recent: Boolean) : HomeMenu
    data class Movie(val stream: StreamEntity) : HomeMenu
    data class Show(val show: ShowEntity) : HomeMenu
}

/** A titled horizontal row; nothing is emitted for an empty list. */
private fun <T> row(
    scope: LazyListScope,
    key: String,
    titleRes: Int,
    items: List<T>,
    itemKey: (T) -> String,
    content: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    scope.item(key = key) {
        Column {
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = ScreenHorizontalPadding, vertical = 8.dp),
                modifier = Modifier.focusRestorer(),
            ) {
                items(items, key = itemKey) { content(it) }
            }
        }
    }
}

@Composable
private fun NavTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 160.dp, height = 96.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.border))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

private val ClockFormat = DateTimeFormatter.ofPattern("HH:mm")

/** 24h wall clock, ticking on minute boundaries while the screen is started. */
@Composable
private fun Clock(modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        // Restarting on STARTED re-reads the time after the TV wakes from standby.
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(60_000 - now % 60_000)
            }
        }
    }
    Text(
        ClockFormat.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())),
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier,
    )
}
