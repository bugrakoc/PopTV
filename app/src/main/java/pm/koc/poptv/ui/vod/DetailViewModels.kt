package pm.koc.poptv.ui.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pm.koc.poptv.data.db.PlaybackPositionEntity
import pm.koc.poptv.data.db.ShowEntity
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.net.AppError
import pm.koc.poptv.data.prefs.SettingsRepository
import pm.koc.poptv.data.repo.LibraryRepository
import pm.koc.poptv.data.repo.Metadata
import pm.koc.poptv.data.repo.MetadataRepository
import pm.koc.poptv.data.repo.PlaybackRepository
import pm.koc.poptv.data.repo.XtreamEpisodeLoader
import pm.koc.poptv.domain.EpisodeRef
import pm.koc.poptv.domain.ItemKind
import pm.koc.poptv.domain.ResumePolicy
import pm.koc.poptv.domain.Season
import pm.koc.poptv.domain.SeasonGrouper
import pm.koc.poptv.ui.nav.MovieDetailRoute
import pm.koc.poptv.ui.nav.ShowDetailRoute
import javax.inject.Inject

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val library: LibraryRepository,
    private val playback: PlaybackRepository,
    private val metadataRepository: MetadataRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val key = savedStateHandle.toRoute<MovieDetailRoute>().streamKey

    val stream: StateFlow<StreamEntity?> = library.observeStream(key)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val position: StateFlow<PlaybackPositionEntity?> = playback.observePosition(key)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val isFavorite: StateFlow<Boolean> = library.isFavorite(key)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val showOriginalNames: StateFlow<Boolean> = settings.settings.map { it.showOriginalNames }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _metadata = MutableStateFlow<Metadata?>(null)
    val metadata: StateFlow<Metadata?> = _metadata.asStateFlow()

    init {
        viewModelScope.launch {
            val s = library.observeStream(key).filterNotNull().first()
            _metadata.value = metadataRepository.movie(s.displayName, s.year)
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch { library.toggleFavorite(key, ItemKind.MOVIE) }
    }

    fun setWatched(watched: Boolean) {
        val s = stream.value ?: return
        viewModelScope.launch { playback.setWatched(s, watched) }
    }
}

data class EpisodeUi(
    val stream: StreamEntity,
    val progress: Float,
    val watched: Boolean,
)

/** On-demand episode loading (Xtream API shows); M3U shows stay [Idle]. */
sealed interface EpisodesLoad {
    data object Idle : EpisodesLoad
    data object Loading : EpisodesLoad
    data class Failed(val error: AppError) : EpisodesLoad
}

@HiltViewModel
class ShowDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val library: LibraryRepository,
    private val playback: PlaybackRepository,
    private val metadataRepository: MetadataRepository,
    private val episodeLoader: XtreamEpisodeLoader,
) : ViewModel() {
    val seriesKey = savedStateHandle.toRoute<ShowDetailRoute>().seriesKey

    val show: StateFlow<ShowEntity?> = library.observeShow(seriesKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val isFavorite: StateFlow<Boolean> = library.isFavorite(seriesKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val seasons: StateFlow<List<Season<EpisodeUi>>> = combine(
        library.observeEpisodes(seriesKey),
        playback.observeSeriesPositions(seriesKey),
    ) { episodes, positions ->
        val byKey = positions.associateBy { it.streamKey }
        val ui = episodes.map { e ->
            val p = byKey[e.key]
            EpisodeUi(
                stream = e,
                progress = p?.let { ResumePolicy.progress(it.positionMs, it.durationMs) } ?: 0f,
                watched = p?.let { it.watched || ResumePolicy.isWatched(it.positionMs, it.durationMs) } ?: false,
            )
        }
        SeasonGrouper.group(ui) { EpisodeRef(it.stream.key, it.stream.displayName, it.stream.season, it.stream.episode, it.stream.sortIndex) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _resume = MutableStateFlow<StreamEntity?>(null)
    /** Episode the main button plays: last unfinished, else the next one. */
    val resume: StateFlow<StreamEntity?> = _resume.asStateFlow()

    private val _selectedSeason = MutableStateFlow<Int?>(null)
    val selectedSeason: StateFlow<Int?> = _selectedSeason.asStateFlow()

    private val _metadata = MutableStateFlow<Metadata?>(null)
    val metadata: StateFlow<Metadata?> = _metadata.asStateFlow()

    private val _episodesLoad = MutableStateFlow<EpisodesLoad>(EpisodesLoad.Idle)
    val episodesLoad: StateFlow<EpisodesLoad> = _episodesLoad.asStateFlow()

    init {
        viewModelScope.launch {
            val s = library.observeShow(seriesKey).filterNotNull().first()
            _metadata.value = metadataRepository.show(s.name, s.year)
        }
        viewModelScope.launch {
            // Episodes can arrive after the screen opened (on-demand loading), so follow both.
            combine(library.observeEpisodes(seriesKey), playback.observeSeriesPositions(seriesKey)) { _, _ -> }.collect {
                val r = playback.resumeEpisode(seriesKey)
                _resume.value = r
                if (_selectedSeason.value == null && r != null) _selectedSeason.value = r.season ?: 1
            }
        }
        loadEpisodes()
    }

    /** Refreshes on-demand episodes on every visit; stored ones stay visible meanwhile. */
    fun loadEpisodes() {
        if (_episodesLoad.value == EpisodesLoad.Loading) return
        _episodesLoad.value = EpisodesLoad.Loading
        viewModelScope.launch {
            _episodesLoad.value = try {
                episodeLoader.refresh(seriesKey)
                EpisodesLoad.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EpisodesLoad.Failed(AppError.from(e))
            }
        }
    }

    fun selectSeason(season: Int) {
        _selectedSeason.value = season
    }

    fun toggleFavorite() {
        viewModelScope.launch { library.toggleFavorite(seriesKey, ItemKind.SHOW) }
    }

    fun setWatched(episode: StreamEntity, watched: Boolean) {
        viewModelScope.launch { playback.setWatched(episode, watched) }
    }

    fun setSeasonWatched(season: Season<EpisodeUi>, watched: Boolean) {
        viewModelScope.launch { season.episodes.forEach { playback.setWatched(it.stream, watched) } }
    }
}
