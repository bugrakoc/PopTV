package pm.koc.poptv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pm.koc.poptv.data.db.ShowEntity
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.prefs.AppState
import pm.koc.poptv.data.prefs.EpgStatus
import pm.koc.poptv.data.prefs.SettingsRepository
import pm.koc.poptv.data.repo.ContinueEntry
import pm.koc.poptv.data.repo.EpgRefreshState
import pm.koc.poptv.data.repo.EpgRepository
import pm.koc.poptv.data.repo.ImportState
import pm.koc.poptv.data.repo.LibraryRepository
import pm.koc.poptv.data.repo.PlaybackRepository
import pm.koc.poptv.data.repo.PlaylistRepository
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val playback: PlaybackRepository,
    private val settings: SettingsRepository,
    playlists: PlaylistRepository,
    epg: EpgRepository,
) : ViewModel() {

    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val continueWatching: StateFlow<List<ContinueEntry>> = playback.continueWatching().state(emptyList())
    val favoriteChannels: StateFlow<List<StreamEntity>> = library.favoriteChannels().state(emptyList())
    val recentChannels: StateFlow<List<StreamEntity>> = library.recentChannels(20).state(emptyList())
    val favoriteMovies: StateFlow<List<StreamEntity>> = library.favoriteMovies().state(emptyList())
    val favoriteShows: StateFlow<List<ShowEntity>> = library.favoriteShows().state(emptyList())
    val showOriginalNames: StateFlow<Boolean> = settings.settings.map { it.showOriginalNames }.state(false)
    val importState: StateFlow<ImportState> = playlists.state
    val epgState: StateFlow<EpgRefreshState> = epg.state

    /** True once when no guide could be found for this playlist (the notice is shown only once). */
    val showEpgNotice: StateFlow<Boolean> = settings.state
        .map { it.epgStatus == EpgStatus.UNAVAILABLE && !it.epgNoticeShown }
        .state(false)

    val appState: StateFlow<AppState> = settings.state.state(AppState())

    fun epgNoticeSeen() {
        viewModelScope.launch { settings.setEpgNoticeShown(true) }
    }

    fun removeFromContinue(entry: ContinueEntry) {
        viewModelScope.launch { playback.hideFromContinue(entry.stream.key, entry.item.seriesKey) }
    }

    fun setWatched(entry: ContinueEntry, watched: Boolean) {
        viewModelScope.launch { playback.setWatched(entry.stream, watched) }
    }
}
