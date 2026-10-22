@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package pm.koc.poptv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import pm.koc.poptv.R
import pm.koc.poptv.player.PlaybackErrorKind
import pm.koc.poptv.player.TrackKind
import pm.koc.poptv.player.TrackOption
import java.util.Locale

/** Human-readable codec name for a MIME type (technical names, not translated). */
fun codecName(mime: String?): String? = when (mime) {
    null -> null
    MimeTypes.VIDEO_H265 -> "HEVC / H.265"
    MimeTypes.VIDEO_H264 -> "H.264 / AVC"
    MimeTypes.VIDEO_VP9 -> "VP9"
    MimeTypes.VIDEO_AV1 -> "AV1"
    MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
    MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
    MimeTypes.VIDEO_MP4V -> "MPEG-4"
    MimeTypes.VIDEO_VC1 -> "VC-1"
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_AC3 -> "AC-3"
    MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "E-AC-3"
    MimeTypes.AUDIO_AC4 -> "AC-4"
    MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS -> "DTS"
    MimeTypes.AUDIO_TRUEHD -> "TrueHD"
    MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L2 -> "MP3/MP2"
    MimeTypes.AUDIO_OPUS -> "Opus"
    MimeTypes.AUDIO_VORBIS -> "Vorbis"
    MimeTypes.AUDIO_FLAC -> "FLAC"
    MimeTypes.TEXT_VTT -> "WebVTT"
    MimeTypes.APPLICATION_SUBRIP -> "SRT"
    MimeTypes.TEXT_SSA -> "SSA"
    MimeTypes.APPLICATION_TTML -> "TTML"
    MimeTypes.APPLICATION_CEA608, MimeTypes.APPLICATION_CEA708 -> "CEA-608/708"
    MimeTypes.APPLICATION_DVBSUBS -> "DVB"
    MimeTypes.APPLICATION_PGS -> "PGS"
    else -> mime.substringAfter('/').uppercase(Locale.ROOT)
}

/** Software decoders on Android use these name prefixes. */
fun isSoftwareDecoder(name: String): Boolean {
    val n = name.lowercase(Locale.ROOT)
    return n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.startsWith("ffmpeg") ||
        (!n.startsWith("omx.") && !n.startsWith("c2."))
}

@Composable
fun hdrLabel(format: Format?): String {
    if (format == null) return "—"
    if (format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION) return "Dolby Vision"
    return when (format.colorInfo?.colorTransfer) {
        C.COLOR_TRANSFER_ST2084 -> "HDR10 (PQ)"
        C.COLOR_TRANSFER_HLG -> "HLG"
        else -> stringResource(R.string.player_info_sdr)
    }
}

@Composable
fun channelLabel(channels: Int): String? = when {
    channels <= 0 -> null
    channels == 1 -> stringResource(R.string.player_audio_mono)
    channels == 2 -> stringResource(R.string.player_audio_stereo)
    channels == 6 -> "5.1"
    channels == 8 -> "7.1"
    else -> pluralStringResource(R.plurals.player_audio_channels, channels, channels)
}

fun languageName(code: String?): String? {
    if (code.isNullOrBlank()) return null
    val locale = Locale.forLanguageTag(code)
    val name = locale.getDisplayLanguage(Locale.getDefault())
    return name.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        ?: code
}

@Composable
fun TrackOption.displayLabel(index: Int): String {
    val parts = ArrayList<String>()
    val lang = languageName(language)
    if (lang != null) parts += lang
    if (!label.isNullOrBlank() && label != lang) parts += label
    if (parts.isEmpty()) parts += stringResource(R.string.player_track_n, index + 1)
    if (kind == TrackKind.AUDIO) channelLabel(channels)?.let { parts += it }
    codecName(mimeType)?.let { parts += it }
    if (forced) parts += stringResource(R.string.player_track_forced)
    if (!supported) parts += stringResource(R.string.player_track_unsupported)
    return parts.joinToString(" · ")
}

@Composable
fun PlaybackErrorKind.message(): String = stringResource(
    when (this) {
        PlaybackErrorKind.NETWORK -> R.string.player_error_network
        PlaybackErrorKind.AUTH -> R.string.player_error_auth
        PlaybackErrorKind.NOT_FOUND -> R.string.player_error_not_found
        PlaybackErrorKind.HTTP -> R.string.player_error_http
        PlaybackErrorKind.DECODER -> R.string.player_error_decoder
        PlaybackErrorKind.UNSUPPORTED_FORMAT -> R.string.player_error_unsupported
        PlaybackErrorKind.BEHIND_LIVE_WINDOW -> R.string.player_error_network
        PlaybackErrorKind.AUDIO -> R.string.player_error_audio
        PlaybackErrorKind.UNKNOWN -> R.string.player_error_unknown
    },
)

fun formatBitrate(bps: Long): String = when {
    bps <= 0 -> "—"
    bps >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f Mbps", bps / 1_000_000.0)
    else -> String.format(Locale.getDefault(), "%d kbps", bps / 1000)
}
