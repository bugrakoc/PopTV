package pm.koc.poptv.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import pm.koc.poptv.R
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.repo.Metadata
import pm.koc.poptv.domain.ResumePolicy
import pm.koc.poptv.ui.components.ContextMenu
import pm.koc.poptv.ui.components.MenuAction
import pm.koc.poptv.ui.components.ProgressBar
import pm.koc.poptv.ui.components.RemoteImage
import pm.koc.poptv.ui.components.formatPosition
import pm.koc.poptv.ui.components.localizedMessage
import pm.koc.poptv.ui.theme.Background
import pm.koc.poptv.ui.theme.SuccessGreen
import pm.koc.poptv.ui.vod.EpisodeUi
import pm.koc.poptv.ui.vod.EpisodesLoad
import pm.koc.poptv.ui.vod.MovieDetailViewModel
import pm.koc.poptv.ui.vod.ShowDetailViewModel

/** Backdrop with the poster overlapping it, then title, meta line, actions and overview. */
private fun LazyListScope.detailHeader(
    title: String,
    poster: String?,
    metadata: Metadata?,
    year: Int?,
    extraLine: String?,
    actions: @Composable () -> Unit,
) {
    item(key = "header") {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val backdropHeight = maxWidth * 9f / 16f
            Box(Modifier.fillMaxWidth().height(backdropHeight)) {
                val backdrop = metadata?.backdropUrl ?: metadata?.posterUrl ?: poster
                if (backdrop != null) {
                    AsyncImage(
                        model = backdrop,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.6f,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Background.copy(alpha = 0.4f), Color.Transparent, Background))))
                Box(Modifier.statusBarsPadding()) { BackButton() }
            }
            // The poster overlaps the bottom of the backdrop.
            Row(
                Modifier.padding(start = PhoneHorizontalPadding, end = PhoneHorizontalPadding, top = backdropHeight - 56.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                RemoteImage(
                    url = metadata?.posterUrl ?: poster,
                    fallbackText = title,
                    shape = RoundedCornerShape(8.dp),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 104.dp, height = 156.dp),
                    textStyle = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(
                        (metadata?.year ?: year)?.toString(),
                        metadata?.rating?.let { String.format(LocalConfiguration.current.locales[0], "★ %.1f", it) },
                        extraLine,
                    ).joinToString("  ·  ")
                    if (meta.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                    val genres = metadata?.genres?.take(3)?.joinToString(", ")
                    if (!genres.isNullOrBlank()) {
                        Text(genres, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
    item(key = "actions") {
        Column(Modifier.padding(start = PhoneHorizontalPadding, end = PhoneHorizontalPadding, top = 16.dp, bottom = 8.dp)) {
            actions()
            val overview = metadata?.overview
            if (!overview.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(overview, style = MaterialTheme.typography.bodyMedium)
            }
            if (!metadata?.cast.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.detail_cast, metadata!!.cast.take(5).joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Full-width primary play button. */
@Composable
private fun PlayButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun SecondaryAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1)
    }
}

@Composable
fun PhoneMovieDetailScreen(
    onPlay: (streamKey: String, startOver: Boolean) -> Unit,
    viewModel: MovieDetailViewModel = hiltViewModel(),
) {
    val stream by viewModel.stream.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val favorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val metadata by viewModel.metadata.collectAsStateWithLifecycle()
    val original by viewModel.showOriginalNames.collectAsStateWithLifecycle()

    val s = stream
    if (s == null) {
        PhoneEmptyState(stringResource(R.string.player_loading))
        return
    }
    val p = position
    val watched = p != null && (p.watched || ResumePolicy.isWatched(p.positionMs, p.durationMs))
    val resumeAt = ResumePolicy.startPosition(p?.positionMs, p?.durationMs, p?.watched ?: false)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + navigationBarHeight())) {
        detailHeader(
            title = if (original) s.name else (metadata?.title?.takeIf { it.isNotBlank() } ?: s.displayName),
            poster = s.logo,
            metadata = metadata,
            year = s.year,
            extraLine = s.groupTitle.takeIf { it.isNotBlank() },
        ) {
            if (resumeAt > 0) {
                PlayButton(stringResource(R.string.detail_resume_at, formatPosition(resumeAt))) { onPlay(s.key, false) }
                if (p != null && p.durationMs > 0 && !watched) {
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(ResumePolicy.progress(p.positionMs, p.durationMs))
                }
            } else {
                PlayButton(stringResource(R.string.player_play)) { onPlay(s.key, true) }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (resumeAt > 0) {
                    SecondaryAction(Icons.Filled.Replay, stringResource(R.string.detail_start_over)) { onPlay(s.key, true) }
                }
                SecondaryAction(
                    if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    stringResource(if (favorite) R.string.detail_remove_favorite else R.string.detail_add_favorite),
                    viewModel::toggleFavorite,
                )
                SecondaryAction(
                    if (watched) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    stringResource(if (watched) R.string.detail_mark_unwatched else R.string.detail_mark_watched),
                ) { viewModel.setWatched(!watched) }
            }
        }
    }
}

@Composable
fun PhoneShowDetailScreen(
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

    val sh = show
    if (sh == null) {
        PhoneEmptyState(stringResource(R.string.player_loading))
        return
    }
    val season = seasons.firstOrNull { it.number == selectedSeason } ?: seasons.firstOrNull()
    val seasonsLine = if (seasons.isEmpty()) null else pluralStringResource(R.plurals.seasons_count, seasons.size, seasons.size)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + navigationBarHeight())) {
        detailHeader(
            title = metadata?.title?.takeIf { it.isNotBlank() } ?: sh.name,
            poster = sh.logo,
            metadata = metadata,
            year = sh.year,
            extraLine = seasonsLine,
        ) {
            val r = resume
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (r != null) {
                    val code = if (r.season != null && r.episode != null) stringResource(R.string.episode_code, r.season, r.episode) else r.displayName
                    Box(Modifier.weight(1f)) {
                        PlayButton(stringResource(R.string.detail_play_episode, code)) { onPlay(r.key, false) }
                    }
                    Spacer(Modifier.width(8.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                FilledTonalIconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = stringResource(if (favorite) R.string.detail_remove_favorite else R.string.detail_add_favorite),
                    )
                }
            }
        }
        if (seasons.size > 1) {
            item(key = "seasons") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = PhoneHorizontalPadding, vertical = 4.dp),
                ) {
                    items(seasons, key = { it.number }) { s ->
                        FilterChip(
                            selected = s.number == season?.number,
                            onClick = { viewModel.selectSeason(s.number) },
                            label = { Text(stringResource(R.string.season_n, s.number)) },
                        )
                    }
                }
            }
        }
        if (seasons.isEmpty()) {
            item(key = "episodes-state") {
                Column(Modifier.padding(horizontal = PhoneHorizontalPadding)) {
                    when (val load = episodesLoad) {
                        EpisodesLoad.Loading -> Text(stringResource(R.string.show_loading_episodes), style = MaterialTheme.typography.titleSmall)
                        EpisodesLoad.Idle -> Text(stringResource(R.string.show_no_episodes), style = MaterialTheme.typography.titleSmall)
                        is EpisodesLoad.Failed -> {
                            Text(stringResource(R.string.show_episodes_failed), style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(load.error.localizedMessage(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            SecondaryAction(Icons.Filled.Replay, stringResource(R.string.action_retry), viewModel::loadEpisodes)
                        }
                    }
                }
            }
        }
        if (season != null) {
            items(season.episodes, key = { it.stream.key }) { ep ->
                PhoneEpisodeRow(
                    episode = ep,
                    onClick = { onPlay(ep.stream.key, false) },
                    onLongClick = { menuFor = ep },
                )
            }
        }
    }

    menuFor?.let { ep ->
        val allWatched = season?.episodes?.all { it.watched } ?: false
        ContextMenu(
            title = episodeLabel(ep.stream),
            actions = listOfNotNull(
                MenuAction(stringResource(R.string.player_play), Icons.Filled.PlayArrow) { onPlay(ep.stream.key, false) },
                MenuAction(stringResource(R.string.detail_start_over), Icons.Filled.Replay) { onPlay(ep.stream.key, true) },
                MenuAction(
                    stringResource(if (ep.watched) R.string.detail_mark_unwatched else R.string.detail_mark_watched),
                    if (ep.watched) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                ) { viewModel.setWatched(ep.stream, !ep.watched) },
                season?.let {
                    MenuAction(
                        stringResource(if (allWatched) R.string.detail_season_unwatched else R.string.detail_season_watched),
                        Icons.Filled.CheckCircle,
                    ) { viewModel.setSeasonWatched(it, !allWatched) }
                },
            ),
            onDismiss = { menuFor = null },
        )
    }
}

@Composable
private fun episodeLabel(stream: StreamEntity): String {
    val code = if (stream.season != null && stream.episode != null) stringResource(R.string.episode_code, stream.season, stream.episode) else null
    return listOfNotNull(code, stream.displayName).joinToString(" · ")
}

@Composable
private fun PhoneEpisodeRow(episode: EpisodeUi, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = episode.stream
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = PhoneHorizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            RemoteImage(
                url = s.logo,
                fallbackText = s.displayName,
                shape = RoundedCornerShape(6.dp),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 112.dp, height = 63.dp),
            )
            if (episode.progress > 0f && !episode.watched) {
                ProgressBar(episode.progress, height = 3.dp, modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                s.episode?.let { stringResource(R.string.episode_short, it) } ?: "•",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(s.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (episode.watched) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.detail_watched), tint = SuccessGreen, modifier = Modifier.size(20.dp))
        }
    }
}
