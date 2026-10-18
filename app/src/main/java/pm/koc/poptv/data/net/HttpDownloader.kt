package pm.koc.poptv.data.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import pm.koc.poptv.core.UrlUtils
import java.io.FilterInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Streams HTTP downloads to a consumer without buffering the whole body in memory. */
@Singleton
class HttpDownloader @Inject constructor(private val client: OkHttpClient) {

    /**
     * Opens [url] and passes the body stream to [consume] on an IO thread. [onBytes] reports the number of
     * bytes read so far and the content length (-1 if unknown). Errors are mapped to [AppError].
     */
    suspend fun <T> download(
        url: String,
        onBytes: (read: Long, total: Long) -> Unit = { _, _ -> },
        consume: (InputStream) -> T,
    ): T = withContext(Dispatchers.IO) {
        if (!UrlUtils.isValidHttpUrl(url)) throw AppError.InvalidUrl()
        val request = try {
            Request.Builder().url(url.trim()).get().build()
        } catch (_: IllegalArgumentException) {
            throw AppError.InvalidUrl()
        }
        val call: Call = client.newCall(request)
        val handle = coroutineContext.job.invokeOnCompletion { if (it != null) call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} for ${UrlUtils.redact(url)}")
                    throw AppError.fromHttpCode(response.code)
                }
                val body = response.body
                val total = body.contentLength()
                body.byteStream().use { raw ->
                    consume(CountingInputStream(raw) { onBytes(it, total) })
                }
            }
        } catch (e: AppError) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!coroutineContext.job.isActive) throw kotlinx.coroutines.CancellationException("cancelled")
            Log.w(TAG, "Download failed for ${UrlUtils.redact(url)}: ${e.javaClass.simpleName}")
            throw AppError.from(e)
        } finally {
            handle.dispose()
        }
    }

    private companion object {
        const val TAG = "HttpDownloader"
    }
}

/** Reports progress at most every 64 KiB to keep callbacks cheap. */
class CountingInputStream(input: InputStream, private val onCount: (Long) -> Unit) : FilterInputStream(input) {
    private var count = 0L
    private var lastReported = 0L

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) advance(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) advance(n.toLong())
        return n
    }

    private fun advance(n: Long) {
        count += n
        if (count - lastReported >= 64 * 1024) {
            lastReported = count
            onCount(count)
        }
    }

    override fun close() {
        onCount(count)
        super.close()
    }
}
