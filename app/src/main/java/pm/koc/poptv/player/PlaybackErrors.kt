package pm.koc.poptv.player

import androidx.media3.common.PlaybackException

enum class PlaybackErrorKind { NETWORK, AUTH, NOT_FOUND, HTTP, DECODER, UNSUPPORTED_FORMAT, BEHIND_LIVE_WINDOW, AUDIO, UNKNOWN }

/** Maps Media3 error codes to user-facing categories and recovery strategies. */
object PlaybackErrors {

    fun classify(errorCode: Int, httpCode: Int? = null): PlaybackErrorKind = when (errorCode) {
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> PlaybackErrorKind.BEHIND_LIVE_WINDOW
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> when (httpCode) {
            401, 403 -> PlaybackErrorKind.AUTH
            404, 410 -> PlaybackErrorKind.NOT_FOUND
            else -> PlaybackErrorKind.HTTP
        }
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackErrorKind.NOT_FOUND
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        PlaybackException.ERROR_CODE_TIMEOUT,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> PlaybackErrorKind.NETWORK
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED -> PlaybackErrorKind.DECODER
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> PlaybackErrorKind.UNSUPPORTED_FORMAT
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> PlaybackErrorKind.AUDIO
        else -> PlaybackErrorKind.UNKNOWN
    }

    /** Whether waiting and reconnecting can plausibly help. */
    fun isRetryable(kind: PlaybackErrorKind): Boolean = when (kind) {
        PlaybackErrorKind.NETWORK, PlaybackErrorKind.HTTP, PlaybackErrorKind.AUDIO, PlaybackErrorKind.UNKNOWN,
        PlaybackErrorKind.BEHIND_LIVE_WINDOW -> true
        // Expired tokens / overloaded providers sometimes answer 403/404 once; a couple of retries are allowed.
        PlaybackErrorKind.AUTH, PlaybackErrorKind.NOT_FOUND -> true
        PlaybackErrorKind.DECODER, PlaybackErrorKind.UNSUPPORTED_FORMAT -> false
    }

    /** Maximum retries for a kind (the general policy may allow more). */
    fun maxRetries(kind: PlaybackErrorKind): Int = when (kind) {
        PlaybackErrorKind.AUTH, PlaybackErrorKind.NOT_FOUND -> 2
        else -> Int.MAX_VALUE
    }
}
