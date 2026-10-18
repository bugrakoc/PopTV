package pm.koc.poptv.ui.player

import android.graphics.Typeface
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import pm.koc.poptv.R
import pm.koc.poptv.data.prefs.AppSettings
import pm.koc.poptv.data.prefs.SubtitleColor
import pm.koc.poptv.data.prefs.SubtitleSize
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.domain.SeekAccelerator
import pm.koc.poptv.player.PlayerStatus
import pm.koc.poptv.player.PlayerUiState
import pm.koc.poptv.player.ResizeMode
import pm.koc.poptv.ui.components.Choice
import pm.koc.poptv.ui.components.ChoiceDialog
import pm.koc.poptv.ui.components.formatPosition
import pm.koc.poptv.ui.components.safeRequestFocus
import pm.koc.poptv.ui.live.ChannelBrowser
import pm.koc.poptv.ui.live.ChannelBrowserViewModel

private enum class Overlay { NONE, CHANNELS, CONTROLS }
private enum class PlayerDialog { AUDIO, SUBTITLES, SPEED, ASPECT }

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
    browser: ChannelBrowserViewModel = hiltViewModel(),
) {
    val player by viewModel.player.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val showOriginal by browser.showOriginalNames.collectAsStateWithLifecycle()

    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var dialog by remember { mutableStateOf<PlayerDialog?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var pendingSeek by remember { mutableStateOf<Long?>(null) }
    var seekPreviewVisible by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val upNextFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }

    val failed = ui.status is PlayerStatus.Failed
    val title = ui.stream?.let { if (showOriginal) it.name else it.displayName }.orEmpty()

    // ---- Lifecycle: pause/stop in background, resume on return ----
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

    // ---- Focus management ----
    LaunchedEffect(overlay, failed, ui.upNext != null, dialog) {
        if (dialog != null) return@LaunchedEffect
        delay(50)
        when {
            overlay == Overlay.CONTROLS -> playFocus.safeRequestFocus()
            overlay == Overlay.CHANNELS -> Unit // the browser focuses the current channel itself
            ui.upNext != null -> upNextFocus.safeRequestFocus()
            failed -> retryFocus.safeRequestFocus()
            else -> rootFocus.safeRequestFocus()
        }
    }
    // Auto-hide controls / channel list after inactivity.
    LaunchedEffect(overlay, lastInteraction, ui.isPlaying, dialog) {
        if (dialog != null) return@LaunchedEffect
        when (overlay) {
            Overlay.CONTROLS -> if (ui.isPlaying) { delay(6_000); overlay = Overlay.NONE }
            Overlay.CHANNELS -> { delay(20_000); overlay = Overlay.NONE }
            Overlay.NONE -> Unit
        }
    }
    LaunchedEffect(seekPreviewVisible, pendingSeek) {
        if (seekPreviewVisible && pendingSeek == null) { delay(1_500); seekPreviewVisible = false }
    }

    BackHandler {
        when {
            overlay != Overlay.NONE -> overlay = Overlay.NONE
            showInfo -> showInfo = false
            ui.numberInput.isNotEmpty() -> viewModel.clearNumber()
            ui.upNext != null && ui.status != PlayerStatus.Ended -> viewModel.dismissUpNext()
            else -> {
                viewModel.saveNow()
                onBack()
            }
        }
    }

    fun handleKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        val native = event.nativeKeyEvent
        val code = native.keyCode
        val down = event.type == KeyEventType.KeyDown
        val up = event.type == KeyEventType.KeyUp

        // Media keys work in every mode.
        if (down) when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> { viewModel.togglePlayPause(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { if (!ui.isPlaying) viewModel.togglePlayPause(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (ui.isPlaying) viewModel.togglePlayPause(); return true }
            KeyEvent.KEYCODE_INFO -> { if (ui.isLive) viewModel.showBanner() else showInfo = !showInfo; return true }
        }
        if (overlay != Overlay.NONE || dialog != null) return false

        if (ui.isLive) {
            if (!down) return false
            val nextOnDown = !settings.invertChannelKeys
            when (code) {
                KeyEvent.KEYCODE_DPAD_UP -> { viewModel.zap(forward = !nextOnDown); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { viewModel.zap(forward = nextOnDown); return true }
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_PAGE_UP -> { viewModel.zap(true); return true }
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_PAGE_DOWN -> { viewModel.zap(false); return true }
                KeyEvent.KEYCODE_LAST_CHANNEL -> { viewModel.previousChannel(); return true }
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> { viewModel.digit(code - KeyEvent.KEYCODE_0); return true }
                in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> { viewModel.digit(code - KeyEvent.KEYCODE_NUMPAD_0); return true }
            }
            if (failed || ui.upNext != null) return false // let the focused buttons handle OK / left / right
            return when (code) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (ui.numberInput.isNotEmpty()) viewModel.commitNumber() else overlay = Overlay.CHANNELS
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_GUIDE -> {
                    overlay = Overlay.CHANNELS
                    true
                }
                else -> false
            }
        }

        // ---- VOD ----
        if (failed || ui.upNext != null) return false
        val seekForward = code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        val seekBack = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_MEDIA_REWIND
        if (seekForward || seekBack) {
            if (down) {
                pendingSeek = SeekAccelerator.target(pendingSeek ?: ui.positionMs, seekForward, native.repeatCount, ui.durationMs)
                seekPreviewVisible = true
            } else if (up) {
                pendingSeek?.let { viewModel.seekTo(it) }
                pendingSeek = null
            }
            return true
        }
        if (!down) return false
        return when (code) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MENU -> {
                overlay = Overlay.CONTROLS
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> { viewModel.playNextEpisode(); true }
            else -> false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent {
                lastInteraction = System.currentTimeMillis()
                false
            }
            .onKeyEvent(::handleKey)
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        // ---- Video surface ----
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(true)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
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

        // ---- Status (loading / reconnecting) ----
        if (!failed && overlay == Overlay.NONE) {
            CenterStatus(ui.status, Modifier.align(Alignment.Center))
        }

        // ---- Live banner ----
        val stream = ui.stream
        AnimatedVisibility(
            visible = ui.isLive && stream != null && overlay == Overlay.NONE && (ui.bannerVisible || failed),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 48.dp, vertical = 32.dp),
        ) {
            if (stream != null) ZapBanner(stream, title, ui.programmes, ui.isFavorite, ui.status)
        }

        if (ui.numberInput.isNotEmpty()) {
            NumberEntry(ui.numberInput, ui.numberNotFound, Modifier.align(Alignment.TopEnd).padding(40.dp))
        }

        if (showInfo) {
            StreamInfoPanel(ui.info, Modifier.align(Alignment.TopEnd).padding(32.dp))
        }

        // ---- VOD quick seek preview (controls hidden) ----
        if (!ui.isLive && overlay == Overlay.NONE && seekPreviewVisible) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(BottomScrim)
                    .padding(horizontal = 48.dp, vertical = 32.dp),
            ) {
                Scrubber(ui.positionMs, ui.durationMs, ui.bufferedMs, onSeek = viewModel::seekTo, pendingExternal = pendingSeek)
            }
        }

        // ---- VOD controls ----
        if (!ui.isLive && overlay == Overlay.CONTROLS) {
            VodControls(
                ui = ui,
                title = title,
                playFocus = playFocus,
                onPlayPause = viewModel::togglePlayPause,
                onSeek = viewModel::seekTo,
                onDialog = { dialog = it },
                onInfo = { showInfo = !showInfo },
                onNext = viewModel::playNextEpisode,
                onFavorite = viewModel::toggleFavorite,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // ---- Live channel overlay ----
        if (ui.isLive && overlay == Overlay.CHANNELS) {
            Row(
                Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(Color(0xF20B0A14), Color(0xE00B0A14), Color.Transparent))),
            ) {
                Column(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.72f)
                        .padding(start = 40.dp, top = 24.dp, bottom = 16.dp, end = 16.dp),
                ) {
                    LiveActions(
                        ui = ui,
                        onFavorite = viewModel::toggleFavorite,
                        onLastChannel = { viewModel.previousChannel(); overlay = Overlay.NONE },
                        onDialog = { dialog = it },
                        onInfo = { showInfo = !showInfo },
                    )
                    Spacer(Modifier.height(12.dp))
                    ChannelBrowser(
                        viewModel = browser,
                        currentKey = stream?.key,
                        initialFocusKey = stream?.key,
                        focusChannelsFirst = true,
                        categoryWidth = 240.dp,
                        onChannelClick = { s, ctx ->
                            viewModel.playChannel(s, ctx)
                            overlay = Overlay.NONE
                        },
                    )
                }
            }
        }

        // ---- Up next ----
        val upNext = ui.upNext
        if (upNext != null && overlay == Overlay.NONE) {
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(40.dp)
                    .widthIn(max = 420.dp)
                    .background(OverlayBackground, OverlayShape)
                    .padding(20.dp),
            ) {
                Text(stringResource(R.string.player_up_next), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(episodeTitle(upNext.episode), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                upNext.secondsLeft?.let {
                    Text(stringResource(R.string.player_up_next_in, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = viewModel::playNextEpisode, modifier = Modifier.focusRequester(upNextFocus)) {
                        Text(stringResource(R.string.player_play_now))
                    }
                    OutlinedButton(onClick = {
                        if (ui.status == PlayerStatus.Ended) onBack() else viewModel.dismissUpNext()
                    }) {
                        Text(stringResource(if (ui.status == PlayerStatus.Ended) R.string.action_close else R.string.action_cancel))
                    }
                }
            }
        }

        // ---- Failure ----
        val status = ui.status
        if (status is PlayerStatus.Failed && overlay == Overlay.NONE) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 560.dp)
                    .background(OverlayBackground, OverlayShape)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.player_failed), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(status.kind.message(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (ui.isLive) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.player_failed_zap_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = viewModel::retryNow, modifier = Modifier.focusRequester(retryFocus)) {
                        Text(stringResource(R.string.action_retry))
                    }
                    if (ui.isLive) {
                        OutlinedButton(onClick = { overlay = Overlay.CHANNELS }) { Text(stringResource(R.string.player_channels)) }
                    }
                    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                }
            }
        }
    }

    // ---- Dialogs ----
    when (dialog) {
        PlayerDialog.AUDIO -> {
            val choices = buildList<Choice<Int>> {
                add(Choice(-1, stringResource(R.string.player_track_auto)))
                ui.audioTracks.forEachIndexed { i, t -> add(Choice(i, t.displayLabel(i))) }
            }
            ChoiceDialog(
                title = stringResource(R.string.player_audio),
                choices = choices,
                selected = ui.audioTracks.indexOfFirst { it.selected }.takeIf { it >= 0 } ?: -1,
                onSelect = { i ->
                    if (i < 0) viewModel.selectAutoAudio() else viewModel.selectTrack(ui.audioTracks[i])
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }
        PlayerDialog.SUBTITLES -> {
            val choices = buildList<Choice<Int>> {
                add(Choice(-1, stringResource(R.string.player_subtitles_off)))
                ui.textTracks.forEachIndexed { i, t -> add(Choice(i, t.displayLabel(i))) }
            }
            ChoiceDialog(
                title = stringResource(R.string.player_subtitles),
                choices = choices,
                selected = if (ui.subtitlesOff) -1 else ui.textTracks.indexOfFirst { it.selected },
                onSelect = { i ->
                    viewModel.selectTrack(if (i < 0) null else ui.textTracks[i])
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }
        PlayerDialog.SPEED -> ChoiceDialog(
            title = stringResource(R.string.player_speed),
            choices = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).map {
                Choice(it, if (it == 1f) stringResource(R.string.player_speed_normal) else "${it}×")
            },
            selected = ui.speed,
            onSelect = { viewModel.setSpeed(it); dialog = null },
            onDismiss = { dialog = null },
        )
        PlayerDialog.ASPECT -> ChoiceDialog(
            title = stringResource(R.string.player_aspect),
            choices = listOf(
                Choice(ResizeMode.FIT, stringResource(R.string.player_aspect_fit)),
                Choice(ResizeMode.FILL, stringResource(R.string.player_aspect_fill)),
                Choice(ResizeMode.ZOOM, stringResource(R.string.player_aspect_zoom)),
            ),
            selected = ui.resizeMode,
            onSelect = { viewModel.setResizeMode(it); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun episodeTitle(stream: pm.koc.poptv.data.db.StreamEntity): String {
    val s = stream.season
    val e = stream.episode
    return if (s != null && e != null) stringResource(R.string.episode_code, s, e) + " · " + stream.displayName else stream.displayName
}

@Composable
private fun VodControls(
    ui: PlayerUiState,
    title: String,
    playFocus: FocusRequester,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onDialog: (PlayerDialog) -> Unit,
    onInfo: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stream = ui.stream
    Column(
        modifier
            .fillMaxWidth()
            .background(BottomScrim)
            .padding(horizontal = 48.dp, vertical = 28.dp),
    ) {
        if (stream?.type == ContentType.SERIES && stream.season != null && stream.episode != null) {
            Text(stream.seriesName.orEmpty(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.episode_code, stream.season, stream.episode) + " · " + title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(14.dp))
        Scrubber(ui.positionMs, ui.durationMs, ui.bufferedMs, onSeek = onSeek)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
            ControlButton(
                if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                stringResource(if (ui.isPlaying) R.string.player_pause else R.string.player_play),
                Modifier.focusRequester(playFocus),
                onPlayPause,
            )
            ControlButton(Icons.Filled.Replay10, stringResource(R.string.player_back_10)) { onSeek((ui.positionMs - 10_000).coerceAtLeast(0)) }
            ControlButton(Icons.Filled.Forward10, stringResource(R.string.player_forward_10)) { onSeek(ui.positionMs + 10_000) }
            if (stream?.type == ContentType.SERIES) {
                ControlButton(Icons.Filled.SkipNext, stringResource(R.string.player_next_episode), onClick = onNext)
            }
            ControlButton(Icons.Filled.Audiotrack, stringResource(R.string.player_audio)) { onDialog(PlayerDialog.AUDIO) }
            ControlButton(Icons.Filled.Subtitles, stringResource(R.string.player_subtitles)) { onDialog(PlayerDialog.SUBTITLES) }
            ControlButton(Icons.Filled.Speed, stringResource(R.string.player_speed)) { onDialog(PlayerDialog.SPEED) }
            ControlButton(Icons.Filled.AspectRatio, stringResource(R.string.player_aspect)) { onDialog(PlayerDialog.ASPECT) }
            ControlButton(if (ui.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, stringResource(R.string.favorite), onClick = onFavorite)
            ControlButton(Icons.Filled.Info, stringResource(R.string.player_stream_info), onClick = onInfo)
            Spacer(Modifier.weight(1f))
            if (ui.durationMs > 0) {
                Text(
                    "${formatPosition(ui.positionMs)} / ${formatPosition(ui.durationMs)}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun LiveActions(
    ui: PlayerUiState,
    onFavorite: () -> Unit,
    onLastChannel: () -> Unit,
    onDialog: (PlayerDialog) -> Unit,
    onInfo: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        ControlButton(if (ui.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, stringResource(R.string.favorite), onClick = onFavorite)
        ControlButton(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.live_last_channel), onClick = onLastChannel)
        ControlButton(Icons.Filled.Audiotrack, stringResource(R.string.player_audio)) { onDialog(PlayerDialog.AUDIO) }
        ControlButton(Icons.Filled.Subtitles, stringResource(R.string.player_subtitles)) { onDialog(PlayerDialog.SUBTITLES) }
        ControlButton(Icons.Filled.AspectRatio, stringResource(R.string.player_aspect)) { onDialog(PlayerDialog.ASPECT) }
        ControlButton(Icons.Filled.Info, stringResource(R.string.player_stream_info), onClick = onInfo)
    }
}

/** Icon button that shows its label while focused. */
@Composable
private fun ControlButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        IconButton(onClick = onClick, modifier = modifier, interactionSource = interaction) {
            Icon(icon, contentDescription = label)
        }
        Text(
            if (focused) label else "",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(UnstableApi::class)
private fun SubtitleView.applyStyle(settings: AppSettings) {
    val fg = when (settings.subtitleColor) {
        SubtitleColor.WHITE -> android.graphics.Color.WHITE
        SubtitleColor.YELLOW -> android.graphics.Color.YELLOW
    }
    val bg = if (settings.subtitleBackground) 0x99000000.toInt() else android.graphics.Color.TRANSPARENT
    setStyle(
        CaptionStyleCompat(
            fg,
            bg,
            android.graphics.Color.TRANSPARENT,
            if (settings.subtitleBackground) CaptionStyleCompat.EDGE_TYPE_NONE else CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            android.graphics.Color.BLACK,
            Typeface.DEFAULT_BOLD,
        ),
    )
    val scale = when (settings.subtitleSize) {
        SubtitleSize.SMALL -> 0.8f
        SubtitleSize.NORMAL -> 1f
        SubtitleSize.LARGE -> 1.25f
        SubtitleSize.XLARGE -> 1.5f
    }
    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale)
    setApplyEmbeddedStyles(false)
}
