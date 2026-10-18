package pm.koc.poptv.domain

import pm.koc.poptv.core.NaturalOrder

/** Minimal episode view used for grouping (decoupled from Room entities for testability). */
data class EpisodeRef(
    val key: String,
    val title: String,
    val season: Int?,
    val episode: Int?,
    val sortIndex: Int,
)

data class Season<T>(val number: Int, val episodes: List<T>)

/**
 * Groups a show's episodes into seasons: seasons ascending (missing season = 1), episodes by number, then
 * natural title order, then playlist order. Duplicate episode numbers (e.g. HD and SD copies) are kept.
 */
object SeasonGrouper {

    fun <T> group(items: List<T>, ref: (T) -> EpisodeRef): List<Season<T>> {
        val comparator = compareBy<T>(
            { ref(it).episode ?: Int.MAX_VALUE },
        ).thenComparator { a, b -> NaturalOrder.compare(ref(a).title, ref(b).title) }
            .thenBy { ref(it).sortIndex }
        return items.groupBy { ref(it).season ?: 1 }
            .toSortedMap()
            .map { (season, eps) -> Season(season, eps.sortedWith(comparator)) }
    }

    /** All episodes in watch order across seasons. */
    fun <T> flatten(seasons: List<Season<T>>): List<T> = seasons.flatMap { it.episodes }

    /** The episode after [currentKey] in watch order, or null at the end. */
    fun <T> next(seasons: List<Season<T>>, currentKey: String, key: (T) -> String): T? {
        val all = flatten(seasons)
        val idx = all.indexOfFirst { key(it) == currentKey }
        return if (idx < 0 || idx + 1 >= all.size) null else all[idx + 1]
    }
}
