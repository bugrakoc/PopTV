package pm.koc.poptv.data.xtream

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import pm.koc.poptv.domain.StableKeyGenerator

class XtreamAccountTest {

    @Test
    fun `parses get php links`() {
        val a = XtreamAccount.fromPlaylistUrl("http://host.example:8080/get.php?username=u1&password=p1&type=m3u_plus&output=mpegts")!!
        assertThat(a.baseUrl).isEqualTo("http://host.example:8080/")
        assertThat(a.username).isEqualTo("u1")
        assertThat(a.password).isEqualTo("p1")
        assertThat(a.liveExtension).isEqualTo("ts")
    }

    @Test
    fun `keeps a path prefix and reads hls output`() {
        val a = XtreamAccount.fromPlaylistUrl(" https://host.example/panel/get.php?username=u&password=p&output=m3u8 ")!!
        assertThat(a.baseUrl).isEqualTo("https://host.example/panel/")
        assertThat(a.liveExtension).isEqualTo("m3u8")
        assertThat(XtreamAccount.fromPlaylistUrl("http://h/get.php?username=u&password=p&output=hls")!!.liveExtension).isEqualTo("m3u8")
    }

    @Test
    fun `other links are not xtream accounts`() {
        assertThat(XtreamAccount.fromPlaylistUrl("http://host.example/list.m3u?username=u&password=p")).isNull()
        assertThat(XtreamAccount.fromPlaylistUrl("http://host.example/get.php?username=u")).isNull()
        assertThat(XtreamAccount.fromPlaylistUrl("http://host.example/get.php?username=&password=p")).isNull()
        assertThat(XtreamAccount.fromPlaylistUrl("not a url")).isNull()
    }

    @Test
    fun `builds api and stream urls with encoded credentials`() {
        val a = XtreamAccount("http://h:8080/", "user name", "p&ss/1", "ts")
        assertThat(a.apiUrl()).isEqualTo("http://h:8080/player_api.php?username=user+name&password=p%26ss%2F1")
        assertThat(a.apiUrl("get_series_info", "series_id" to "42"))
            .isEqualTo("http://h:8080/player_api.php?username=user+name&password=p%26ss%2F1&action=get_series_info&series_id=42")
        assertThat(a.liveUrl("7")).isEqualTo("http://h:8080/live/user%20name/p%26ss%2F1/7.ts")
        assertThat(a.movieUrl("8", "mkv")).isEqualTo("http://h:8080/movie/user%20name/p%26ss%2F1/8.mkv")
        assertThat(a.episodeUrl("9", null)).isEqualTo("http://h:8080/series/user%20name/p%26ss%2F1/9.mp4")
    }

    @Test
    fun `api stream urls get the same stable keys as m3u stream urls`() {
        val a = XtreamAccount.fromPlaylistUrl("http://h:8080/get.php?username=u1&password=p1&type=m3u_plus&output=ts")!!
        val keys = StableKeyGenerator(setOf("u1", "p1"))
        assertThat(keys.normalizedPath(a.movieUrl("123", "mkv"))).isEqualTo(keys.normalizedPath("http://h:8080/movie/u1/p1/123.mkv"))
        assertThat(keys.normalizedPath(a.liveUrl("5"))).isEqualTo("/live/5")
    }

    @Test
    fun `toString does not leak the password`() {
        assertThat(XtreamAccount("http://h/", "u1", "secret", "ts").toString()).doesNotContain("secret")
    }

    @Test
    fun `reads the movie id back from a movie url`() {
        val a = XtreamAccount.fromPlaylistUrl("http://host.example:8080/get.php?username=u1&password=p1")!!
        assertThat(a.movieId(a.movieUrl("190838", "mkv"))).isEqualTo("190838")
        assertThat(a.movieId(a.episodeUrl("190838", "mkv"))).isNull()
        assertThat(a.movieId("http://other.example/movie/u1/p1/1.mkv")).isNull()
    }
}
