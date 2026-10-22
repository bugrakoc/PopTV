package pm.koc.poptv.ui.phone

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import pm.koc.poptv.R
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.repo.LiveContext
import pm.koc.poptv.data.repo.Programme
import pm.koc.poptv.ui.components.RemoteImage
import pm.koc.poptv.ui.components.rememberTimeFormatter
import pm.koc.poptv.ui.guide.GuideTime
import pm.koc.poptv.ui.guide.GuideViewModel
import pm.koc.poptv.ui.theme.Accent

private val ChannelColumnWidth = 76.dp
private val RowHeight = 60.dp
private val SlotWidth = 132.dp
private val StripWidth = SlotWidth * (GuideTime.SPAN_MS / GuideTime.SLOT_MS).toInt()
private val MinTextCellWidth = 28.dp

/**
 * Channels x time grid. The time strip scrolls sideways (all rows together); the arrows move the 2.5 h window
 * by two hours, "Now" jumps back.
 */
@Composable
fun PhoneGuideScreen(
    onPlay: (StreamEntity, String) -> Unit,
    onSearch: () -> Unit,
    viewModel: GuideViewModel = hiltViewModel(),
) {
    val hasGuide by viewModel.hasGuide.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val windowStart by viewModel.windowStart.collectAsStateWithLifecycle()
    val original by viewModel.showOriginalNames.collectAsStateWithLifecycle()
    val channels = viewModel.channels.collectAsLazyPagingItems()
    val time = rememberTimeFormatter()
    val scroll = rememberScrollState()
    val listState = rememberLazyListState()

    var pickerOpen by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Pair<StreamEntity, Programme>?>(null) }
    val windowEnd = windowStart + GuideTime.SPAN_MS
    val uncategorized = stringResource(R.string.category_uncategorized)

    val categoryLabel = when (category) {
        LiveContext.ALL -> stringResource(R.string.live_all_channels)
        LiveContext.FAVORITES -> stringResource(R.string.live_favorites)
        else -> categories.firstOrNull { it.name == category }?.displayName?.ifBlank { uncategorized } ?: category
    }

    ScrollToTopOnCategoryChange(category, channels) { listState.scrollToItem(0) }
    // A new window starts scrolled to its beginning.
    LaunchedEffect(windowStart) { scroll.scrollTo(0) }

    PhoneScreen(
        title = stringResource(R.string.nav_guide),
        subtitle = time.day(windowStart),
        showBack = false,
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.nav_search)) }
        },
    ) {
        when (hasGuide) {
            null -> return@PhoneScreen
            false -> {
                PhoneEmptyState(stringResource(R.string.guide_unavailable), detail = stringResource(R.string.guide_unavailable_detail))
                return@PhoneScreen
            }
            true -> Unit
        }
        Row(
            Modifier.fillMaxWidth().padding(start = PhoneHorizontalPadding, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CategoryButton(categoryLabel, onClick = { pickerOpen = true }, modifier = Modifier.weight(1f, fill = false))
            IconButton(onClick = { viewModel.shiftWindow(-(GuideTime.SPAN_MS - GuideTime.SLOT_MS)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.guide_earlier))
            }
            TextButton(onClick = viewModel::jumpToNow) { Text(stringResource(R.string.guide_now)) }
            IconButton(onClick = { viewModel.shiftWindow(GuideTime.SPAN_MS - GuideTime.SLOT_MS) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.guide_later))
            }
        }
        TimeRuler(windowStart, scroll)
        Spacer(Modifier.height(4.dp))
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(3.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(channels.itemCount, key = channels.itemKey { it.key }) { index ->
                val channel = channels[index] ?: run {
                    Spacer(Modifier.height(RowHeight))
                    return@items
                }
                GuideRow(
                    channel = channel,
                    title = if (original) channel.name else channel.displayName,
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    scroll = scroll,
                    load = viewModel::programmes,
                    onPlay = { onPlay(channel, category) },
                    onOpen = { programme ->
                        val now = System.currentTimeMillis()
                        if (programme == null || programme.isLive(now)) onPlay(channel, category) else detail = channel to programme
                    },
                    onDetails = { programme -> detail = channel to programme },
                )
            }
        }
    }

    if (pickerOpen) {
        val options = buildList {
            add(CategoryOption(LiveContext.ALL, stringResource(R.string.live_all_channels)))
            add(CategoryOption(LiveContext.FAVORITES, stringResource(R.string.live_favorites)))
            categories.forEach { add(CategoryOption(it.name, it.displayName.ifBlank { uncategorized }, it.itemCount)) }
        }
        CategorySheet(
            options = options,
            selected = category,
            onSelect = { it?.let(viewModel::selectCategory) },
            onDismiss = { pickerOpen = false },
        )
    }

    detail?.let { (channel, programme) ->
        ProgrammeDialog(
            channelName = if (original) channel.name else channel.displayName,
            programme = programme,
            onWatch = { detail = null; onPlay(channel, category) },
            onDismiss = { detail = null },
        )
    }
}

@Composable
private fun TimeRuler(windowStart: Long, scroll: ScrollState) {
    val time = rememberTimeFormatter()
    val slots = (GuideTime.SPAN_MS / GuideTime.SLOT_MS).toInt()
    Row {
        Spacer(Modifier.width(ChannelColumnWidth))
        Box(Modifier.horizontalScroll(scroll).width(StripWidth)) {
            for (i in 0 until slots) {
                Text(
                    time.time(windowStart + i * GuideTime.SLOT_MS),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.offset(x = SlotWidth * i).padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun GuideRow(
    channel: StreamEntity,
    title: String,
    windowStart: Long,
    windowEnd: Long,
    scroll: ScrollState,
    load: suspend (String, Long, Long) -> List<Programme>,
    onPlay: () -> Unit,
    onOpen: (Programme?) -> Unit,
    onDetails: (Programme) -> Unit,
) {
    val programmes by produceState(emptyList<Programme>(), channel.epgChannelId, windowStart) {
        value = channel.epgChannelId?.let { load(it, windowStart, windowEnd) }.orEmpty()
    }
    val now = System.currentTimeMillis()
    val time = rememberTimeFormatter()

    Row(Modifier.height(RowHeight), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .width(ChannelColumnWidth)
                .fillMaxHeight()
                .combinedClickable(onClick = onPlay)
                .padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            RemoteImage(channel.logo, title, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(width = 56.dp, height = 34.dp))
            Text(
                channel.number.takeIf { it > 0 }?.toString() ?: title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.horizontalScroll(scroll).width(StripWidth).fillMaxHeight()) {
            val visible = programmes.mapNotNull { p -> GuideTime.clip(p.start, p.end, windowStart, windowEnd)?.let { p to it } }
            if (visible.isEmpty()) {
                GuideCell(
                    text = title,
                    subtext = stringResource(if (channel.epgChannelId == null) R.string.epg_no_guide_for_channel else R.string.epg_no_info),
                    live = true,
                    x = 0.dp,
                    width = StripWidth,
                    scroll = scroll,
                    onClick = { onOpen(null) },
                    onLongClick = null,
                )
            } else {
                visible.forEach { (p, frac) ->
                    GuideCell(
                        text = p.title,
                        subtext = time.range(p.start, p.end),
                        live = p.isLive(now),
                        x = StripWidth * frac.first,
                        width = StripWidth * (frac.second - frac.first),
                        scroll = scroll,
                        onClick = { onOpen(p) },
                        onLongClick = { onDetails(p) },
                    )
                }
            }
            GuideTime.clip(now, now + 1, windowStart, windowEnd)?.let { (f, _) ->
                Box(
                    Modifier
                        .offset(x = StripWidth * f)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Accent),
                )
            }
        }
    }
}

@Composable
private fun GuideCell(
    text: String,
    subtext: String?,
    live: Boolean,
    x: Dp,
    width: Dp,
    scroll: ScrollState,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .offset(x = x)
            .width((width - 3.dp).coerceAtLeast(6.dp))
            .fillMaxHeight()
            .clip(shape)
            .background(if (live) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (width < MinTextCellWidth) return@Box
        Column(
            Modifier
                // Keeps the title in view while the start of a long programme is scrolled off to the left.
                // Read during layout, so scrolling does not recompose the grid.
                .layout { measurable, constraints ->
                    val hidden = (scroll.value - x.roundToPx())
                        .coerceIn(0, (constraints.maxWidth - MinTextCellWidth.roundToPx()).coerceAtLeast(0))
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth - hidden))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(hidden, 0) }
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtext != null && width > 72.dp) {
                Text(subtext, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ProgrammeDialog(
    channelName: String,
    programme: Programme,
    onWatch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val time = rememberTimeFormatter()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(programme.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    listOfNotNull(channelName, time.dayAndTime(programme.start) + " – " + time.time(programme.end), programme.category).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                programme.subtitle?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.titleSmall)
                }
                programme.description?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onWatch) { Text(stringResource(R.string.guide_watch_channel)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
