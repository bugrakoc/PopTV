package pm.koc.poptv.data.classify

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import pm.koc.poptv.data.classify.Classification.Reason
import pm.koc.poptv.domain.ContentType

class ContentClassifierTest {

    private val classifier = ContentClassifier()

    private fun classify(title: String, url: String, group: String = "", tvgType: String? = null) =
        classifier.classify(title, url, group, tvgType)

    @Test
    fun `tvg-type has highest priority`() {
        assertThat(classify("X", "http://example.com/live/u/p/1.ts", "Movies", "movie"))
            .isEqualTo(Classification(ContentType.MOVIE, Reason.TVG_TYPE))
        assertThat(classify("X", "http://example.com/movie/u/p/1.mkv", tvgType = "live").type).isEqualTo(ContentType.LIVE)
        assertThat(classify("X", "http://example.com/1.mp4", tvgType = "series").type).isEqualTo(ContentType.SERIES)
    }

    @Test
    fun `url path patterns`() {
        assertThat(classify("Channel", "http://example.com/live/u/p/1.ts")).isEqualTo(Classification(ContentType.LIVE, Reason.URL))
        assertThat(classify("A Film", "http://example.com/movie/u/p/2.mkv")).isEqualTo(Classification(ContentType.MOVIE, Reason.URL))
        assertThat(classify("Show S01E01", "http://example.com/series/u/p/3.mp4")).isEqualTo(Classification(ContentType.SERIES, Reason.URL))
        // The file name itself is not a path segment.
        assertThat(classifier.urlType("http://example.com/live")).isNull()
    }

    @Test
    fun `movie url upgraded to series when title has an episode marker`() {
        assertThat(classify("Show S02E05", "http://example.com/vod/u/p/9.mp4").type).isEqualTo(ContentType.SERIES)
    }

    @Test
    fun `group keywords in english and turkish`() {
        val url = "http://example.com/u/p/1" // no url hints, no extension
        assertThat(classify("x", url, "TR | Yerli Diziler").type).isEqualTo(ContentType.SERIES)
        assertThat(classify("x", url, "TV Shows").type).isEqualTo(ContentType.SERIES)
        assertThat(classify("x", url, "VOD | Yabancı Filmler").type).isEqualTo(ContentType.MOVIE)
        assertThat(classify("x", url, "SİNEMA").type).isEqualTo(ContentType.MOVIE)
        assertThat(classify("x", url, "Movies 4K").type).isEqualTo(ContentType.MOVIE)
        assertThat(classify("x", url, "TR | Ulusal Kanallar").type).isEqualTo(ContentType.LIVE)
        assertThat(classify("x", url, "Sinema Kanalları").type).isEqualTo(ContentType.LIVE)
        assertThat(classify("x", url, "CANLI SPOR").type).isEqualTo(ContentType.LIVE)
        assertThat(classify("x", url, "UK | Sports").type).isEqualTo(ContentType.LIVE)
        assertThat(classify("x", url, "Haber").type).isEqualTo(ContentType.LIVE)
        assertThat(classify("x", url, "Live Events").reason).isEqualTo(Reason.GROUP)
    }

    @Test
    fun `film keyword does not catch channel brands`() {
        // "FilmBox" is a channel brand; no keyword matches so the extension decides.
        assertThat(classify("FilmBox", "http://example.com/u/p/1.ts", "FilmBox").type).isEqualTo(ContentType.LIVE)
    }

    @Test
    fun `file extension`() {
        assertThat(classify("Something", "http://example.com/a/b.mkv")).isEqualTo(Classification(ContentType.MOVIE, Reason.EXTENSION))
        assertThat(classify("Something", "http://example.com/a/b.mp4").type).isEqualTo(ContentType.MOVIE)
        assertThat(classify("Something", "http://example.com/a/b.ts")).isEqualTo(Classification(ContentType.LIVE, Reason.EXTENSION))
        assertThat(classify("Something", "http://example.com/a/index.m3u8").type).isEqualTo(ContentType.LIVE)
        assertThat(classify("Show S01E01", "http://example.com/a/b.mkv").type).isEqualTo(ContentType.SERIES)
    }

    @Test
    fun `title patterns`() {
        assertThat(classify("Show Name 1x03", "http://example.com/a/1")).isEqualTo(Classification(ContentType.SERIES, Reason.TITLE))
        assertThat(classify("Dizi Sezon 1 Bölüm 3", "http://example.com/a/1.ts").type).isEqualTo(ContentType.SERIES)
        assertThat(classify("The Matrix (1999)", "http://example.com/a/1")).isEqualTo(Classification(ContentType.MOVIE, Reason.TITLE))
        assertThat(classify("Some Channel", "http://example.com/a/1")).isEqualTo(Classification(ContentType.LIVE, Reason.DEFAULT))
    }

    @Test
    fun `rules are data driven`() {
        val custom = ContentClassifier(ClassificationRules(groupMovie = setOf("peliculas")))
        assertThat(custom.groupType("Peliculas Accion")).isEqualTo(ContentType.MOVIE)
        assertThat(custom.groupType("Movies")).isNull()
    }
}
