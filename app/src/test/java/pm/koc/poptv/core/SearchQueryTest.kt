package pm.koc.poptv.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchQueryTest {
    @Test
    fun `folds turkish and accents and joins words`() {
        assertThat(SearchQuery.likePattern("İstanbul Işık")).isEqualTo("%istanbul%isik%")
        assertThat(SearchQuery.likePattern("  KANAL   D ")).isEqualTo("%kanal%d%")
        assertThat(SearchQuery.likePattern("Şahsiyet")).isEqualTo("%sahsiyet%")
    }

    @Test
    fun `too short or punctuation only returns null`() {
        assertThat(SearchQuery.likePattern("a")).isNull()
        assertThat(SearchQuery.likePattern("  ")).isNull()
        assertThat(SearchQuery.likePattern("!!")).isNull()
    }

    @Test
    fun `punctuation is ignored so wildcards cannot be injected`() {
        assertThat(SearchQuery.likePattern("100%_x")).isEqualTo("%100%x%")
    }

    @Test
    fun `matches stored search names`() {
        // Simulate SQL LIKE matching against the folded column.
        fun like(pattern: String, value: String): Boolean {
            val regex = pattern.split('%').joinToString(".*") { Regex.escape(it) }
            return Regex(regex).matches(value)
        }
        val stored = TextNormalizer.searchKey("TR: İNCİ TANELERİ (2023) 4K")
        assertThat(like(SearchQuery.likePattern("inci tane")!!, stored)).isTrue()
        assertThat(like(SearchQuery.likePattern("İnci")!!, stored)).isTrue()
        assertThat(like(SearchQuery.likePattern("INCI")!!, stored)).isTrue()
        assertThat(like(SearchQuery.likePattern("taneleri inci")!!, stored)).isFalse()
    }
}
