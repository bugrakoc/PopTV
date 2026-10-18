package pm.koc.poptv.ui.player

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import pm.koc.poptv.R
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.repo.Programme
import pm.koc.poptv.domain.SeekAccelerator
import pm.koc.poptv.player.PlayerStatus
import pm.koc.poptv.player.StreamInfo
import pm.koc.poptv.ui.components.ProgressBar
import pm.koc.poptv.ui.components.RemoteImage
import pm.koc.poptv.ui.components.formatPosition
import pm.koc.poptv.ui.components.rememberTimeFormatter
import pm.koc.poptv.ui.theme.AccentWarm

val OverlayShape = RoundedCornerShape(14.dp)
val OverlayBackground = Color(0xE6101018)

/** Live channel banner: logo, number, name, now/next with progress. */
@Composable
fun ZapBanner(
    stream: StreamEntity,
    title: String,
    programmes: List<Programme>,
    isFavorite: Boolean,
    status: PlayerStatus,
    modifier: Modifier = Modifier,
) {
    val time = rememberTimeFormatter()
    val now = System.currentTimeMillis()
    val current = programmes.firstOrNull { it.isLive(now) }
    val next = programmes.firstOrNull { it.start >= (current?.end ?: now) }
    Row(
        modifier
            .fillMaxWidth()
            .background(OverlayBackground, OverlayShape)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        RemoteImage(
            url = stream.logo,
            fallbackText = title,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(width = 132.dp, height = 80.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (stream.number > 0) {
                    Text(stream.number.toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                }
                Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (isFavorite) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.favorite), tint = AccentWarm, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                StatusChip(status)
            }
            Spacer(Modifier.height(8.dp))
            if (current != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(time.range(current.start, current.end), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(current.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                ProgressBar(current.progress(now), height = 4.dp)
                if (next != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.epg_next_format, time.time(next.start), next.title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    stringResource(if (stream.epgChannelId == null) R.string.epg_no_guide_for_channel else R.string.epg_no_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun StatusChip(status: PlayerStatus) {
    val text = when (status) {
        PlayerStatus.Loading, PlayerStatus.Buffering -> stringResource(R.string.player_buffering)
        is PlayerStatus.Reconnecting -> stringResource(R.string.player_reconnecting, status.attempt, status.max)
        is PlayerStatus.Failed -> stringResource(R.string.player_failed)
        else -> return
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Big channel number while typing digits. */
@Composable
fun NumberEntry(input: String, notFound: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(OverlayBackground, OverlayShape)
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(input, style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp, fontWeight = FontWeight.Bold))
        if (notFound) {
            Text(stringResource(R.string.live_number_not_found), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Centered loading / reconnecting indicator (no black screen without feedback). */
@Composable
fun CenterStatus(status: PlayerStatus, modifier: Modifier = Modifier) {
    val text = when (status) {
        PlayerStatus.Loading -> stringResource(R.string.player_loading)
        PlayerStatus.Buffering -> stringResource(R.string.player_buffering)
        is PlayerStatus.Reconnecting -> stringResource(R.string.player_reconnecting, status.attempt, status.max)
        else -> return
    }
    Column(
        modifier
            .widthIn(min = 260.dp)
            .background(OverlayBackground, OverlayShape)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(14.dp))
        ProgressBar(null, modifier = Modifier.width(200.dp))
    }
}

/**
 * Seek bar. When focused, left/right seek with acceleration while held; the target is shown as a preview and
 * committed when the key is released.
 */
@Composable
fun Scrubber(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    pendingExternal: Long? = null,
) {
    var pending by remember { mutableStateOf<Long?>(null) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shown = pending ?: pendingExternal ?: positionMs
    val fraction = if (durationMs > 0) shown.toFloat() / durationMs else 0f

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .onKeyEvent { event ->
                    val code = event.nativeKeyEvent.keyCode
                    if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return@onKeyEvent false
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            pending = SeekAccelerator.target(
                                pending ?: positionMs,
                                forward = code == KeyEvent.KEYCODE_DPAD_RIGHT,
                                repeatCount = event.nativeKeyEvent.repeatCount,
                                durationMs = durationMs,
                            )
                            true
                        }
                        KeyEventType.KeyUp -> {
                            pending?.let(onSeek)
                            pending = null
                            true
                        }
                        else -> false
                    }
                }
                .focusable(interactionSource = interaction)
                .border(
                    BorderStroke(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent),
                    RoundedCornerShape(11.dp),
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxWidth()) {
                ProgressBar(
                    if (durationMs > 0) bufferedMs.toFloat() / durationMs else 0f,
                    height = if (focused) 8.dp else 5.dp,
                    color = Color.White.copy(alpha = 0.35f),
                )
                ProgressBar(fraction, height = if (focused) 8.dp else 5.dp, trackColor = Color.Transparent)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                formatPosition(shown),
                style = MaterialTheme.typography.labelLarge,
                color = if (pending != null || pendingExternal != null) MaterialTheme.colorScheme.primary else Color.White,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (durationMs > 0) "-" + formatPosition(durationMs - shown) else "",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Gradient behind bottom controls so text stays readable on bright video. */
val BottomScrim = Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000), Color(0xF0000000)))
val TopScrim = Brush.verticalGradient(listOf(Color(0xE0000000), Color.Transparent))

@Composable
fun StreamInfoPanel(info: StreamInfo, modifier: Modifier = Modifier) {
    val v = info.videoFormat
    val a = info.audioFormat
    val rows = buildList {
        add(stringResource(R.string.player_info_resolution) to (v?.let {
            buildString {
                append("${it.width}×${it.height}")
                if (it.frameRate > 0) append(String.format(java.util.Locale.getDefault(), " @ %.2f fps", it.frameRate))
            }
        } ?: "—"))
        add(stringResource(R.string.player_info_video_codec) to listOfNotNull(codecName(v?.sampleMimeType), v?.codecs).joinToString(" · ").ifEmpty { "—" })
        add(stringResource(R.string.player_info_hdr) to hdrLabel(v))
        add(stringResource(R.string.player_info_video_decoder) to (info.videoDecoder?.let { name ->
            name + " (" + stringResource(if (isSoftwareDecoder(name)) R.string.player_info_software else R.string.player_info_hardware) + ")"
        } ?: "—"))
        add(stringResource(R.string.player_info_audio) to (a?.let {
            listOfNotNull(codecName(it.sampleMimeType), channelLabel(it.channelCount), if (it.sampleRate > 0) "${it.sampleRate / 1000.0} kHz" else null, it.language).joinToString(" · ")
        } ?: "—"))
        add(stringResource(R.string.player_info_audio_decoder) to (info.audioDecoder ?: stringResource(R.string.player_info_passthrough_or_none)))
        add(stringResource(R.string.player_info_bitrate) to formatBitrate(v?.bitrate?.takeIf { it > 0 }?.toLong() ?: info.bandwidthBps))
        add(stringResource(R.string.player_info_bandwidth) to formatBitrate(info.bandwidthBps))
        add(stringResource(R.string.player_info_dropped) to info.droppedFrames.toString())
        add(stringResource(R.string.player_info_decoder_mode) to stringResource(decoderModeLabel(info.decoderMode)))
        add(stringResource(R.string.player_info_tunneling) to stringResource(if (info.tunneling) R.string.on else R.string.off))
    }
    Column(
        modifier
            .widthIn(max = 560.dp)
            .background(OverlayBackground, OverlayShape)
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.player_stream_info), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        rows.forEach { (label, value) ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(170.dp))
                Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

fun decoderModeLabel(mode: pm.koc.poptv.data.prefs.DecoderMode): Int = when (mode) {
    pm.koc.poptv.data.prefs.DecoderMode.HARDWARE -> R.string.decoder_hardware
    pm.koc.poptv.data.prefs.DecoderMode.SOFTWARE -> R.string.decoder_software
    pm.koc.poptv.data.prefs.DecoderMode.AUTO -> R.string.decoder_auto
}
