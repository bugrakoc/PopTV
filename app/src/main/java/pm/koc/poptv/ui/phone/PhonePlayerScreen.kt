package pm.koc.poptv.ui.phone

import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import pm.koc.poptv.R
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.player.PlayerStatus
import pm.koc.poptv.player.PlayerUiState
import pm.koc.poptv.player.ResizeMode
import pm.koc.poptv.ui.components.formatPosition
import pm.koc.poptv.ui.live.ChannelBrowserViewModel
import pm.koc.poptv.ui.player.BottomScrim
import pm.koc.poptv.ui.player.CenterStatus
import pm.koc.poptv.ui.player.OverlayBackground
import pm.koc.poptv.ui.player.OverlayShape
import pm.koc.poptv.ui.player.PlayerChoiceDialog
import pm.koc.poptv.ui.player.PlayerDialog
import pm.koc.poptv.ui.player.PlayerViewModel
import pm.koc.poptv.ui.player.StreamInfoPanel
import pm.koc.poptv.ui.player.TopScrim
import pm.koc.poptv.ui.player.ZapBanner
import pm.koc.poptv.ui.player.applyStyle
import pm.koc.poptv.ui.player.episodeTitle
import pm.koc.poptv.ui.player.message
import kotlin.math.abs

private const val CONTROLS_TIMEOUT_MS = 4_000L
private const val SEEK_STEP_MS = 10_000L

/**
 * Fullscreen landscape player for phones. Tap shows or hides the controls; on videos a double tap on the left
 * or right half skips 10 s; on live TV a vertical swipe changes the channel.
 */
@OptIn(UnstableApi::class)
@Composable
fun PhonePlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
    browser: ChannelBrowserViewModel = hiltViewModel(),
) {
    val player by viewModel.player.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val showOriginal by browser.showOriginalNames.collectAsStateWithLifecycle()

    var controlsVisible by remember { mutableStateOf(true) }
    var channelsOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<PlayerDialog?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dragPosition by remember { mutableStateOf<Long?>(null) }
    var skipFeedback by remember { mutableStateOf<Boolean?>(null) }
    var skipCount by remember { mutableIntStateOf(0) }
    val current by rememberUpdatedState(ui)

    val failed = ui.status is PlayerStatus.Failed
    val title = ui.stream?.let { if (showOriginal) it.name else it.displayName }.orEmpty()

    FullscreenLandscape()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onStart()
                Lifecycle.Event.ON_STOP -> viewModel.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(ui.finished) { if (ui.finished) onBack() }

    // Hide the controls after a few seconds of playback without touches.
    LaunchedEffect(controlsVisible, lastInteraction, ui.isPlaying, dialog, channelsOpen, dragPosition) {
        if (controlsVisible && ui.isPlaying && dialog == null && !channelsOpen && dragPosition == null) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(skipCount) {
        if (skipFeedback != null) {
            delay(700)
            skipFeedback = null
        }
    }

    BackHandler {
        when {
            channelsOpen -> channelsOpen = false
            showInfo -> showInfo = false
            ui.upNext != null && ui.status != PlayerStatus.Ended -> viewModel.dismissUpNext()
            else -> {
                viewModel.saveNow()
                onBack()
            }
        }
    }

    fun touched() {
        lastInteraction = System.currentTimeMillis()
    }

    fun skip(forward: Boolean) {
        val target = if (forward) current.positionMs + SEEK_STEP_MS else (current.positionMs - SEEK_STEP_MS).coerceAtLeast(0)
        viewModel.seekTo(target)
        skipFeedback = forward
        skipCount++
        touched()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(true)
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { view ->
                if (view.player !== player) view.player = player
                view.resizeMode = when (ui.resizeMode) {
                    ResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    ResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    ResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
                view.subtitleView?.applyStyle(settings)
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        // ---- Gestures on the video ----
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(ui.isLive) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            touched()
                        },
                        onDoubleTap = if (ui.isLive) {
                            null
                        } else {
                            { offset -> skip(forward = offset.x > size.width / 2) }
                        },
                    )
                }
                .pointerInput(ui.isLive) {
                    if (!ui.isLive) return@pointerInput
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onVerticalDrag = { _, dy -> total += dy },
                        onDragEnd = {
                            // Swiping up brings the next channel, like scrolling a list.
                            if (abs(total) > 80.dp.toPx()) viewModel.zap(forward = total < 0)
                        },
                    )
                },
        )

        if (!failed && !controlsVisible) {
            CenterStatus(ui.status, Modifier.align(Alignment.Center))
        }

        skipFeedback?.let { forward ->
            Icon(
                if (forward) Icons.Filled.Forward10 else Icons.Filled.Replay10,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 96.dp)
                    .background(OverlayBackground, OverlayShape)
                    .padding(16.dp)
                    .size(40.dp),
            )
        }

        // Live banner after a zap while the controls are hidden.
        val stream = ui.stream
        AnimatedVisibility(
            visible = ui.isLive && stream != null && !controlsVisible && !channelsOpen && ui.bannerVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(16.dp),
        ) {
            if (stream != null) ZapBanner(stream, title, ui.programmes, ui.isFavorite, ui.status)
        }

        // ---- Controls ----
        AnimatedVisibility(visible = controlsVisible && !channelsOpen, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                TopControls(
                    ui = ui,
                    title = title,
                    onFavorite = { viewModel.toggleFavorite(); touched() },
                    onLastChannel = { viewModel.previousChannel(); touched() },
                    onDialog = { dialog = it; touched() },
                    onInfo = { showInfo = !showInfo; touched() },
                    onChannels = { channelsOpen = true },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                CenterControls(
                    ui = ui,
                    onPlayPause = { viewModel.togglePlayPause(); touched() },
                    onSkip = ::skip,
                    onZap = { viewModel.zap(it); touched() },
                    onNext = viewModel::playNextEpisode,
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(BottomScrim)
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (ui.isLive) {
                        if (stream != null) ZapBanner(stream, title, ui.programmes, ui.isFavorite, ui.status)
                    } else {
                        SeekBar(
                            positionMs = dragPosition ?: ui.positionMs,
                            durationMs = ui.durationMs,
                            onDrag = { dragPosition = it; touched() },
                            onDragEnd = {
                                dragPosition?.let(viewModel::seekTo)
                                dragPosition = null
                                touched()
                            },
                        )
                    }
                }
            }
        }

        if (showInfo) {
            StreamInfoPanel(
                ui.info,
                Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(top = 64.dp, end = 16.dp),
            )
        }

        // ---- Up next ----
        val upNext = ui.upNext
        if (upNext != null && !channelsOpen) {
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(16.dp)
                    .widthIn(max = 360.dp)
                    .background(OverlayBackground, OverlayShape)
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.player_up_next), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(episodeTitle(upNext.episode), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                upNext.secondsLeft?.let {
                    Text(stringResource(R.string.player_up_next_in, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::playNextEpisode) { Text(stringResource(R.string.player_play_now)) }
                    OutlinedButton(onClick = { if (ui.status == PlayerStatus.Ended) onBack() else viewModel.dismissUpNext() }) {
                        Text(stringResource(if (ui.status == PlayerStatus.Ended) R.string.action_close else R.string.action_cancel))
                    }
                }
            }
        }

        // ---- Failure ----
        val status = ui.status
        if (status is PlayerStatus.Failed && !channelsOpen) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
                    .widthIn(max = 480.dp)
                    .background(OverlayBackground, OverlayShape)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.player_failed), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(status.kind.message(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (ui.isLive) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.player_failed_swipe_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::retryNow) { Text(stringResource(R.string.action_retry)) }
                    if (ui.isLive) {
                        OutlinedButton(onClick = { channelsOpen = true }) { Text(stringResource(R.string.player_channels)) }
                    }
                    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                }
            }
        }

        // ---- Live channel panel ----
        if (channelsOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .pointerInput(Unit) { detectTapGestures { channelsOpen = false } },
            )
        }
        AnimatedVisibility(
            visible = ui.isLive && channelsOpen,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Surface(Modifier.fillMaxHeight().width(400.dp)) {
                PhoneChannelList(
                    viewModel = browser,
                    currentKey = stream?.key,
                    onChannelClick = { s, ctx ->
                        viewModel.playChannel(s, ctx)
                        channelsOpen = false
                        controlsVisible = false
                    },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.displayCutout).padding(top = 8.dp),
                )
            }
        }
    }

    dialog?.let { PlayerChoiceDialog(it, ui, viewModel, onClose = { dialog = null }) }
}

@Composable
private fun TopControls(
    ui: PlayerUiState,
    title: String,
    onFavorite: () -> Unit,
    onLastChannel: () -> Unit,
    onDialog: (PlayerDialog) -> Unit,
    onInfo: () -> Unit,
    onChannels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stream = ui.stream
    Row(
        modifier
            .fillMaxWidth()
            .background(TopScrim)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton()
        Column(Modifier.weight(1f)) {
            if (stream?.type == ContentType.SERIES && stream.season != null && stream.episode != null) {
                Text(stream.seriesName.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(episodeTitle(stream), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        ControlIcon(if (ui.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, stringResource(R.string.favorite), onClick = onFavorite)
        if (ui.isLive) {
            ControlIcon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.live_last_channel), onClick = onLastChannel)
        }
        ControlIcon(Icons.Filled.Audiotrack, stringResource(R.string.player_audio)) { onDialog(PlayerDialog.AUDIO) }
        ControlIcon(Icons.Filled.Subtitles, stringResource(R.string.player_subtitles)) { onDialog(PlayerDialog.SUBTITLES) }
        if (!ui.isLive) {
            ControlIcon(Icons.Filled.Speed, stringResource(R.string.player_speed)) { onDialog(PlayerDialog.SPEED) }
        }
        ControlIcon(Icons.Filled.AspectRatio, stringResource(R.string.player_aspect)) { onDialog(PlayerDialog.ASPECT) }
        ControlIcon(Icons.Filled.Info, stringResource(R.string.player_stream_info), onClick = onInfo)
        if (ui.isLive) {
            ControlIcon(Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(R.string.player_channels), onClick = onChannels)
        }
    }
}

@Composable
private fun CenterControls(
    ui: PlayerUiState,
    onPlayPause: () -> Unit,
    onSkip: (forward: Boolean) -> Unit,
    onZap: (forward: Boolean) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loading = ui.status == PlayerStatus.Loading || ui.status == PlayerStatus.Buffering || ui.status is PlayerStatus.Reconnecting
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
        if (ui.isLive) {
            ControlIcon(Icons.Filled.SkipPrevious, stringResource(R.string.player_previous_channel), large = true) { onZap(false) }
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(color = Color.White)
            }
            ControlIcon(Icons.Filled.SkipNext, stringResource(R.string.player_next_channel), large = true) { onZap(true) }
        } else {
            ControlIcon(Icons.Filled.Replay10, stringResource(R.string.player_back_10), large = true) { onSkip(false) }
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                if (loading) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    ControlIcon(
                        if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        stringResource(if (ui.isPlaying) R.string.player_pause else R.string.player_play),
                        large = true,
                        onClick = onPlayPause,
                    )
                }
            }
            ControlIcon(Icons.Filled.Forward10, stringResource(R.string.player_forward_10), large = true) { onSkip(true) }
            if (ui.stream?.type == ContentType.SERIES) {
                ControlIcon(Icons.Filled.SkipNext, stringResource(R.string.player_next_episode), large = true, onClick = onNext)
            }
        }
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onDrag: (Long) -> Unit, onDragEnd: () -> Unit) {
    Column {
        Slider(
            value = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
            onValueChange = { if (durationMs > 0) onDrag((it * durationMs).toLong()) },
            onValueChangeFinished = onDragEnd,
            enabled = durationMs > 0,
            colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatPosition(positionMs), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            if (durationMs > 0) Text("-" + formatPosition(durationMs - positionMs), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ControlIcon(icon: ImageVector, label: String, large: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = if (large) Modifier.size(64.dp) else Modifier) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = if (large) Modifier.size(44.dp) else Modifier)
    }
}

/** Landscape and hidden system bars while the player is shown; the previous state comes back on exit. */
@Composable
private fun FullscreenLandscape() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        val original = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = original
        }
    }
}
