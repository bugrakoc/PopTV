package pm.koc.poptv.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import pm.koc.poptv.R
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.repo.LiveContext
import pm.koc.poptv.data.repo.Programme
import pm.koc.poptv.ui.components.ConfirmDialog
import pm.koc.poptv.ui.components.ProgressBar
import pm.koc.poptv.ui.components.RemoteImage
import pm.koc.poptv.ui.components.rememberTimeFormatter
import pm.koc.poptv.ui.live.ChannelBrowserViewModel
import pm.koc.poptv.ui.menu.StreamMenu
import pm.koc.poptv.ui.theme.AccentWarm

/**
 * Category picker + channel list, shared by the Live TV tab and the player's channel panel. Long-press a
 * channel for its menu, or a category in the picker to hide it.
 */
@Composable
fun PhoneChannelList(
    viewModel: ChannelBrowserViewModel,
    onChannelClick: (StreamEntity, String) -> Unit,
    modifier: Modifier = Modifier,
    currentKey: String? = null,
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteKeys.collectAsStateWithLifecycle()
    val original by viewModel.showOriginalNames.collectAsStateWithLifecycle()
    val total by viewModel.totalLive.collectAsStateWithLifecycle()
    val channels = viewModel.channels.collectAsLazyPagingItems()

    var pickerOpen by remember { mutableStateOf(false) }
    var hideCandidate by remember { mutableStateOf<String?>(null) }
    var channelMenu by remember { mutableStateOf<StreamEntity?>(null) }
    val listState = rememberLazyListState()

    val uncategorized = stringResource(R.string.category_uncategorized)
    val selectedLabel = when (selected) {
        LiveContext.FAVORITES -> stringResource(R.string.live_favorites)
        LiveContext.RECENT -> stringResource(R.string.live_recent)
        LiveContext.ALL -> stringResource(R.string.live_all_channels)
        else -> categories.firstOrNull { it.name == selected }?.displayName?.ifBlank { uncategorized } ?: selected.ifBlank { uncategorized }
    }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PhoneHorizontalPadding, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryButton(selectedLabel, onClick = { pickerOpen = true }, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(12.dp))
            Text(
                pluralStringResource(R.plurals.channels_count, channels.itemCount, channels.itemCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (channels.itemCount == 0) {
            PhoneEmptyState(
                stringResource(if (selected == LiveContext.FAVORITES) R.string.live_no_favorites else R.string.live_no_channels),
                detail = if (selected == LiveContext.FAVORITES) stringResource(R.string.live_favorites_hint_touch) else null,
            )
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 4.dp)) {
                items(count = channels.itemCount, key = channels.itemKey { it.key }) { index ->
                    val stream = channels[index]
                    if (stream == null) {
                        Spacer(Modifier.height(64.dp))
                    } else {
                        PhoneChannelRow(
                            stream = stream,
                            title = if (original) stream.name else stream.displayName,
                            isFavorite = stream.key in favorites,
                            isCurrent = stream.key == currentKey,
                            onClick = { onChannelClick(stream, selected) },
                            onLongClick = { channelMenu = stream },
                            loadProgrammes = viewModel::nowNext,
                        )
                    }
                }
            }
        }
    }

    ScrollToTopOnCategoryChange(selected, channels) { listState.scrollToItem(0) }

    // Show the playing channel when the list opens in the player.
    LaunchedEffect(currentKey, channels.itemCount) {
        val key = currentKey ?: return@LaunchedEffect
        val idx = channels.itemSnapshotList.items.indexOfFirst { it.key == key }
        if (idx > 0 && listState.firstVisibleItemIndex == 0) listState.scrollToItem((idx - 2).coerceAtLeast(0))
    }

    if (pickerOpen) {
        val options = buildList {
            add(CategoryOption(LiveContext.FAVORITES, stringResource(R.string.live_favorites)))
            add(CategoryOption(LiveContext.RECENT, stringResource(R.string.live_recent)))
            add(CategoryOption(LiveContext.ALL, stringResource(R.string.live_all_channels), total))
            categories.forEach { add(CategoryOption(it.name, it.displayName.ifBlank { uncategorized }, it.itemCount, hideable = true)) }
        }
        CategorySheet(
            options = options,
            selected = selected,
            onSelect = { it?.let(viewModel::select) },
            onDismiss = { pickerOpen = false },
            onLongPress = { pickerOpen = false; hideCandidate = it },
        )
    }

    channelMenu?.let { stream ->
        StreamMenu(
            stream = stream,
            title = if (original) stream.name else stream.displayName,
            onDismiss = { channelMenu = null },
            onPlay = { onChannelClick(stream, selected) },
            onHideCategory = viewModel::hideCategory,
        )
    }

    hideCandidate?.let { group ->
        ConfirmDialog(
            title = stringResource(R.string.live_hide_category_title, group),
            message = stringResource(R.string.live_hide_category_message),
            confirmLabel = stringResource(R.string.action_hide),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { viewModel.hideCategory(group); hideCandidate = null },
            onDismiss = { hideCandidate = null },
        )
    }
}

/** One channel: number, logo, name, current programme with progress and the next programme. */
@Composable
fun PhoneChannelRow(
    stream: StreamEntity,
    title: String,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    loadProgrammes: suspend (String) -> List<Programme>,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
) {
    val programmes by produceState(initialValue = emptyList<Programme>(), stream.epgChannelId) {
        value = stream.epgChannelId?.let { loadProgrammes(it) }.orEmpty()
    }
    val time = rememberTimeFormatter()
    val now = System.currentTimeMillis()
    val current = programmes.firstOrNull { it.isLive(now) }
    val next = programmes.firstOrNull { it.start >= (current?.end ?: now) }

    Row(
        modifier
            .fillMaxWidth()
            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = PhoneHorizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RemoteImage(
            url = stream.logo,
            fallbackText = title,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.size(width = 72.dp, height = 44.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (stream.number > 0) {
                    Text(
                        stream.number.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (current != null) {
                Text(
                    "${time.time(current.start)}  ${current.title}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                ProgressBar(current.progress(now), height = 3.dp)
                if (next != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.epg_next_format, time.time(next.start), next.title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (isCurrent) {
            Icon(Icons.Filled.Equalizer, contentDescription = stringResource(R.string.live_now_playing), modifier = Modifier.size(20.dp))
        }
        if (isFavorite) {
            Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.favorite), tint = AccentWarm, modifier = Modifier.size(20.dp))
        }
    }
}
