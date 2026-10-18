package pm.koc.poptv.data.net

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import pm.koc.poptv.data.m3u.M3uEntry
import pm.koc.poptv.data.m3u.M3uParser

class HttpDownloaderTest {

    private lateinit var server: MockWebServer
    private val downloader = HttpDownloader(OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build())

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.close() }

    private suspend fun expectError(path: String): AppError {
        try {
            downloader.download(server.url(path).toString()) { it.readBytes() }
            fail("expected an error")
        } catch (e: AppError) {
            return e
        }
        throw AssertionError()
    }

    @Test
    fun `maps 401 and 403 to unauthorized`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).build())
        assertThat(expectError("/get.php")).isInstanceOf(AppError.Unauthorized::class.java)
        server.enqueue(MockResponse.Builder().code(403).build())
        assertThat((expectError("/get.php") as AppError.Unauthorized).code).isEqualTo(403)
    }

    @Test
    fun `maps 404 and 5xx`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())
        assertThat(expectError("/x")).isInstanceOf(AppError.NotFound::class.java)
        server.enqueue(MockResponse.Builder().code(503).build())
        assertThat((expectError("/x") as AppError.Http).code).isEqualTo(503)
    }

    @Test
    fun `rejects invalid urls without a request`() = runTest {
        try {
            downloader.download("not a url") { it.readBytes() }
            fail()
        } catch (e: AppError) {
            assertThat(e).isInstanceOf(AppError.InvalidUrl::class.java)
        }
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `follows redirects and streams the body into the parser`() = runTest {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", server.url("/real.m3u")).build())
        server.enqueue(
            MockResponse.Builder().code(200).body(
                "#EXTM3U\n#EXTINF:-1 group-title=\"News\",Channel A\nhttp://example.com/a.ts\n#EXTINF:-1,Channel B\nhttp://example.com/b.ts\n",
            ).build(),
        )
        var reported = 0L
        val entries = mutableListOf<M3uEntry>()
        downloader.download(server.url("/get.php").toString(), onBytes = { read, _ -> reported = read }) { input ->
            M3uParser().parse(input) { entries += it }
        }
        assertThat(entries.map { it.title }).containsExactly("Channel A", "Channel B").inOrder()
        assertThat(reported).isGreaterThan(0)
        assertThat(server.requestCount).isEqualTo(2)
    }
}
