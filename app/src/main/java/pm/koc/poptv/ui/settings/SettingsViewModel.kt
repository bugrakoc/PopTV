package pm.koc.poptv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pm.koc.poptv.data.prefs.AppSettings
import pm.koc.poptv.data.prefs.AppState
import pm.koc.poptv.data.prefs.BufferMode
import pm.koc.poptv.data.prefs.DecoderMode
import pm.koc.poptv.data.prefs.SettingsRepository
import pm.koc.poptv.data.prefs.SubtitleColor
import pm.koc.poptv.data.prefs.SubtitleSize
import pm.koc.poptv.data.repo.EpgRefreshState
import pm.koc.poptv.data.repo.EpgRepository
import pm.koc.poptv.data.repo.ImportState
import pm.koc.poptv.data.repo.MetadataRepository
import pm.koc.poptv.data.repo.PlaylistRepository
import pm.koc.poptv.data.work.WorkScheduler
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val playlists: PlaylistRepository,
    private val epg: EpgRepository,
    private val metadata: MetadataRepository,
    private val workScheduler: WorkScheduler,
) : ViewModel() {

    val values: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val state: StateFlow<AppState> = settings.state.stateIn(viewModelScope, SharingStarted.Eagerly, AppState())
    val importState: StateFlow<ImportState> = playlists.state
    val epgState: StateFlow<EpgRefreshState> = epg.state
    val hasTmdbKey: StateFlow<Boolean> = metadata.enabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val hasManualEpg: StateFlow<Boolean> = settings.hasManualEpgUrl.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _loggedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loggedOut: SharedFlow<Unit> = _loggedOut.asSharedFlow()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun refreshPlaylist() = playlists.refreshNow()
    fun refreshEpg() = epg.refreshInBackground()

    fun setPlaylistRefreshHours(h: Int) = launch { settings.setPlaylistRefreshHours(h); workScheduler.schedule() }
    fun setEpgRefreshHours(h: Int) = launch { settings.setEpgRefreshHours(h); workScheduler.schedule() }
    fun setEpgOffset(minutes: Int) = launch { settings.setEpgOffsetMinutes(minutes) }
    fun setDecoder(mode: DecoderMode) = launch { settings.setDecoderMode(mode) }
    fun setTunneling(on: Boolean) = launch { settings.setTunneling(on) }
    fun setBuffer(mode: BufferMode) = launch { settings.setBufferMode(mode) }
    fun setUserAgent(ua: String) = launch { settings.setUserAgent(ua) }
    fun setSubtitleSize(size: SubtitleSize) = launch { settings.setSubtitleSize(size) }
    fun setSubtitleColor(color: SubtitleColor) = launch { settings.setSubtitleColor(color) }
    fun setSubtitleBackground(on: Boolean) = launch { settings.setSubtitleBackground(on) }
    fun setAutoplayNext(on: Boolean) = launch { settings.setAutoplayNext(on) }
    fun setInvertChannelKeys(on: Boolean) = launch { settings.setInvertChannelKeys(on) }
    fun setShowOriginalNames(on: Boolean) = launch { settings.setShowOriginalNames(on) }
    /** The library is updated by the app-wide filter watcher in [pm.koc.poptv.PopTvApp]. */
    fun setHideAdult(on: Boolean) = launch { settings.setHideAdult(on) }

    suspend fun manualEpgUrl(): String = settings.manualEpgUrl()
    fun setManualEpgUrl(url: String) = launch {
        settings.setManualEpgUrl(url)
        epg.refreshInBackground()
    }

    fun setTmdbKey(key: String) = launch {
        settings.setTmdbKey(key)
        metadata.clearCache()
    }

    fun clearMetadataCache() = launch { metadata.clearCache() }

    fun logout() = launch {
        playlists.logout()
        _loggedOut.emit(Unit)
    }
}
