package pm.koc.poptv.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import pm.koc.poptv.core.UrlUtils
import pm.koc.poptv.data.repo.ImportState
import pm.koc.poptv.data.repo.PlaylistRepository
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
) : ViewModel() {

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _invalidUrl = MutableStateFlow(false)
    val invalidUrl: StateFlow<Boolean> = _invalidUrl.asStateFlow()

    val importState: StateFlow<ImportState> =
        playlists.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportState.Idle)

    fun onUrlChange(value: String) {
        // Remotes and IMEs sometimes insert line breaks or spaces when pasting.
        _url.value = value.replace("\n", "").replace(" ", "")
        _invalidUrl.value = false
    }

    fun prefill(value: String?) {
        if (!value.isNullOrBlank() && _url.value.isEmpty()) onUrlChange(value)
    }

    fun submit() {
        val value = _url.value.trim()
        if (!UrlUtils.isValidHttpUrl(value)) {
            _invalidUrl.value = true
            return
        }
        playlists.login(value)
    }

    fun cancel() = playlists.cancel()

    fun acknowledge() = playlists.acknowledge()
}
