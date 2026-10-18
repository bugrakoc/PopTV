package pm.koc.poptv.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import pm.koc.poptv.data.db.AppDatabase
import pm.koc.poptv.data.db.CategoryEntity
import pm.koc.poptv.data.db.CategoryOverrideEntity
import pm.koc.poptv.domain.CategoryGroups
import pm.koc.poptv.domain.CategoryOverride
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.domain.GroupInfo
import javax.inject.Inject
import javax.inject.Singleton

/** Browsable categories plus user overrides of a category's type (or hiding it). */
@Singleton
class CategoryRepository @Inject constructor(
    private val db: AppDatabase,
    private val indexer: LibraryIndexer,
) {
    fun categories(type: ContentType): Flow<List<CategoryEntity>> = db.categoryDao().observeByType(type)

    suspend fun groups(): List<GroupInfo> {
        val overrides = db.overrideDao().all().associate { it.groupTitle to it.override }
        val rows = db.streamDao().groupSummaries().map {
            CategoryGroups.Row(it.groupTitle, it.detectedType, it.count, it.firstIndex)
        }
        return CategoryGroups.fold(rows, overrides)
    }

    /** Sets (or with null clears) the override for [group] and updates the library immediately. */
    suspend fun setOverride(group: String, override: CategoryOverride?) {
        val dao = db.streamDao()
        db.withTransaction {
            when (override) {
                null -> {
                    db.overrideDao().delete(group)
                    dao.resetGroup(group, hidden = false)
                }
                CategoryOverride.HIDDEN -> {
                    db.overrideDao().upsert(CategoryOverrideEntity(group, override))
                    dao.resetGroup(group, hidden = true)
                }
                else -> {
                    db.overrideDao().upsert(CategoryOverrideEntity(group, override))
                    val type = when (override) {
                        CategoryOverride.LIVE -> ContentType.LIVE
                        CategoryOverride.MOVIE -> ContentType.MOVIE
                        else -> ContentType.SERIES
                    }
                    dao.setGroupType(group, type)
                }
            }
            // Entries that become live channels need a channel number.
            dao.fillMissingNumbers(group)
        }
        indexer.rebuildDerived()
        indexer.rematchEpg()
    }

    suspend fun hide(group: String) = setOverride(group, CategoryOverride.HIDDEN)
}
