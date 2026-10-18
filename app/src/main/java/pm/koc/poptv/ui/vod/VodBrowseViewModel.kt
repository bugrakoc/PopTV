package pm.koc.poptv.ui.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pm.koc.poptv.data.db.CategoryEntity
import pm.koc.poptv.data.db.ShowEntity
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.prefs.SettingsRepository
import pm.koc.poptv.data.repo.CategoryRepository
import pm.koc.poptv.data.repo.LibraryRepository
import pm.koc.poptv.data.repo.SortOrder
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.domain.ItemKind
import javax.inject.Inject

/** Poster grid for movies or shows with category filter and sort order. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class VodBrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val library: LibraryRepository,
    categories: CategoryRepository,
    settings: SettingsRepository,
) : ViewModel() {

    /** Set by the screen (Movies or Shows) before first collection. */
    private val type = MutableStateFlow(ContentType.MOVIE)

    private val _category = MutableStateFlow(savedStateHandle.get<String>("category"))
    val category: StateFlow<String?> = _category.asStateFlow()

    private val _sort = MutableStateFlow(SortOrder.PLAYLIST)
    val sort: StateFlow<SortOrder> = _sort.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = type.flatMapLatest { categories.categories(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val showOriginalNames: StateFlow<Boolean> = settings.settings.map { it.showOriginalNames }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val favoriteKeys: StateFlow<Set<String>> = type.flatMapLatest {
        library.favoriteKeys(if (it == ContentType.SERIES) ItemKind.SHOW else ItemKind.MOVIE)
    }.map { it.toSet() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val movies: Flow<PagingData<StreamEntity>> = combine(_category.debounce(250), _sort) { c, s -> c to s }
        .flatMapLatest { (c, s) -> library.streams(ContentType.MOVIE, c, s) }
        .cachedIn(viewModelScope)

    val shows: Flow<PagingData<ShowEntity>> = combine(_category.debounce(250), _sort) { c, s -> c to s }
        .flatMapLatest { (c, s) -> library.shows(c, s) }
        .cachedIn(viewModelScope)

    fun setType(value: ContentType) {
        type.value = value
    }

    fun selectCategory(value: String?) {
        _category.value = value
    }

    fun setSort(value: SortOrder) {
        _sort.value = value
    }
}
