package pm.koc.poptv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pm.koc.poptv.data.prefs.SettingsRepository
import javax.inject.Inject

/** Decides the first screen: login when no playlist is stored, home otherwise. */
@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {

    enum class Start { LOADING, LOGIN, HOME }

    private val _start = MutableStateFlow(Start.LOADING)
    val start: StateFlow<Start> = _start.asStateFlow()

    init {
        viewModelScope.launch {
            val loggedIn = settings.isLoggedIn.first() && settings.playlistUrl() != null
            _start.value = if (loggedIn) Start.HOME else Start.LOGIN
        }
    }
}
