package pm.koc.poptv.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CategoryGroupsTest {
    @Test
    fun `majority type, totals, overrides and playlist order`() {
        val rows = listOf(
            CategoryGroups.Row("Mixed", ContentType.MOVIE, 30, 10),
            CategoryGroups.Row("Mixed", ContentType.SERIES, 5, 12),
            CategoryGroups.Row("News", ContentType.LIVE, 8, 0),
            CategoryGroups.Row("Tie", ContentType.SERIES, 2, 50),
            CategoryGroups.Row("Tie", ContentType.MOVIE, 2, 51),
        )
        val groups = CategoryGroups.fold(rows, mapOf("News" to CategoryOverride.HIDDEN))
        assertThat(groups.map { it.groupTitle }).containsExactly("News", "Mixed", "Tie").inOrder()
        val mixed = groups[1]
        assertThat(mixed.detectedType).isEqualTo(ContentType.MOVIE)
        assertThat(mixed.count).isEqualTo(35)
        assertThat(mixed.firstIndex).isEqualTo(10)
        assertThat(mixed.effective).isEqualTo(CategoryOverride.MOVIE)
        assertThat(groups[0].effective).isEqualTo(CategoryOverride.HIDDEN)
        assertThat(groups[2].detectedType).isEqualTo(ContentType.MOVIE) // tie -> lower ordinal
    }
}
