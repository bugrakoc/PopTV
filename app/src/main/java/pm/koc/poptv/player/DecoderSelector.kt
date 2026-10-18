package pm.koc.poptv.player

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import pm.koc.poptv.data.prefs.DecoderMode
import java.util.concurrent.ConcurrentHashMap

/**
 * Orders the platform's MediaCodec decoders according to the user's decoder mode. Combined with
 * `setEnableDecoderFallback(true)` ExoPlayer tries the next decoder in this list when one fails to
 * initialize, so "hardware first" still ends up on a software decoder when hardware cannot handle a stream.
 */
@OptIn(UnstableApi::class)
class DecoderSelector(private val mode: DecoderMode) : MediaCodecSelector {

    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean,
    ): List<MediaCodecInfo> {
        val infos = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        val ordered = when (mode) {
            DecoderMode.HARDWARE -> infos.sortedBy { if (it.hardwareAccelerated && !it.softwareOnly) 0 else 1 }
            DecoderMode.SOFTWARE -> infos.sortedBy { if (it.softwareOnly) 0 else 1 }
            DecoderMode.AUTO -> infos
        }
        val logKey = "$mode|$mimeType|$requiresSecureDecoder|$requiresTunnelingDecoder"
        if (logged.add(logKey)) {
            Log.i(
                TAG,
                "Decoders for $mimeType (mode=$mode, tunneling=$requiresTunnelingDecoder): " +
                    ordered.joinToString { "${it.name}${if (it.hardwareAccelerated) "[hw]" else ""}${if (it.softwareOnly) "[sw]" else ""}" }
                        .ifEmpty { "none" },
            )
        }
        return ordered
    }

    private companion object {
        const val TAG = "PopTV-Decoder"
        val logged: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }
}
