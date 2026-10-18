package pm.koc.poptv.domain

/** One playlist group-title as shown in the category settings. */
data class GroupInfo(
    val groupTitle: String,
    val detectedType: ContentType,
    val count: Int,
    val firstIndex: Int,
    val override: CategoryOverride?,
) {
    val effective: CategoryOverride
        get() = override ?: when (detectedType) {
            ContentType.LIVE -> CategoryOverride.LIVE
            ContentType.MOVIE -> CategoryOverride.MOVIE
            ContentType.SERIES -> CategoryOverride.SERIES
        }
}

object CategoryGroups {

    /** One row per (group, detectedType) from the database. */
    data class Row(val groupTitle: String, val detectedType: ContentType, val count: Int, val firstIndex: Int)

    /**
     * Folds per-type rows into one entry per group: the detected type is the majority type in the group
     * (ties broken by LIVE < MOVIE < SERIES order), sorted by first appearance in the playlist.
     */
    fun fold(rows: List<Row>, overrides: Map<String, CategoryOverride>): List<GroupInfo> =
        rows.groupBy { it.groupTitle }.map { (group, parts) ->
            val majority = parts.sortedWith(compareByDescending<Row> { it.count }.thenBy { it.detectedType.ordinal }).first()
            GroupInfo(
                groupTitle = group,
                detectedType = majority.detectedType,
                count = parts.sumOf { it.count },
                firstIndex = parts.minOf { it.firstIndex },
                override = overrides[group],
            )
        }.sortedBy { it.firstIndex }
}
