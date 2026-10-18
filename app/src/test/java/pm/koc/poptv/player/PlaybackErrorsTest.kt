package pm.koc.poptv.player

import androidx.media3.common.PlaybackException
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackErrorsTest {
    @Test
    fun `classifies common errors`() {
        assertThat(PlaybackErrors.classify(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 403)).isEqualTo(PlaybackErrorKind.AUTH)
        assertThat(PlaybackErrors.classify(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 404)).isEqualTo(PlaybackErrorKind.NOT_FOUND)
        assertThat(PlaybackErrors.classify(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 502)).isEqualTo(PlaybackErrorKind.HTTP)
        assertThat(PlaybackErrors.classify(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)).isEqualTo(PlaybackErrorKind.NETWORK)
        assertThat(PlaybackErrors.classify(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)).isEqualTo(PlaybackErrorKind.DECODER)
        assertThat(PlaybackErrors.classify(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)).isEqualTo(PlaybackErrorKind.UNSUPPORTED_FORMAT)
        assertThat(PlaybackErrors.classify(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW)).isEqualTo(PlaybackErrorKind.BEHIND_LIVE_WINDOW)
    }

    @Test
    fun `retry rules`() {
        assertThat(PlaybackErrors.isRetryable(PlaybackErrorKind.NETWORK)).isTrue()
        assertThat(PlaybackErrors.isRetryable(PlaybackErrorKind.DECODER)).isFalse()
        assertThat(PlaybackErrors.maxRetries(PlaybackErrorKind.AUTH)).isEqualTo(2)
    }
}
