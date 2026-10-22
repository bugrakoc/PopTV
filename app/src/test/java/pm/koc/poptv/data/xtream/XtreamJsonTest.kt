package pm.koc.poptv.data.xtream

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class XtreamJsonTest {

    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test
    fun `reads user info in its different shapes`() {
        assertThat(XtreamJson.userInfo(obj("""{"user_info":{"auth":1,"status":"Active"}}"""))!!.isActive).isTrue()
        assertThat(XtreamJson.userInfo(obj("""{"user_info":{"auth":"1"}}"""))!!.isActive).isTrue()
        assertThat(XtreamJson.userInfo(obj("""{"user_info":{"auth":0}}"""))!!.isActive).isFalse()
        val expired = XtreamJson.userInfo(obj("""{"user_info":{"auth":1,"status":"Expired"}}"""))!!
        assertThat(expired.isActive).isFalse()
        assertThat(expired.status).isEqualTo("Expired")
        assertThat(XtreamJson.userInfo(obj("""{"error":"nope"}"""))).isNull()
    }

    @Test
    fun `reads categories in order and skips broken ones`() {
        val items = Json.parseToJsonElement(
            """[{"category_id":"5","category_name":"TR | ULUSAL"},{"category_id":2,"category_name":"SPOR"},{"category_name":"no id"},"junk"]""",
        ) as JsonArray
        val c = XtreamJson.categories(items.asSequence())
        assertThat(c.keys).containsExactly("5", "2").inOrder()
        assertThat(c["2"]).isEqualTo("SPOR")
    }

    @Test
    fun `reads streams with numbers as strings, nulls and category_ids`() {
        val s = XtreamJson.stream(
            obj("""{"num":"12","name":" TRT 1 HD ","stream_id":101,"stream_icon":"","epg_channel_id":null,"category_id":null,"category_ids":[7]}"""),
        )!!
        assertThat(s.id).isEqualTo("101")
        assertThat(s.name).isEqualTo("TRT 1 HD")
        assertThat(s.number).isEqualTo(12)
        assertThat(s.icon).isNull()
        assertThat(s.epgChannelId).isNull()
        assertThat(s.categoryId).isEqualTo("7")
        assertThat(XtreamJson.stream(obj("""{"name":"no id"}"""))).isNull()
    }

    @Test
    fun `reads series years from year or release date`() {
        assertThat(XtreamJson.series(obj("""{"series_id":1,"name":"A","year":"2019"}"""))!!.year).isEqualTo(2019)
        assertThat(XtreamJson.series(obj("""{"series_id":1,"name":"A","releaseDate":"2021-03-04"}"""))!!.year).isEqualTo(2021)
        assertThat(XtreamJson.series(obj("""{"series_id":1,"name":"A","releaseDate":"unknown"}"""))!!.year).isNull()
    }

    @Test
    fun `reads episodes keyed by season`() {
        val eps = XtreamJson.episodes(
            obj(
                """{"episodes":{"1":[{"id":"11","episode_num":1,"title":"Pilot","container_extension":"mkv","season":1},
                   {"id":"12","episode_num":"2","container_extension":"mp4"}],"2":[{"id":"21","episode_num":1}]}}""",
            ),
        )
        assertThat(eps.map { it.id }).containsExactly("11", "12", "21").inOrder()
        assertThat(eps[1].season).isEqualTo(1) // from the key
        assertThat(eps[1].episode).isEqualTo(2)
        assertThat(eps[2].season).isEqualTo(2)
        assertThat(eps[0].extension).isEqualTo("mkv")
    }

    @Test
    fun `reads episodes as arrays of arrays and tolerates missing episodes`() {
        val eps = XtreamJson.episodes(obj("""{"episodes":[[{"id":"1","season":3,"episode_num":4}]]}"""))
        assertThat(eps.single().season).isEqualTo(3)
        assertThat(XtreamJson.episodes(obj("""{"episodes":[]}"""))).isEmpty()
        assertThat(XtreamJson.episodes(obj("""{"info":{}}"""))).isEmpty()
    }

    @Test
    fun `builds m3u entries like the provider playlist`() {
        val a = XtreamAccount("http://h/", "u", "p", "ts")
        val live = XtreamEntries.live(a, XtreamStream("1", "TRT 1", "5", "http://logo", "trt1.tr", 3, null), "ULUSAL")
        assertThat(live.url).isEqualTo("http://h/live/u/p/1.ts")
        assertThat(live.tvgId).isEqualTo("trt1.tr")
        assertThat(live.tvgChno).isEqualTo(3)
        assertThat(live.tvgType).isEqualTo("live")
        assertThat(live.group).isEqualTo("ULUSAL")
        val ep = XtreamEntries.episode(a, "Friends", XtreamEpisode("9", null, 1, 2, "mkv"), "DIZI", null)
        assertThat(ep.title).isEqualTo("Friends S01E02")
        assertThat(ep.url).isEqualTo("http://h/series/u/p/9.mkv")
    }

    @Test
    fun `reads TMDB ids from vod and series info`() {
        assertThat(XtreamJson.vodTmdbId(obj("""{"info":{"tmdb_id":"793040"}}"""))).isEqualTo(793040L)
        assertThat(XtreamJson.vodTmdbId(obj("""{"info":{"tmdb_id":"","tmdb":1396}}"""))).isEqualTo(1396L)
        assertThat(XtreamJson.vodTmdbId(obj("""{"info":[]}"""))).isNull()
        assertThat(XtreamJson.seriesTmdbId(obj("""{"info":{"tmdb":"1396"}}"""))).isEqualTo(1396L)
        // Episode-level ids are TMDB episode ids, not the show's.
        assertThat(
            XtreamJson.seriesTmdbId(obj("""{"info":{"name":"Breaking Bad"},"episodes":{"1":[{"id":"1","info":{"tmdb_id":62085}}]}}""")),
        ).isNull()
    }
}
