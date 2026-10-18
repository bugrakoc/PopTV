package pm.koc.poptv.ui.guide

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import pm.koc.poptv.R
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.repo.LiveContext
import pm.koc.poptv.data.repo.Programme
import pm.koc.poptv.ui.components.Choice
import pm.koc.poptv.ui.components.ChoiceDialog
import pm.koc.poptv.ui.components.EmptyState
import pm.koc.poptv.ui.components.RemoteImage
import pm.koc.poptv.ui.components.ScreenScaffold
import pm.koc.poptv.ui.components.TvDialog
import pm.koc.poptv.ui.components.rememberTimeFormatter
import pm.koc.poptv.ui.components.safeRequestFocus
import pm.koc.poptv.ui.theme.Accent

private val ChannelColumnWidth = 230.dp
private val RowHeight = 62.dp

@Composable
fun GuideScreen(
    onPlay: (StreamEntity, String) -> Unit,
    viewModel: GuideViewModel = hiltViewModel(),
) {
    val hasGuide by viewModel.hasGuide.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val windowStart by viewModel.windowStart.collectAsStateWithLifecycle()
    val original by viewModel.showOriginalNames.collectAsStateWithLifecycle()
    val channels = viewModel.channels.collectAsLazyPagingItems()
    val time = rememberTimeFormatter()

    var categoryDialog by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Pair<StreamEntity, Programme>?>(null) }
    // Focus target survives navigation to the player and back.
    var targetKey by rememberSaveable { mutableStateOf<String?>(null) }
    var targetTime by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    val targetFocus = remember { FocusRequester() }
    val windowEnd = windowStart + GuideTime.SPAN_MS

    val categoryLabel = when (category) {
        LiveContext.ALL -> stringResource(R.string.live_all_channels)
        LiveContext.FAVORITES -> stringResource(R.string.live_favorites)
        else -> category
    }

    ScreenScaffold(
        title = stringResource(R.string.nav_guide),
        subtitle = time.day(windowStart),
        actions = {
            OutlinedButton(onClick = { categoryDialog = true }) { Text(categoryLabel, maxLines = 1) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                viewModel.jumpToNow()
                targetTime = System.currentTimeMillis()
            }) { Text(stringResource(R.string.guide_now)) }
        },
    ) {
        when {
            hasGuide == null -> return@ScreenScaffold
            hasGuide == false -> {
                EmptyState(stringResource(R.string.guide_unavailable), detail = stringResource(R.string.guide_unavailable_detail))
                return@ScreenScaffold
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val stripWidth = maxWidth - ChannelColumnWidth
            Column {
                TimeRuler(windowStart, stripWidth)
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    state = rememberLazyListState(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
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
                            stripWidth = stripWidth,
                            load = viewModel::programmes,
                            focusTime = if (channel.key == targetKey) targetTime else null,
                            focusRequester = targetFocus,
                            onFocused = { t -> targetKey = channel.key; targetTime = t },
                            onEdge = { forward ->
                                viewModel.shiftWindow(if (forward) GuideTime.SLOT_MS else -GuideTime.SLOT_MS)
                                targetKey = channel.key
                                targetTime = if (forward) windowEnd + GuideTime.SLOT_MS - 60_000 else windowStart - GuideTime.SLOT_MS
                            },
                            onOpen = { programme ->
                                val now = System.currentTimeMillis()
                                if (programme == null || programme.isLive(now)) onPlay(channel, category) else detail = channel to programme
                            },
                            onDetails = { programme -> if (programme != null) detail = channel to programme },
                        )
                    }
                }
            }
        }
    }

    // Initial focus: the programme airing now on the first channel (or the remembered target).
    LaunchedEffect(channels.itemCount > 0) {
        if (channels.itemCount == 0) return@LaunchedEffect
        if (targetKey == null) targetKey = channels.peek(0)?.key
    }
    LaunchedEffect(targetKey, targetTime, windowStart) {
        repeat(10) {
            delay(60)
            if (targetFocus.safeRequestFocus()) return@LaunchedEffect
        }
    }

    if (categoryDialog) {
        val choices = buildList<Choice<String>> {
            add(Choice(LiveContext.ALL, stringResource(R.string.live_all_channels)))
            add(Choice(LiveContext.FAVORITES, stringResource(R.string.live_favorites)))
            categories.forEach { add(Choice(it.name, it.displayName.ifBlank { stringResource(R.string.category_uncategorized) })) }
        }
        ChoiceDialog(
            title = stringResource(R.string.guide_category),
            choices = choices,
            selected = category,
            onSelect = {
                viewModel.selectCategory(it)
                targetKey = null
                categoryDialog = false
            },
            onDismiss = { categoryDialog = false },
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
private fun TimeRuler(windowStart: Long, stripWidth: Dp) {
    val time = rememberTimeFormatter()
    val slots = (GuideTime.SPAN_MS / GuideTime.SLOT_MS).toInt()
    val slotWidth = stripWidth / slots
    Row {
        Spacer(Modifier.width(ChannelColumnWidth))
        Box(Modifier.width(stripWidth)) {
            for (i in 0 until slots) {
                Text(
                    time.time(windowStart + i * GuideTime.SLOT_MS),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.offset(x = slotWidth * i).padding(start = 4.dp),
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
    stripWidth: Dp,
    load: suspend (String, Long, Long) -> List<Programme>,
    focusTime: Long?,
    focusRequester: FocusRequester,
    onFocused: (Long) -> Unit,
    onEdge: (forward: Boolean) -> Unit,
    onOpen: (Programme?) -> Unit,
    onDetails: (Programme?) -> Unit,
) {
    val programmes by produceState(emptyList<Programme>(), channel.epgChannelId, windowStart) {
        value = channel.epgChannelId?.let { load(it, windowStart, windowEnd) }.orEmpty()
    }
    val now = System.currentTimeMillis()
    val time = rememberTimeFormatter()

    Row(Modifier.height(RowHeight), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.width(ChannelColumnWidth).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                channel.number.takeIf { it > 0 }?.toString().orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(38.dp),
            )
            RemoteImage(channel.logo, title, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(width = 64.dp, height = 40.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.width(stripWidth).fillMaxHeight()) {
            val visible = programmes.mapNotNull { p -> GuideTime.clip(p.start, p.end, windowStart, windowEnd)?.let { p to it } }
            if (visible.isEmpty()) {
                GuideCell(
                    text = stringResource(if (channel.epgChannelId == null) R.string.epg_no_guide_for_channel else R.string.epg_no_info),
                    subtext = null,
                    live = true,
                    x = 0.dp,
                    width = stripWidth,
                    modifier = Modifier
                        .then(if (focusTime != null) Modifier.focusRequester(focusRequester) else Modifier)
                        .onFocusChanged { if (it.isFocused) onFocused(maxOf(now, windowStart)) }
                        .edgeKeys(isFirst = true, isLast = true, onEdge = onEdge),
                    onClick = { onOpen(null) },
                    onLongClick = {},
                )
            } else {
                visible.forEachIndexed { index, (p, frac) ->
                    val isTarget = focusTime != null && (focusTime in p.start until p.end ||
                        (index == 0 && focusTime < p.start) || (index == visible.lastIndex && focusTime >= p.end))
                    GuideCell(
                        text = p.title,
                        subtext = time.range(p.start, p.end),
                        live = p.isLive(now),
                        x = stripWidth * frac.first,
                        width = stripWidth * (frac.second - frac.first),
                        modifier = Modifier
                            .then(if (isTarget) Modifier.focusRequester(focusRequester) else Modifier)
                            .onFocusChanged { if (it.isFocused) onFocused(maxOf(p.start, windowStart) + 60_000) }
                            .edgeKeys(isFirst = index == 0, isLast = index == visible.lastIndex, onEdge = onEdge),
                        onClick = { onOpen(p) },
                        onLongClick = { onDetails(p) },
                    )
                }
            }
            GuideTime.clip(now, now + 1, windowStart, windowEnd)?.let { (f, _) ->
                Box(
                    Modifier
                        .offset(x = stripWidth * f)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Accent),
                )
            }
        }
    }
}

/** Left on the first cell / right on the last cell scrolls the time window. */
private fun Modifier.edgeKeys(isFirst: Boolean, isLast: Boolean, onEdge: (Boolean) -> Unit): Modifier =
    onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (event.nativeKeyEvent.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (isFirst) { onEdge(false); true } else false
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (isLast) { onEdge(true); true } else false
            else -> false
        }
    }

@Composable
private fun GuideCell(
    text: String,
    subtext: String?,
    live: Boolean,
    x: Dp,
    width: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .offset(x = x)
            .width((width - 3.dp).coerceAtLeast(6.dp))
            .fillMaxHeight(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (live) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtext != null && width > 110.dp) {
                Text(subtext, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val focus = remember { FocusRequester() }
    TvDialog(onDismissRequest = onDismiss, title = programme.title) {
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
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 12, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onWatch, modifier = Modifier.focusRequester(focus)) { Text(stringResource(R.string.guide_watch_channel)) }
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    }
    LaunchedEffect(Unit) { focus.safeRequestFocus() }
}
