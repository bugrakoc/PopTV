package pm.koc.poptv.ui.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pm.koc.poptv.data.db.AppDatabase
import pm.koc.poptv.data.db.CategoryEntity
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.prefs.SettingsRepository
import pm.koc.poptv.data.repo.CategoryRepository
import pm.koc.poptv.data.repo.EpgRepository
import pm.koc.poptv.data.repo.LibraryRepository
import pm.koc.poptv.data.repo.LiveContext
import pm.koc.poptv.data.repo.Programme
import pm.koc.poptv.domain.ContentType
import javax.inject.Inject

/** Channels x time grid. Programmes are loaded per channel in 6-hour blocks and cached. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GuideViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val epg: EpgRepository,
    categories: CategoryRepository,
    settings: SettingsRepository,
    db: AppDatabase,
) : ViewModel() {

    private val _category = MutableStateFlow(LiveContext.ALL)
    val category: StateFlow<String> = _category.asStateFlow()

    private val _windowStart = MutableStateFlow(GuideTime.initialWindowStart(System.currentTimeMillis()))
    val windowStart: StateFlow<Long> = _windowStart.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = categories.categories(ContentType.LIVE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasGuide: StateFlow<Boolean?> = db.epgDao().observeProgramCount().map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val showOriginalNames: StateFlow<Boolean> = settings.settings.map { it.showOriginalNames }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val channels: Flow<PagingData<StreamEntity>> = _category.flatMapLatest { ctx ->
        when (ctx) {
            LiveContext.FAVORITES -> library.favoriteChannels().map { PagingData.from(it) }
            else -> library.streams(ContentType.LIVE, LiveContext.groupOrNull(ctx))
        }
    }.cachedIn(viewModelScope)

    private val cache = HashMap<Pair<String, Long>, List<Programme>>()

    fun selectCategory(value: String) {
        _category.value = value
    }

    fun shiftWindow(deltaMs: Long) {
        _windowStart.value = GuideTime.clampWindow(_windowStart.value + deltaMs, System.currentTimeMillis())
    }

    fun jumpToNow() {
        _windowStart.value = GuideTime.initialWindowStart(System.currentTimeMillis())
    }

    /** Programmes for [epgId] overlapping [from, to). */
    suspend fun programmes(epgId: String, from: Long, to: Long): List<Programme> {
        val blocks = GuideTime.blocks(from, to)
        val all = ArrayList<Programme>()
        for (block in blocks) {
            val key = epgId to block
            val list = cache[key] ?: epg.inRange(listOf(epgId), block, block + GuideTime.BLOCK_MS)[epgId].orEmpty()
                .also { cache[key] = it }
            all += list
        }
        return all.distinctBy { it.start to it.title }.filter { it.end > from && it.start < to }.sortedBy { it.start }
    }
}
