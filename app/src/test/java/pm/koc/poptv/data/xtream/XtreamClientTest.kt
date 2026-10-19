package pm.koc.poptv.data.xtream

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import pm.koc.poptv.data.net.AppError
import pm.koc.poptv.data.net.HttpDownloader

class XtreamClientTest {

    private lateinit var server: MockWebServer
    private lateinit var account: XtreamAccount
    private val client = XtreamClient(HttpDownloader(OkHttpClient()), Json { ignoreUnknownKeys = true; isLenient = true })

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        account = XtreamAccount.fromPlaylistUrl(server.url("/get.php?username=u&password=p&type=m3u_plus").toString())!!
    }

    @After fun tearDown() { server.close() }

    private fun enqueue(body: String) = server.enqueue(MockResponse.Builder().body(body).build())

    private suspend fun loginError(): AppError {
        try {
            client.login(account)
            fail("expected an error")
        } catch (e: AppError) {
            return e
        }
        throw AssertionError()
    }

    @Test
    fun `login succeeds for active accounts`() = runTest {
        enqueue("""{"user_info":{"auth":1,"status":"Active"},"server_info":{}}""")
        assertThat(client.login(account).isActive).isTrue()
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/player_api.php")
    }

    @Test
    fun `login reports wrong credentials and inactive accounts`() = runTest {
        enqueue("""{"user_info":{"auth":0}}""")
        assertThat((loginError() as AppError.AccountRejected).status).isNull()
        enqueue("""{"user_info":{"auth":1,"status":"Expired"}}""")
        assertThat((loginError() as AppError.AccountRejected).status).isEqualTo("Expired")
    }

    @Test
    fun `login treats non json answers as invalid`() = runTest {
        enqueue("<html>error code: 520</html>")
        assertThat(loginError()).isInstanceOf(AppError.InvalidPlaylist::class.java)
        enqueue("""{"something":"else"}""")
        assertThat(loginError()).isInstanceOf(AppError.InvalidPlaylist::class.java)
    }

    @Test
    fun `streams list items one by one`() = runTest {
        enqueue("""[{"stream_id":1,"name":"A"},{"stream_id":2,"name":"B"}]""")
        val names = mutableListOf<String>()
        val bytes = client.list(account, XtreamKind.LIVE, onBytes = {}) { names += XtreamJson.stream(it)!!.name }
        assertThat(names).containsExactly("A", "B").inOrder()
        assertThat(bytes).isGreaterThan(0L)
        assertThat(server.takeRequest().url.queryParameter("action")).isEqualTo("get_live_streams")
    }

    @Test
    fun `empty answers yield no items`() = runTest {
        for (body in listOf("[]", "{}", "")) {
            enqueue(body)
            var count = 0
            client.list(account, XtreamKind.MOVIE, onBytes = {}) { if (XtreamJson.stream(it) != null) count++ }
            assertThat(count).isEqualTo(0)
        }
    }

    @Test
    fun `broken lists are invalid`() = runTest {
        enqueue("""[{"stream_id":1,"name":"A"}, <oops""")
        try {
            client.list(account, XtreamKind.MOVIE, onBytes = {}) {}
            fail("expected an error")
        } catch (e: AppError) {
            assertThat(e).isInstanceOf(AppError.InvalidPlaylist::class.java)
        }
    }

    @Test
    fun `uses the right actions`() = runTest {
        enqueue("""[{"category_id":"1","category_name":"Dizi"}]""")
        assertThat(client.categories(account, XtreamKind.SERIES)).containsExactly("1", "Dizi")
        assertThat(server.takeRequest().url.queryParameter("action")).isEqualTo("get_series_categories")

        enqueue("[]")
        client.list(account, XtreamKind.SERIES, onBytes = {}) {}
        assertThat(server.takeRequest().url.queryParameter("action")).isEqualTo("get_series")

        enqueue("""{"episodes":{"1":[{"id":"5","episode_num":1}]}}""")
        assertThat(client.episodes(account, "77").single().id).isEqualTo("5")
        val request = server.takeRequest().url
        assertThat(request.queryParameter("action")).isEqualTo("get_series_info")
        assertThat(request.queryParameter("series_id")).isEqualTo("77")
    }
}
