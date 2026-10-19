package pm.koc.poptv.domain

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class AdultContentTest {

    @Test
    fun `recognizes adult categories`() {
        listOf(
            "FOR ADULTS",
            "FOR ADULTS ➾ MILF",
            "FOR ADULTS ➾ (TR ALTYAZI)",
            "XXX Movies",
            "Adult | VOD",
            "+18 Kanallar",
            "18+ ONLY",
            "Yetişkin",
            "YETİŞKİNLER",
            "Erotik Filmler",
            "TR: Porno",
        ).forEach { assertWithMessage(it).that(AdultContent.isAdult(it)).isTrue() }
    }

    @Test
    fun `leaves ordinary categories alone`() {
        listOf(
            "",
            "TR ➾ Salı Dizileri",
            "Adultswim",
            "Sussex Sports",
            "UEFA U18",
            "2018+ Movies",
            "Top 180",
            "Film 18 Ağustos",
        ).forEach { assertWithMessage(it).that(AdultContent.isAdult(it)).isFalse() }
    }

    @Test
    fun `hidden combines the user override and the adult filter`() {
        assertThat(AdultContent.hidden("FOR ADULTS", null, hideAdult = true)).isTrue()
        assertThat(AdultContent.hidden("FOR ADULTS", null, hideAdult = false)).isFalse()
        assertThat(AdultContent.hidden("FOR ADULTS", CategoryOverride.MOVIE, hideAdult = true)).isTrue()
        assertThat(AdultContent.hidden("Spor", CategoryOverride.HIDDEN, hideAdult = false)).isTrue()
        assertThat(AdultContent.hidden("Spor", null, hideAdult = true)).isFalse()
    }
}
