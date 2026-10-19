package pm.koc.poptv.domain

import pm.koc.poptv.core.TextNormalizer

/**
 * Recognizes adult categories by their group title ("FOR ADULTS ➾ ...", "XXX", "+18", "Yetişkin", ...).
 * Whole categories are filtered, so an item is adult when its category is.
 */
object AdultContent {

    /** Matched as whole words of the folded title, so "Adultswim" or "Sussex" do not count. */
    private val keywords = setOf(
        "adult", "adults", "xxx", "porn", "porno", "erotic", "erotik", "erotica", "yetiskin", "yetiskinler",
    )
    private val ageMarker = Regex("""(?<![\d+])(\+\s?18|18\s?\+)(?!\d)""")

    fun isAdult(groupTitle: String): Boolean {
        if (groupTitle.isBlank()) return false
        if (ageMarker.containsMatchIn(groupTitle)) return true
        return TextNormalizer.searchKey(groupTitle).split(' ').any { it in keywords }
    }

    /** Whether rows of [groupTitle] are hidden, given the user's category override and the adult filter. */
    fun hidden(groupTitle: String, override: CategoryOverride?, hideAdult: Boolean): Boolean =
        override == CategoryOverride.HIDDEN || (hideAdult && isAdult(groupTitle))
}
