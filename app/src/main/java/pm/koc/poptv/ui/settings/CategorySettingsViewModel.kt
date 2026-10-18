package pm.koc.poptv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.koc.poptv.data.repo.CategoryRepository
import pm.koc.poptv.domain.CategoryOverride
import pm.koc.poptv.domain.GroupInfo
import javax.inject.Inject

@HiltViewModel
class CategorySettingsViewModel @Inject constructor(
    private val categories: CategoryRepository,
) : ViewModel() {

    enum class Filter { ALL, LIVE, MOVIE, SERIES, HIDDEN, CHANGED }

    data class UiState(
        val loading: Boolean = true,
        val busy: Boolean = false,
        val groups: List<GroupInfo> = emptyList(),
        val filter: Filter = Filter.ALL,
    ) {
        val visible: List<GroupInfo>
            get() = groups.filter {
                when (filter) {
                    Filter.ALL -> true
                    Filter.LIVE -> it.effective == CategoryOverride.LIVE
                    Filter.MOVIE -> it.effective == CategoryOverride.MOVIE
                    Filter.SERIES -> it.effective == CategoryOverride.SERIES
                    Filter.HIDDEN -> it.effective == CategoryOverride.HIDDEN
                    Filter.CHANGED -> it.override != null
                }
            }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            val groups = categories.groups()
            _state.update { it.copy(loading = false, busy = false, groups = groups) }
        }
    }

    fun setFilter(filter: Filter) = _state.update { it.copy(filter = filter) }

    fun setOverride(group: String, override: CategoryOverride?) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            categories.setOverride(group, override)
            reload()
        }
    }
}
