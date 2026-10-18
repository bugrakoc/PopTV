package pm.koc.poptv.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import pm.koc.poptv.data.prefs.SettingsRepository
import pm.koc.poptv.data.repo.SearchRepository
import pm.koc.poptv.data.repo.SearchResults
import javax.inject.Inject

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: SearchRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val _query = MutableStateFlow(savedStateHandle.get<String>(KEY) ?: "")
    val query: StateFlow<String> = _query.asStateFlow()

    /** null = query too short (nothing searched yet). */
    val results: StateFlow<SearchResults?> = _query
        .debounce(300)
        .map { it.trim() }
        .distinctUntilChanged()
        .mapLatest { repository.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val showOriginalNames: StateFlow<Boolean> = settings.settings.map { it.showOriginalNames }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onQueryChange(value: String) {
        _query.value = value
        savedStateHandle[KEY] = value
    }

    private companion object {
        const val KEY = "query"
    }
}
