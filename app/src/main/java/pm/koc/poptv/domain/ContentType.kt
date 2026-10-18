package pm.koc.poptv.domain

/** What a playlist entry is. Stored by name in Room. */
enum class ContentType { LIVE, MOVIE, SERIES }

/** User override for a playlist category (group-title). */
enum class CategoryOverride { LIVE, MOVIE, SERIES, HIDDEN }

fun CategoryOverride.toContentType(): ContentType? = when (this) {
    CategoryOverride.LIVE -> ContentType.LIVE
    CategoryOverride.MOVIE -> ContentType.MOVIE
    CategoryOverride.SERIES -> ContentType.SERIES
    CategoryOverride.HIDDEN -> null
}

/** Kind of item stored in favorites / history. */
enum class ItemKind { CHANNEL, MOVIE, SHOW }
