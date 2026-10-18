package pm.koc.poptv.player

import androidx.media3.common.Format
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.prefs.DecoderMode
import pm.koc.poptv.data.repo.Programme

sealed interface PlayerStatus {
    data object Loading : PlayerStatus
    data object Buffering : PlayerStatus
    data object Playing : PlayerStatus
    data object Paused : PlayerStatus
    data class Reconnecting(val attempt: Int, val max: Int) : PlayerStatus
    data class Failed(val kind: PlaybackErrorKind) : PlayerStatus
    data object Ended : PlayerStatus
}

enum class TrackKind { AUDIO, TEXT, VIDEO }

data class TrackOption(
    val kind: TrackKind,
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String?,
    val label: String?,
    val mimeType: String?,
    val codecs: String?,
    val channels: Int,
    val bitrate: Int,
    val width: Int,
    val height: Int,
    val selected: Boolean,
    val supported: Boolean,
    val forced: Boolean,
)

data class StreamInfo(
    val videoFormat: Format? = null,
    val audioFormat: Format? = null,
    val videoDecoder: String? = null,
    val audioDecoder: String? = null,
    val droppedFrames: Int = 0,
    val bandwidthBps: Long = 0,
    val decoderMode: DecoderMode = DecoderMode.HARDWARE,
    val tunneling: Boolean = false,
    val url: String = "",
)

enum class ResizeMode { FIT, FILL, ZOOM }

data class UpNext(val episode: StreamEntity, val secondsLeft: Int?)

data class PlayerUiState(
    val stream: StreamEntity? = null,
    val isLive: Boolean = true,
    val isPlaying: Boolean = false,
    val status: PlayerStatus = PlayerStatus.Loading,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val programmes: List<Programme> = emptyList(),
    val bannerVisible: Boolean = false,
    val numberInput: String = "",
    val numberNotFound: Boolean = false,
    val audioTracks: List<TrackOption> = emptyList(),
    val textTracks: List<TrackOption> = emptyList(),
    val subtitlesOff: Boolean = true,
    val speed: Float = 1f,
    val resizeMode: ResizeMode = ResizeMode.FIT,
    val info: StreamInfo = StreamInfo(),
    val upNext: UpNext? = null,
    val isFavorite: Boolean = false,
    val finished: Boolean = false,
    val zapContext: String? = null,
)
