package pm.koc.poptv.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import pm.koc.poptv.domain.CategoryOverride
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.domain.ItemKind

/** One playlist entry (live channel, movie or episode). Replaced on every playlist import. */
@Entity(
    tableName = "streams",
    indices = [
        Index(value = ["type", "hidden", "groupTitle", "sortIndex"]),
        Index(value = ["type", "hidden", "sortIndex"]),
        Index(value = ["type", "number"]),
        Index(value = ["groupTitle"]),
        Index(value = ["searchName"]),
        Index(value = ["tvgId"]),
        Index(value = ["epgChannelId"]),
        Index(value = ["seriesKey", "season", "episode"]),
        Index(value = ["generation"]),
    ],
)
data class StreamEntity(
    @PrimaryKey val key: String,
    /** Title exactly as in the playlist. */
    val name: String,
    /** Cleaned title for display (prefixes, quality tags and year removed). */
    val displayName: String,
    /** Folded original name used for search. */
    val searchName: String,
    val url: String,
    val logo: String?,
    val groupTitle: String,
    val tvgId: String?,
    val tvgName: String?,
    /** Channel number: tvg-chno when present, otherwise the position among live channels. */
    val number: Int,
    val type: ContentType,
    val detectedType: ContentType,
    val hidden: Boolean,
    val sortIndex: Int,
    val userAgent: String?,
    val referrer: String?,
    val seriesKey: String?,
    val seriesName: String?,
    val season: Int?,
    val episode: Int?,
    val year: Int?,
    /** Compact normalized channel name used as the EPG fallback match key. */
    val epgNameKey: String,
    val epgChannelId: String?,
    val generation: Int,
)

/** A browsable category: a group-title within one content type. Rebuilt after each import. */
@Entity(tableName = "categories", indices = [Index(value = ["type", "sortIndex"])])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val type: ContentType,
    val name: String,
    val displayName: String,
    val sortIndex: Int,
    val itemCount: Int,
)

/** A TV show built from grouped episodes. Rebuilt after each import. */
@Entity(
    tableName = "shows",
    indices = [Index(value = ["groupTitle", "sortIndex"]), Index(value = ["sortIndex"]), Index(value = ["searchName"])],
)
data class ShowEntity(
    @PrimaryKey val seriesKey: String,
    val name: String,
    val searchName: String,
    val logo: String?,
    val groupTitle: String,
    val sortIndex: Int,
    val episodeCount: Int,
    val seasonCount: Int,
    val year: Int?,
)

/** User-chosen type for a group-title. Survives refreshes. */
@Entity(tableName = "category_overrides")
data class CategoryOverrideEntity(
    @PrimaryKey val groupTitle: String,
    val override: CategoryOverride,
)

@Entity(tableName = "favorites", indices = [Index(value = ["kind", "addedAt"])])
data class FavoriteEntity(
    @PrimaryKey val key: String,
    val kind: ItemKind,
    val addedAt: Long,
)

/** Recently watched channels (and other items). */
@Entity(tableName = "watch_history", indices = [Index(value = ["kind", "watchedAt"])])
data class HistoryEntity(
    @PrimaryKey val key: String,
    val kind: ItemKind,
    val watchedAt: Long,
)

/** Resume position for a movie or an episode. */
@Entity(
    tableName = "playback_positions",
    indices = [Index(value = ["updatedAt"]), Index(value = ["seriesKey", "updatedAt"])],
)
data class PlaybackPositionEntity(
    @PrimaryKey val streamKey: String,
    val seriesKey: String?,
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val hiddenFromContinue: Boolean = false,
)

@Entity(tableName = "epg_channels")
data class EpgChannelEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    /** Normalized display names ([pm.koc.poptv.data.epg.EpgMatcher.nameKey]) separated by '|'. */
    val nameKeys: String,
    val icon: String?,
)

@Entity(
    tableName = "epg_programs",
    indices = [Index(value = ["channelId", "start"]), Index(value = ["end"])],
)
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val start: Long,
    val end: Long,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val category: String?,
)

/** Cached TMDB lookup (also caches misses so they are not retried constantly). */
@Entity(tableName = "tmdb_cache")
data class MetadataEntity(
    @PrimaryKey val cacheKey: String,
    val tmdbId: Long?,
    val title: String?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: Double?,
    val year: Int?,
    val genres: String?,
    val cast: String?,
    val fetchedAt: Long,
)
