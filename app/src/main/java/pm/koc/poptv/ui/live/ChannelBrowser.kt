package pm.koc.poptv.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import pm.koc.poptv.R
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.repo.LiveContext
import pm.koc.poptv.ui.components.ConfirmDialog
import pm.koc.poptv.ui.components.EmptyState
import pm.koc.poptv.ui.components.safeRequestFocus

/**
 * Category sidebar + channel list. Moving through categories updates the list (debounced); long-press on a
 * channel toggles favorite, long-press on a category offers to hide it.
 */
@Composable
fun ChannelBrowser(
    viewModel: ChannelBrowserViewModel,
    onChannelClick: (StreamEntity, String) -> Unit,
    modifier: Modifier = Modifier,
    currentKey: String? = null,
    categoryWidth: Dp = 280.dp,
    initialFocusKey: String? = null,
    focusChannelsFirst: Boolean = false,
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteKeys.collectAsStateWithLifecycle()
    val original by viewModel.showOriginalNames.collectAsStateWithLifecycle()
    val total by viewModel.totalLive.collectAsStateWithLifecycle()
    val channels = viewModel.channels.collectAsLazyPagingItems()

    var lastFocusedChannel by rememberSaveable { mutableStateOf(initialFocusKey ?: currentKey) }
    var hideCandidate by remember { mutableStateOf<String?>(null) }
    val channelFocus = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }
    val categoryState = rememberLazyListState()
    val channelState = rememberLazyListState()

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        // ---- Categories ----
        LazyColumn(
            state = categoryState,
            modifier = Modifier
                .width(categoryWidth)
                .fillMaxHeight()
                .focusRestorer(categoryFocus),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            val specials = listOf(
                LiveContext.FAVORITES to R.string.live_favorites,
                LiveContext.RECENT to R.string.live_recent,
                LiveContext.ALL to R.string.live_all_channels,
            )
            items(specials, key = { it.first }) { (ctx, label) ->
                CategoryItem(
                    title = stringResource(label),
                    count = if (ctx == LiveContext.ALL) total else null,
                    selected = selected == ctx,
                    onClick = { viewModel.select(ctx); channelFocus.safeRequestFocus() },
                    modifier = Modifier
                        .then(if (selected == ctx) Modifier.focusRequester(categoryFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) viewModel.select(ctx) },
                )
            }
            items(categories, key = { it.id }) { cat ->
                CategoryItem(
                    title = cat.displayName.ifBlank { stringResource(R.string.category_uncategorized) },
                    count = cat.itemCount,
                    selected = selected == cat.name,
                    onClick = { viewModel.select(cat.name); channelFocus.safeRequestFocus() },
                    onLongClick = { hideCandidate = cat.name },
                    modifier = Modifier
                        .then(if (selected == cat.name) Modifier.focusRequester(categoryFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) viewModel.select(cat.name) },
                )
            }
        }

        // ---- Channels ----
        Column(Modifier.weight(1f)) {
            val title = when (selected) {
                LiveContext.FAVORITES -> stringResource(R.string.live_favorites)
                LiveContext.RECENT -> stringResource(R.string.live_recent)
                LiveContext.ALL -> stringResource(R.string.live_all_channels)
                else -> selected.ifBlank { stringResource(R.string.category_uncategorized) }
            }
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(
                pluralStringResource(R.plurals.channels_count, channels.itemCount, channels.itemCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (channels.itemCount == 0) {
                EmptyState(
                    stringResource(
                        if (selected == LiveContext.FAVORITES) R.string.live_no_favorites else R.string.live_no_channels,
                    ),
                    detail = if (selected == LiveContext.FAVORITES) stringResource(R.string.live_favorites_hint) else null,
                )
            } else {
                LazyColumn(
                    state = channelState,
                    modifier = Modifier
                        .fillMaxHeight()
                        .focusRestorer(channelFocus),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                ) {
                    items(count = channels.itemCount, key = channels.itemKey { it.key }) { index ->
                        val stream = channels[index]
                        if (stream == null) {
                            Spacer(Modifier.height(62.dp))
                        } else {
                            val isTarget = stream.key == lastFocusedChannel
                            ChannelRow(
                                stream = stream,
                                title = if (original) stream.name else stream.displayName,
                                isFavorite = stream.key in favorites,
                                isCurrent = stream.key == currentKey,
                                onClick = { onChannelClick(stream, selected) },
                                onLongClick = { viewModel.toggleFavorite(stream) },
                                loadProgrammes = viewModel::nowNext,
                                modifier = Modifier
                                    .then(if (isTarget || (lastFocusedChannel == null && index == 0)) Modifier.focusRequester(channelFocus) else Modifier)
                                    .onFocusChanged { if (it.isFocused) lastFocusedChannel = stream.key },
                            )
                        }
                    }
                }
            }
        }
    }

    // Initial focus: the remembered/current channel when the list has it, otherwise the selected category.
    LaunchedEffect(Unit) {
        repeat(10) {
            delay(80)
            val ok = if (focusChannelsFirst || lastFocusedChannel != null) {
                channelFocus.safeRequestFocus() || categoryFocus.safeRequestFocus()
            } else {
                categoryFocus.safeRequestFocus()
            }
            if (ok) return@LaunchedEffect
        }
    }
    // Scroll to the current channel when opening the overlay.
    LaunchedEffect(currentKey, channels.itemCount) {
        val key = currentKey ?: return@LaunchedEffect
        val idx = channels.itemSnapshotList.items.indexOfFirst { it.key == key }
        if (idx > 0 && channelState.firstVisibleItemIndex == 0) channelState.scrollToItem((idx - 2).coerceAtLeast(0))
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
