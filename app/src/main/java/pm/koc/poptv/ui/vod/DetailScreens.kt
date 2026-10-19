package pm.koc.poptv.ui.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import pm.koc.poptv.R
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.repo.Metadata
import pm.koc.poptv.domain.ResumePolicy
import pm.koc.poptv.ui.components.EmptyState
import pm.koc.poptv.ui.components.ProgressBar
import pm.koc.poptv.ui.components.RemoteImage
import pm.koc.poptv.ui.components.ScreenHorizontalPadding
import pm.koc.poptv.ui.components.TvDialog
import pm.koc.poptv.ui.components.formatPosition
import pm.koc.poptv.ui.components.localizedMessage
import pm.koc.poptv.ui.components.safeRequestFocus
import pm.koc.poptv.ui.live.listItemColors
import pm.koc.poptv.ui.theme.Background
import pm.koc.poptv.ui.theme.SuccessGreen
import androidx.compose.ui.platform.LocalConfiguration

/** Backdrop + poster + text block shared by movie and show details. */
@Composable
private fun DetailHeader(
    title: String,
    poster: String?,
    metadata: Metadata?,
    year: Int?,
    extraLine: String?,
    buttons: @Composable FlowRowScope.() -> Unit,
) {
    // At least 400dp tall, taller when the title, overview or wrapped buttons need it.
    Box(Modifier.fillMaxWidth().heightIn(min = 400.dp)) {
        val backdrop = metadata?.backdropUrl ?: metadata?.posterUrl ?: poster
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.45f,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.horizontalGradient(listOf(Background, Background.copy(alpha = 0.85f), Color.Transparent))),
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Background), startY = 250f)))
        Row(
            Modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            RemoteImage(
                url = metadata?.posterUrl ?: poster,
                fallbackText = title,
                shape = RoundedCornerShape(12.dp),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 210.dp, height = 315.dp),
                textStyle = MaterialTheme.typography.headlineLarge,
            )
            Column(Modifier.widthIn(max = 640.dp)) {
                Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                val meta = listOfNotNull(
                    (metadata?.year ?: year)?.toString(),
                    metadata?.rating?.let { String.format(LocalConfiguration.current.locales[0], "★ %.1f", it) },
                    metadata?.genres?.take(3)?.joinToString(", ")?.takeIf { it.isNotBlank() },
                    extraLine,
                ).joinToString("  ·  ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                }
                val overview = metadata?.overview
                if (!overview.isNullOrBlank()) {
                    Text(overview, style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                }
                if (!metadata?.cast.isNullOrEmpty()) {
                    Text(
                        stringResource(R.string.detail_cast, metadata!!.cast.take(5).joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(20.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = buttons,
                )
            }
        }
    }
}

@Composable
private fun IconLabelButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    if (primary) {
        Button(onClick = onClick, modifier = modifier, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}

@Composable
fun MovieDetailScreen(
    onPlay: (streamKey: String, startOver: Boolean) -> Unit,
    viewModel: MovieDetailViewModel = hiltViewModel(),
) {
    val stream by viewModel.stream.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val favorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val metadata by viewModel.metadata.collectAsStateWithLifecycle()
    val original by viewModel.showOriginalNames.collectAsStateWithLifecycle()
    val playFocus = remember { FocusRequester() }

    val s = stream
    if (s == null) {
        EmptyState(stringResource(R.string.player_loading))
        return
    }
    val p = position
    val watched = p != null && (p.watched || ResumePolicy.isWatched(p.positionMs, p.durationMs))
    val resumeAt = ResumePolicy.startPosition(p?.positionMs, p?.durationMs, p?.watched ?: false)

    Column(Modifier.fillMaxSize()) {
        DetailHeader(
            title = if (original) s.name else (metadata?.title?.takeIf { it.isNotBlank() } ?: s.displayName),
            poster = s.logo,
            metadata = metadata,
            year = s.year,
            extraLine = s.groupTitle.takeIf { it.isNotBlank() },
        ) {
            if (resumeAt > 0) {
                IconLabelButton(
                    Icons.Filled.PlayArrow,
                    stringResource(R.string.detail_resume_at, formatPosition(resumeAt)),
                    { onPlay(s.key, false) },
                    Modifier.focusRequester(playFocus),
                    primary = true,
                )
                IconLabelButton(Icons.Filled.Replay, stringResource(R.string.detail_start_over), { onPlay(s.key, true) })
            } else {
                IconLabelButton(Icons.Filled.PlayArrow, stringResource(R.string.player_play), { onPlay(s.key, true) }, Modifier.focusRequester(playFocus), primary = true)
            }
            IconLabelButton(
                if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                stringResource(if (favorite) R.string.detail_remove_favorite else R.string.detail_add_favorite),
                viewModel::toggleFavorite,
            )
            IconLabelButton(
                if (watched) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                stringResource(if (watched) R.string.detail_mark_unwatched else R.string.detail_mark_watched),
                { viewModel.setWatched(!watched) },
            )
        }
        if (p != null && !watched && p.durationMs > 0 && resumeAt > 0) {
            ProgressBar(
                ResumePolicy.progress(p.positionMs, p.durationMs),
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding).widthIn(max = 420.dp),
            )
        }
    }
    LaunchedEffect(s.key) {
        repeat(5) { delay(60); if (playFocus.safeRequestFocus()) return@LaunchedEffect }
    }
}

@Composable
fun ShowDetailScreen(
    onPlay: (streamKey: String, startOver: Boolean) -> Unit,
    viewModel: ShowDetailViewModel = hiltViewModel(),
) {
    val show by viewModel.show.collectAsStateWithLifecycle()
    val seasons by viewModel.seasons.collectAsStateWithLifecycle()
    val resume by viewModel.resume.collectAsStateWithLifecycle()
    val selectedSeason by viewModel.selectedSeason.collectAsStateWithLifecycle()
    val favorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val metadata by viewModel.metadata.collectAsStateWithLifecycle()
    val episodesLoad by viewModel.episodesLoad.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<EpisodeUi?>(null) }
    var lastFocused by rememberSaveable { mutableStateOf<String?>(null) }
    val playFocus = remember { FocusRequester() }
    val episodeFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }

    val sh = show
    if (sh == null) {
        EmptyState(stringResource(R.string.player_loading))
        return
    }
    val season = seasons.firstOrNull { it.number == selectedSeason } ?: seasons.firstOrNull()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "header") {
            DetailHeader(
                title = metadata?.title?.takeIf { it.isNotBlank() } ?: sh.name,
                poster = sh.logo,
                metadata = metadata,
                year = sh.year,
                extraLine = if (seasons.isEmpty()) null else pluralStringResource(R.plurals.seasons_count, seasons.size, seasons.size),
            ) {
                val r = resume
                if (r != null) {
                    val code = if (r.season != null && r.episode != null) stringResource(R.string.episode_code, r.season, r.episode) else r.displayName
                    IconLabelButton(Icons.Filled.PlayArrow, stringResource(R.string.detail_play_episode, code), { onPlay(r.key, false) }, Modifier.focusRequester(playFocus), primary = true)
                }
                IconLabelButton(
                    if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    stringResource(if (favorite) R.string.detail_remove_favorite else R.string.detail_add_favorite),
                    viewModel::toggleFavorite,
                )
            }
        }
        if (seasons.size > 1) {
            item(key = "seasons") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = ScreenHorizontalPadding, vertical = 8.dp),
                ) {
                    items(seasons, key = { it.number }) { s ->
                        FilterChip(selected = s.number == season?.number, onClick = { viewModel.selectSeason(s.number) }) {
                            Text(stringResource(R.string.season_n, s.number))
                        }
                    }
                }
            }
        }
        if (seasons.isEmpty()) {
            item(key = "episodes-state") {
                Column(Modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 16.dp)) {
                    when (val load = episodesLoad) {
                        EpisodesLoad.Loading -> Text(stringResource(R.string.show_loading_episodes), style = MaterialTheme.typography.titleMedium)
                        EpisodesLoad.Idle -> Text(stringResource(R.string.show_no_episodes), style = MaterialTheme.typography.titleMedium)
                        is EpisodesLoad.Failed -> {
                            Text(stringResource(R.string.show_episodes_failed), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(load.error.localizedMessage(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            IconLabelButton(Icons.Filled.Replay, stringResource(R.string.action_retry), viewModel::loadEpisodes, Modifier.focusRequester(retryFocus))
                            LaunchedEffect(load) { repeat(5) { delay(60); if (retryFocus.safeRequestFocus()) return@LaunchedEffect } }
                        }
                    }
                }
            }
        }
        if (season != null) {
            items(season.episodes, key = { it.stream.key }) { ep ->
                EpisodeRow(
                    episode = ep,
                    onClick = { onPlay(ep.stream.key, false) },
                    onLongClick = { menuFor = ep },
                    modifier = Modifier
                        .padding(horizontal = ScreenHorizontalPadding, vertical = 3.dp)
                        .then(if (ep.stream.key == lastFocused) Modifier.focusRequester(episodeFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) lastFocused = ep.stream.key },
                )
            }
        }
    }

    LaunchedEffect(sh.seriesKey, resume?.key) {
        repeat(8) {
            delay(60)
            val ok = if (lastFocused != null) episodeFocus.safeRequestFocus() || playFocus.safeRequestFocus() else playFocus.safeRequestFocus()
            if (ok) return@LaunchedEffect
        }
    }

    menuFor?.let { ep ->
        TvDialog(onDismissRequest = { menuFor = null }, title = episodeLabel(ep.stream)) {
            val first = remember { FocusRequester() }
            ListItem(selected = false, onClick = { menuFor = null; onPlay(ep.stream.key, true) }, headlineContent = { Text(stringResource(R.string.detail_start_over)) }, modifier = Modifier.focusRequester(first))
            ListItem(
                selected = false,
                onClick = { viewModel.setWatched(ep.stream, !ep.watched); menuFor = null },
                headlineContent = { Text(stringResource(if (ep.watched) R.string.detail_mark_unwatched else R.string.detail_mark_watched)) },
            )
            if (season != null) {
                val allWatched = season.episodes.all { it.watched }
                ListItem(
                    selected = false,
                    onClick = { viewModel.setSeasonWatched(season, !allWatched); menuFor = null },
                    headlineContent = { Text(stringResource(if (allWatched) R.string.detail_season_unwatched else R.string.detail_season_watched)) },
                )
            }
            LaunchedEffect(Unit) { first.safeRequestFocus() }
        }
    }
}

@Composable
private fun episodeLabel(stream: StreamEntity): String {
    val code = if (stream.season != null && stream.episode != null) stringResource(R.string.episode_code, stream.season, stream.episode) else null
    return listOfNotNull(code, stream.displayName).joinToString(" · ")
}

@Composable
private fun EpisodeRow(
    episode: EpisodeUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = episode.stream
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = listItemColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                s.episode?.let { stringResource(R.string.episode_short, it) } ?: "•",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(64.dp),
            )
            RemoteImage(
                url = s.logo,
                fallbackText = s.displayName,
                shape = RoundedCornerShape(6.dp),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 120.dp, height = 68.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(s.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (episode.progress > 0f && !episode.watched) {
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(episode.progress, height = 3.dp, modifier = Modifier.widthIn(max = 320.dp))
                }
            }
            if (episode.watched) {
                Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.detail_watched), tint = SuccessGreen, modifier = Modifier.size(22.dp))
            }
        }
    }
}
