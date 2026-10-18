package pm.koc.poptv.ui.live

import android.util.LruCache
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pm.koc.poptv.data.db.CategoryEntity
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.prefs.SettingsRepository
import pm.koc.poptv.data.repo.CategoryRepository
import pm.koc.poptv.data.repo.EpgRepository
import pm.koc.poptv.data.repo.LibraryRepository
import pm.koc.poptv.data.repo.LiveContext
import pm.koc.poptv.data.repo.Programme
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.domain.ItemKind
import javax.inject.Inject

/** Categories + paged channel list + now/next cache, shared by the Live TV screen and the player overlay. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ChannelBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val library: LibraryRepository,
    private val categoryRepository: CategoryRepository,
    private val epg: EpgRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val _selected = MutableStateFlow(savedStateHandle.get<String>("category") ?: LiveContext.ALL)
    val selected: StateFlow<String> = _selected.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.categories(ContentType.LIVE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteKeys: StateFlow<Set<String>> = library.favoriteKeys(ItemKind.CHANNEL).map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val showOriginalNames: StateFlow<Boolean> = settings.settings.map { it.showOriginalNames }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val totalLive: StateFlow<Int> = library.count(ContentType.LIVE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val channels: Flow<PagingData<StreamEntity>> = _selected
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { ctx ->
            when (ctx) {
                LiveContext.FAVORITES -> library.favoriteChannels().map { PagingData.from(it) }
                LiveContext.RECENT -> library.recentChannels(50).map { PagingData.from(it) }
                else -> library.streams(ContentType.LIVE, LiveContext.groupOrNull(ctx))
            }
        }
        .cachedIn(viewModelScope)

    private val nowNextCache = LruCache<String, Pair<Long, List<Programme>>>(500)

    fun select(context: String) {
        _selected.value = context
    }

    /** Current/next programmes for a guide channel, cached for a minute. */
    suspend fun nowNext(epgChannelId: String): List<Programme> {
        val now = System.currentTimeMillis()
        nowNextCache.get(epgChannelId)?.let { (at, list) ->
            val stillValid = now - at < 60_000 && (list.isEmpty() || list.first().end > now)
            if (stillValid) return list
        }
        val list = runCatching { epg.upcoming(epgChannelId, 2) }.getOrDefault(emptyList())
        nowNextCache.put(epgChannelId, now to list)
        return list
    }

    fun toggleFavorite(stream: StreamEntity) {
        viewModelScope.launch { library.toggleFavorite(stream.key, ItemKind.CHANNEL) }
    }

    fun hideCategory(group: String) {
        viewModelScope.launch {
            categoryRepository.hide(group)
            if (_selected.value == group) _selected.value = LiveContext.ALL
        }
    }
}
