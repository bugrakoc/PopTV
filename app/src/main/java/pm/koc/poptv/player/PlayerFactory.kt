package pm.koc.poptv.player

import android.content.Context
import androidx.core.net.toUri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import pm.koc.poptv.core.UrlUtils
import pm.koc.poptv.data.prefs.AppSettings
import pm.koc.poptv.data.prefs.BufferMode
import pm.koc.poptv.data.prefs.DecoderMode
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** What to play and how to request it. */
data class PlayRequest(
    val url: String,
    val title: String,
    val isLive: Boolean,
    val userAgent: String?,
    val referrer: String?,
    val forceHls: Boolean = false,
    val subtitleUrl: String? = null,
)

/** Builds ExoPlayer instances and per-stream media sources from the user's settings. */
@OptIn(UnstableApi::class)
@Singleton
class PlayerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttp: OkHttpClient,
) {

    fun createPlayer(settings: AppSettings, decoderMode: DecoderMode = settings.decoderMode): ExoPlayer {
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(DecoderSelector(decoderMode))
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setTunnelingEnabled(settings.tunneling)
                    .setPreferredAudioLanguage(Locale.getDefault().language)
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true),
            )
        }

        val (minBuffer, maxBuffer, startBuffer, rebufferBuffer, bytes) = when (settings.bufferMode) {
            BufferMode.SMALL -> BufferSpec(15_000, 30_000, 1_500, 3_000, 32 * MB)
            BufferMode.NORMAL -> BufferSpec(30_000, 60_000, 2_500, 5_000, 64 * MB)
            BufferMode.LARGE -> BufferSpec(50_000, 120_000, 3_500, 8_000, 128 * MB)
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuffer, maxBuffer, startBuffer, rebufferBuffer)
            .setTargetBufferBytes(bytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        return ExoPlayer.Builder(context, renderers)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }

    private data class BufferSpec(val min: Int, val max: Int, val start: Int, val rebuffer: Int, val bytes: Int)

    /** Media source with the stream's own User-Agent / Referer headers. */
    fun createMediaSource(request: PlayRequest, defaultUserAgent: String): MediaSource {
        val headers = buildMap {
            request.referrer?.let {
                put("Referer", it)
                put("Origin", it.substringBefore("://") + "://" + it.substringAfter("://").substringBefore('/'))
            }
        }
        val http = OkHttpDataSource.Factory(okHttp)
            .setUserAgent(request.userAgent ?: defaultUserAgent)
            .setDefaultRequestProperties(headers)
        val dataSource = DefaultDataSource.Factory(context, http)
        val errorPolicy = DefaultLoadErrorHandlingPolicy(if (request.isLive) 3 else 6)

        val mimeType = when (contentType(request)) {
            C.CONTENT_TYPE_HLS -> MimeTypes.APPLICATION_M3U8
            C.CONTENT_TYPE_DASH -> MimeTypes.APPLICATION_MPD
            else -> null
        }
        val item = MediaItem.Builder()
            .setUri(request.url)
            .setMediaId(request.url)
            .setMimeType(mimeType)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(request.title).build())
            .apply {
                request.subtitleUrl?.let { sub ->
                    val subMime = when (UrlUtils.extension(sub)) {
                        "vtt" -> MimeTypes.TEXT_VTT
                        "ssa", "ass" -> MimeTypes.TEXT_SSA
                        "ttml", "xml", "dfxp" -> MimeTypes.APPLICATION_TTML
                        else -> MimeTypes.APPLICATION_SUBRIP
                    }
                    setSubtitleConfigurations(
                        listOf(
                            MediaItem.SubtitleConfiguration.Builder(sub.toUri())
                                .setMimeType(subMime)
                                .setSelectionFlags(0)
                                .build(),
                        ),
                    )
                }
            }
            .build()

        // IPTV MPEG-TS streams often start without an IDR frame and lack access unit delimiters.
        val extractors = DefaultExtractorsFactory()
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
            )
            .setConstantBitrateSeekingEnabled(true)
        return DefaultMediaSourceFactory(dataSource, extractors)
            .setLoadErrorHandlingPolicy(errorPolicy)
            .createMediaSource(item)
    }

    private fun contentType(request: PlayRequest): Int {
        if (request.forceHls) return C.CONTENT_TYPE_HLS
        val url = request.url.lowercase(Locale.ROOT)
        return when {
            UrlUtils.extension(url) == "m3u8" || url.contains(".m3u8") || url.contains("/hls/") && !url.endsWith(".ts") -> C.CONTENT_TYPE_HLS
            UrlUtils.extension(url) == "mpd" || url.contains(".mpd") -> C.CONTENT_TYPE_DASH
            else -> C.CONTENT_TYPE_OTHER
        }
    }

    private companion object {
        const val MB = 1024 * 1024
    }
}
