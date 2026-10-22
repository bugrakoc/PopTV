package pm.koc.poptv.ui.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaSession
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.net.UserAgentHolder
import pm.koc.poptv.data.prefs.AppSettings
import pm.koc.poptv.data.prefs.DecoderMode
import pm.koc.poptv.data.prefs.SettingsRepository
import pm.koc.poptv.data.repo.EpgRepository
import pm.koc.poptv.data.repo.LibraryRepository
import pm.koc.poptv.data.repo.OriginalLanguageRepository
import pm.koc.poptv.data.repo.PlaybackRepository
import pm.koc.poptv.di.ApplicationScope
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.domain.ItemKind
import pm.koc.poptv.domain.ReconnectPolicy
import pm.koc.poptv.domain.ResumePolicy
import pm.koc.poptv.player.PlayRequest
import pm.koc.poptv.player.PlaybackErrorKind
import pm.koc.poptv.player.PlaybackErrors
import pm.koc.poptv.player.PlayerFactory
import pm.koc.poptv.player.PlayerStatus
import pm.koc.poptv.player.PlayerUiState
import pm.koc.poptv.player.ResizeMode
import pm.koc.poptv.player.TrackDefaults
import pm.koc.poptv.player.TrackKind
import pm.koc.poptv.player.TrackOption
import pm.koc.poptv.player.UpNext
import pm.koc.poptv.ui.nav.PlayerRoute
import java.util.Locale
import javax.inject.Inject

/**
 * Owns the ExoPlayer instance for the player screen (so it survives recomposition) and implements
 * reconnects, zapping, resume positions and the up-next flow. The player is released in [onCleared].
 */
@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val factory: PlayerFactory,
    private val library: LibraryRepository,
    private val playback: PlaybackRepository,
    private val epg: EpgRepository,
    private val originalLanguages: OriginalLanguageRepository,
    private val settingsRepository: SettingsRepository,
    private val userAgent: UserAgentHolder,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<PlayerRoute>()

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    /** Current player; replaced when falling back to software decoding. */
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()

    private val _ui = MutableStateFlow(PlayerUiState(zapContext = route.category))
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private var session: MediaSession? = null
    private var current: StreamEntity? = null
    private var nextEpisode: StreamEntity? = null
    private var decoderMode = DecoderMode.HARDWARE
    private var triedSoftwareFallback = false
    private var forceHls = false
    private var triedHlsFallback = false
    private var errorRetries = 0
    private val reconnect = ReconnectPolicy()
    private var stopped = false
    private var upNextDismissed = false
    private var bufferingSince = -1L
    private var lastSaveAt = 0L
    private var droppedFrames = 0
    /** Stream whose default track languages are applied to the current player. */
    private var trackDefaultsKey: String? = null
    /** Waiting for a movie's or episode's tracks to pick its default audio and subtitle tracks. */
    private var trackRulesPending = false
    /** TMDB original language of [originalLanguageKey]'s title, looked up once per stream. */
    private var originalLanguage: String? = null
    private var originalLanguageKey: String? = null

    private var retryJob: Job? = null
    private var bannerJob: Job? = null
    private var numberJob: Job? = null
    private var zapJob: Job? = null
    private var upNextJob: Job? = null
    private var favoriteJob: Job? = null
    private var epgJob: Job? = null

    init {
        library.resetRecentSnapshot()
        viewModelScope.launch {
            val s = settingsRepository.current()
            _settings.value = s
            decoderMode = s.decoderMode
            createPlayer()
            val stream = library.stream(route.streamKey)
            if (stream == null) {
                _ui.update { it.copy(status = PlayerStatus.Failed(PlaybackErrorKind.NOT_FOUND)) }
            } else {
                play(stream, startOver = route.startOver)
            }
            startTicker()
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { _settings.value = it }
        }
    }

    // ---------------------------------------------------------------- player lifecycle

    private fun createPlayer() {
        val old = _player.value
        val p = factory.createPlayer(_settings.value, decoderMode)
        p.addListener(listener)
        p.addAnalyticsListener(analytics)
        _player.value = p
        trackDefaultsKey = null
        if (session == null) {
            session = runCatching {
                MediaSession.Builder(context, p).setId("poptv-${System.nanoTime()}").build()
            }.onFailure { Log.w(TAG, "MediaSession unavailable: ${it.message}") }.getOrNull()
        } else {
            session?.player = p
        }
        old?.let {
            it.removeListener(listener)
            it.removeAnalyticsListener(analytics)
            it.release()
        }
        _ui.update { it.copy(info = it.info.copy(decoderMode = decoderMode, tunneling = _settings.value.tunneling)) }
        Log.i(TAG, "Player created (decoder mode $decoderMode, tunneling ${_settings.value.tunneling}, buffer ${_settings.value.bufferMode})")
    }

    override fun onCleared() {
        val p = _player.value
        val stream = current
        if (p != null && stream != null && stream.type != ContentType.LIVE && p.currentMediaItem?.mediaId == stream.url) {
            val pos = p.currentPosition
            val dur = p.duration.takeIf { it != C.TIME_UNSET } ?: 0
            appScope.launch { playback.save(stream, pos, dur) }
        }
        session?.release()
        session = null
        p?.removeListener(listener)
        p?.removeAnalyticsListener(analytics)
        p?.release()
        _player.value = null
    }

    /** Activity went to the background: save, pause VOD, stop live streams (frees the provider connection). */
    fun onStop() {
        val p = _player.value ?: return
        stopped = true
        retryJob?.cancel()
        saveNow()
        if (_ui.value.isLive) p.stop() else p.pause()
    }

    fun onStart() {
        if (!stopped) return
        stopped = false
        val p = _player.value ?: return
        if (_ui.value.isLive) {
            current?.let { play(it, recordHistory = false) }
        } else if (p.playbackState == Player.STATE_IDLE) {
            p.prepare()
        }
    }

    // ---------------------------------------------------------------- playback

    private fun play(
        stream: StreamEntity,
        startOver: Boolean = false,
        positionMs: Long? = null,
        recordHistory: Boolean = true,
    ) {
        val p = _player.value ?: return
        val previous = current
        if (previous != null && previous.key != stream.key && previous.type != ContentType.LIVE) saveNow()
        retryJob?.cancel()
        upNextJob?.cancel()
        val isLive = stream.type == ContentType.LIVE
        if (previous?.key != stream.key) {
            reconnect.reset()
            errorRetries = 0
            forceHls = false
            triedHlsFallback = false
            upNextDismissed = false
            droppedFrames = 0
            nextEpisode = null
            // A decoder fallback applies to the stream that needed it, not to the next channel.
            if (triedSoftwareFallback && decoderMode != _settings.value.decoderMode) {
                triedSoftwareFallback = false
                decoderMode = _settings.value.decoderMode
                createPlayer()
            }
        }
        current = stream
        bufferingSince = -1
        _ui.update {
            it.copy(
                stream = stream,
                isLive = isLive,
                status = PlayerStatus.Loading,
                upNext = null,
                finished = false,
                positionMs = 0,
                durationMs = 0,
                numberNotFound = false,
                programmes = if (previous?.key == stream.key) it.programmes else emptyList(),
                info = it.info.copy(url = stream.url, videoFormat = null, audioFormat = null, droppedFrames = 0),
            )
        }
        observeFavorite(stream)

        viewModelScope.launch {
            val start: Long = when {
                isLive -> C.TIME_UNSET
                positionMs != null -> positionMs
                startOver -> 0
                else -> playback.position(stream.key).let { saved ->
                    ResumePolicy.startPosition(saved?.positionMs, saved?.durationMs, saved?.watched ?: false)
                }
            }
            val request = PlayRequest(
                url = stream.url,
                title = stream.displayName,
                isLive = isLive,
                userAgent = stream.userAgent,
                referrer = stream.referrer,
                forceHls = forceHls,
                subtitleUrl = stream.subtitleUrl,
            )
            if (!isLive && originalLanguageKey != stream.key) {
                originalLanguage = lookupOriginalLanguage(stream)
                originalLanguageKey = stream.key
            }
            val source = factory.createMediaSource(request, userAgent.value)
            val player = _player.value ?: return@launch
            // Reconnects keep the user's manual track choices; a new stream or player starts from the defaults.
            if (trackDefaultsKey != stream.key) {
                applyTrackDefaults(player, isLive)
                trackDefaultsKey = stream.key
            }
            if (start == C.TIME_UNSET) player.setMediaSource(source, true) else player.setMediaSource(source, start)
            player.setPlaybackSpeed(if (isLive) 1f else _ui.value.speed)
            player.prepare()
            player.playWhenReady = true

            if (isLive) {
                if (recordHistory) library.recordChannelWatched(stream.key)
                showBanner()
                loadProgrammes(stream)
            } else if (stream.type == ContentType.SERIES) {
                nextEpisode = playback.nextEpisode(stream)
            }
        }
    }

    private fun replayCurrent() {
        val stream = current ?: return
        val p = _player.value
        val pos = if (_ui.value.isLive) null else p?.currentPosition?.coerceAtLeast(0)
        play(stream, positionMs = pos, recordHistory = false)
    }

    fun retryNow() {
        reconnect.reset()
        errorRetries = 0
        replayCurrent()
    }

    private fun observeFavorite(stream: StreamEntity) {
        favoriteJob?.cancel()
        val key = if (stream.type == ContentType.SERIES) stream.seriesKey ?: stream.key else stream.key
        favoriteJob = viewModelScope.launch {
            library.isFavorite(key).collectLatest { fav -> _ui.update { it.copy(isFavorite = fav) } }
        }
    }

    fun toggleFavorite() {
        val stream = current ?: return
        viewModelScope.launch {
            when (stream.type) {
                ContentType.LIVE -> library.toggleFavorite(stream.key, ItemKind.CHANNEL)
                ContentType.MOVIE -> library.toggleFavorite(stream.key, ItemKind.MOVIE)
                ContentType.SERIES -> library.toggleFavorite(stream.seriesKey ?: stream.key, ItemKind.SHOW)
            }
        }
    }

    // ---------------------------------------------------------------- errors & reconnects

    private fun onError(error: PlaybackException) {
        val http = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        val kind = PlaybackErrors.classify(error.errorCode, http)
        Log.w(TAG, "Playback error ${error.errorCodeName} (http=$http, kind=$kind): ${error.message}")
        val p = _player.value ?: return

        if (kind == PlaybackErrorKind.BEHIND_LIVE_WINDOW) {
            p.seekToDefaultPosition()
            p.prepare()
            return
        }
        if (kind == PlaybackErrorKind.DECODER && decoderMode != DecoderMode.SOFTWARE && !triedSoftwareFallback) {
            Log.w(TAG, "Decoder failure; retrying with software-preferred decoders")
            triedSoftwareFallback = true
            decoderMode = DecoderMode.SOFTWARE
            val pos = if (_ui.value.isLive) null else p.currentPosition
            createPlayer()
            current?.let { play(it, positionMs = pos, recordHistory = false) }
            return
        }
        if (kind == PlaybackErrorKind.UNSUPPORTED_FORMAT && !triedHlsFallback && !forceHls) {
            // URLs without an extension are sometimes HLS playlists rather than progressive streams.
            Log.w(TAG, "Unrecognized container; retrying as HLS")
            triedHlsFallback = true
            forceHls = true
            replayCurrent()
            return
        }
        errorRetries++
        val delayMs = if (PlaybackErrors.isRetryable(kind) && errorRetries <= PlaybackErrors.maxRetries(kind)) {
            reconnect.nextDelay()
        } else {
            null
        }
        if (delayMs == null || stopped) {
            _ui.update { it.copy(status = PlayerStatus.Failed(kind)) }
            return
        }
        _ui.update { it.copy(status = PlayerStatus.Reconnecting(reconnect.attempts, reconnect.maxRetries)) }
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            delay(delayMs)
            replayCurrent()
        }
    }

    // ---------------------------------------------------------------- ticker: progress, stalls, saving, up next

    private fun startTicker() {
        viewModelScope.launch {
            while (isActive) {
                tick()
                delay(500)
            }
        }
    }

    private fun tick() {
        val p = _player.value ?: return
        val now = System.currentTimeMillis()
        val duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0
        val position = p.currentPosition.coerceAtLeast(0)
        _ui.update { it.copy(positionMs = position, durationMs = duration, bufferedMs = p.bufferedPosition) }

        if (p.isPlaying) reconnect.onPlaying(now) else reconnect.onNotPlaying()

        // Stall watchdog: buffering for too long while we want to play counts as an error.
        if (p.playbackState == Player.STATE_BUFFERING && p.playWhenReady && !stopped) {
            if (bufferingSince < 0) bufferingSince = now
            val limit = if (_ui.value.isLive) LIVE_STALL_MS else VOD_STALL_MS
            if (now - bufferingSince > limit && retryJob?.isActive != true) {
                Log.w(TAG, "Stalled for ${now - bufferingSince} ms; reconnecting")
                bufferingSince = -1
                onError(PlaybackException("stall", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
            }
        } else {
            bufferingSince = -1
        }

        val stream = current ?: return
        if (stream.type != ContentType.LIVE && p.isPlaying && p.currentMediaItem?.mediaId == stream.url &&
            now - lastSaveAt >= ResumePolicy.SAVE_INTERVAL_MS
        ) {
            lastSaveAt = now
            viewModelScope.launch { playback.save(stream, position, duration) }
        }

        val next = nextEpisode
        if (next != null && _settings.value.autoplayNext && !upNextDismissed && _ui.value.upNext == null &&
            p.isPlaying && ResumePolicy.shouldShowUpNext(position, duration)
        ) {
            startUpNextCountdown(next)
        }
    }

    private fun startUpNextCountdown(next: StreamEntity) {
        upNextJob?.cancel()
        upNextJob = viewModelScope.launch {
            for (s in ResumePolicy.UP_NEXT_COUNTDOWN_SECONDS downTo 1) {
                _ui.update { it.copy(upNext = UpNext(next, s)) }
                delay(1000)
            }
            playNextEpisode()
        }
    }

    fun playNextEpisode() {
        val next = nextEpisode ?: _ui.value.upNext?.episode ?: return
        upNextJob?.cancel()
        current?.let { cur ->
            // Finishing an episode early via "play next" still marks it watched.
            val p = _player.value
            val dur = p?.duration?.takeIf { it != C.TIME_UNSET } ?: 0
            if (dur > 0) viewModelScope.launch { playback.save(cur, dur, dur) }
        }
        play(next, startOver = false)
    }

    fun dismissUpNext() {
        upNextJob?.cancel()
        upNextDismissed = true
        _ui.update { it.copy(upNext = null) }
    }

    private fun onEnded() {
        val stream = current ?: return
        if (stream.type == ContentType.LIVE) {
            // A live stream that "ends" has dropped: reconnect.
            onError(PlaybackException("live ended", null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
            return
        }
        val p = _player.value
        val dur = p?.duration?.takeIf { it != C.TIME_UNSET } ?: 0
        viewModelScope.launch { playback.save(stream, dur, dur) }
        val next = nextEpisode
        when {
            next != null && _settings.value.autoplayNext && !upNextDismissed -> playNextEpisode()
            next != null -> {
                upNextJob?.cancel()
                _ui.update { it.copy(upNext = UpNext(next, null), status = PlayerStatus.Ended) }
            }
            else -> _ui.update { it.copy(finished = true, status = PlayerStatus.Ended) }
        }
    }

    /** Saves the VOD position now (pause, back, background). */
    fun saveNow() {
        val stream = current ?: return
        if (stream.type == ContentType.LIVE) return
        val p = _player.value ?: return
        // While switching items the player may still report the previous item's position.
        if (p.currentMediaItem?.mediaId != stream.url) return
        val pos = p.currentPosition
        val dur = p.duration.takeIf { it != C.TIME_UNSET } ?: 0
        lastSaveAt = System.currentTimeMillis()
        appScope.launch { playback.save(stream, pos, dur) }
    }

    // ---------------------------------------------------------------- transport controls

    fun togglePlayPause() {
        val p = _player.value ?: return
        when {
            _ui.value.status is PlayerStatus.Failed -> retryNow()
            p.playbackState == Player.STATE_ENDED -> { p.seekTo(0); p.play() }
            p.isPlaying -> p.pause()
            else -> {
                if (p.playbackState == Player.STATE_IDLE) p.prepare()
                p.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val p = _player.value ?: return
        if (_ui.value.isLive) return
        p.seekTo(positionMs.coerceAtLeast(0))
    }

    fun setSpeed(speed: Float) {
        _player.value?.playbackParameters = PlaybackParameters(speed)
        _ui.update { it.copy(speed = speed) }
    }

    fun setResizeMode(mode: ResizeMode) = _ui.update { it.copy(resizeMode = mode) }

    fun selectTrack(option: TrackOption?) {
        val p = _player.value ?: return
        val builder = p.trackSelectionParameters.buildUpon()
        when {
            option == null -> builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            else -> {
                val type = if (option.kind == TrackKind.AUDIO) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
                val group = p.currentTracks.groups.getOrNull(option.groupIndex) ?: return
                builder.setTrackTypeDisabled(type, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            }
        }
        p.trackSelectionParameters = builder.build()
    }

    /**
     * TMDB original language of a movie or an episode's show. Cached lookups (the detail screen already
     * made one) return at once; a slow network lookup is not waited for, and null when TMDB is off.
     */
    private suspend fun lookupOriginalLanguage(stream: StreamEntity): String? {
        if (_settings.value.audioLanguage != AppSettings.ORIGINAL) return null
        return withTimeoutOrNull(ORIGINAL_LOOKUP_MS) { originalLanguages.of(stream) }
    }

    /**
     * Drops the previous stream's manual track choices and applies the language settings. Subtitles of
     * movies and episodes stay off until [applyTrackRules] knows which audio track plays.
     */
    private fun applyTrackDefaults(p: ExoPlayer, isLive: Boolean) {
        val audio = TrackDefaults.startAudioLanguage(_settings.value, isLive, Locale.getDefault().language, originalLanguage)
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .clearOverrides()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isLive)
            .setPreferredAudioLanguage(audio)
            .setPreferredTextLanguage(null)
            .build()
        trackRulesPending = !isLive
    }

    /**
     * Once a movie's or episode's tracks are known: picks the default audio track ([TrackDefaults.pickAudio])
     * and the subtitles for that audio's language ([TrackDefaults.subtitleLanguages]).
     */
    private fun applyTrackRules(tracks: Tracks) {
        if (tracks.groups.isEmpty()) return
        trackRulesPending = false
        val p = _player.value ?: return
        val s = _settings.value
        val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { g -> (0 until g.length).map { g to it } }
        val infos = audio.map { (g, i) ->
            val f = g.getTrackFormat(i)
            TrackDefaults.AudioTrack(f.language?.let(Util::normalizeLanguageCode), f.label, g.isTrackSupported(i, true))
        }
        val pick = TrackDefaults.pickAudio(infos, s, originalLanguage, Locale.getDefault().language)
        val builder = p.trackSelectionParameters.buildUpon()
        var chosen = audio.indexOfFirst { (g, i) -> g.isTrackSelected(i) }
        if (pick != null && pick.index != chosen) {
            val (g, i) = audio[pick.index]
            builder.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
            chosen = pick.index
        }
        val language = infos.getOrNull(chosen)?.language

        val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.flatMap { g -> (0 until g.length).map { g to it } }
        val textInfos = text.map { (g, i) ->
            val f = g.getTrackFormat(i)
            TrackDefaults.TextTrack(
                language = f.language?.let(Util::normalizeLanguageCode),
                label = f.label,
                forced = f.selectionFlags and C.SELECTION_FLAG_FORCED != 0,
                hearingImpaired = f.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND != 0,
                supported = g.isTrackSupported(i, true),
            )
        }
        val subtitleLanguages = TrackDefaults.subtitleLanguages(language, s)
        val subtitles = TrackDefaults.pickSubtitles(textInfos, subtitleLanguages)
        if (subtitles != null) {
            val (g, i) = text[subtitles.index]
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
        }
        fun describe(language: String?, label: String?) = listOfNotNull(language ?: "und", label).joinToString("/")
        Log.i(
            TAG,
            "Audio tracks ${infos.map { describe(it.language, it.label) }}, original ${originalLanguage ?: "unknown"}: " +
                "playing ${language ?: "und"} (${pick?.reason ?: "player default"}). " +
                "Subtitle tracks ${textInfos.map { describe(it.language, it.label) }}, wanted $subtitleLanguages: " +
                (subtitles?.let { describe(textInfos[it.index].language, textInfos[it.index].label) } ?: "off"),
        )
        p.trackSelectionParameters = builder.build()
    }

    fun selectAutoAudio() {
        val p = _player.value ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()
    }

    // ---------------------------------------------------------------- live: zapping, banner, numbers

    fun showBanner() {
        bannerJob?.cancel()
        _ui.update { it.copy(bannerVisible = true) }
        bannerJob = viewModelScope.launch {
            delay(BANNER_MS)
            _ui.update { it.copy(bannerVisible = false) }
        }
    }

    fun hideBanner() {
        bannerJob?.cancel()
        _ui.update { it.copy(bannerVisible = false) }
    }

    /** Switches channel; rapid presses update the banner immediately and tune once the user pauses. */
    fun zap(forward: Boolean) {
        val base = _ui.value.stream ?: return
        if (base.type != ContentType.LIVE) return
        zapJob?.cancel()
        zapJob = viewModelScope.launch {
            val target = library.adjacentChannel(base, _ui.value.zapContext, forward) ?: return@launch
            if (target.key == base.key) return@launch
            _ui.update { it.copy(stream = target, programmes = emptyList()) }
            showBanner()
            loadProgrammes(target)
            delay(ZAP_DEBOUNCE_MS)
            play(target)
        }
    }

    fun playChannel(stream: StreamEntity, context: String?) {
        zapJob?.cancel()
        library.resetRecentSnapshot()
        _ui.update { it.copy(zapContext = context) }
        play(stream)
    }

    fun previousChannel() {
        viewModelScope.launch {
            val key = library.previousChannelKey() ?: return@launch
            if (key == current?.key) return@launch
            library.stream(key)?.let { play(it) }
        }
    }

    fun digit(d: Int) {
        if (!_ui.value.isLive) return
        val next = (_ui.value.numberInput + d).takeLast(5)
        _ui.update { it.copy(numberInput = next, numberNotFound = false) }
        numberJob?.cancel()
        numberJob = viewModelScope.launch {
            delay(NUMBER_ENTRY_MS)
            commitNumber()
        }
    }

    fun commitNumber() {
        val input = _ui.value.numberInput
        numberJob?.cancel()
        if (input.isEmpty()) return
        viewModelScope.launch {
            val stream = input.toIntOrNull()?.let { library.channelByNumber(it) }
            if (stream != null) {
                _ui.update { it.copy(numberInput = "") }
                play(stream)
            } else {
                _ui.update { it.copy(numberNotFound = true) }
                delay(1500)
                _ui.update { it.copy(numberInput = "", numberNotFound = false) }
            }
        }
    }

    fun clearNumber() {
        numberJob?.cancel()
        _ui.update { it.copy(numberInput = "", numberNotFound = false) }
    }

    private fun loadProgrammes(stream: StreamEntity) {
        epgJob?.cancel()
        val id = stream.epgChannelId ?: return
        epgJob = viewModelScope.launch {
            while (isActive) {
                val list = runCatching { epg.upcoming(id, 2) }.getOrDefault(emptyList())
                if (_ui.value.stream?.key == stream.key) _ui.update { it.copy(programmes = list) }
                delay(60_000)
            }
        }
    }

    // ---------------------------------------------------------------- listeners

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> _ui.update {
                    if (it.status is PlayerStatus.Reconnecting) it else it.copy(status = PlayerStatus.Buffering)
                }
                Player.STATE_READY -> {
                    errorRetries = 0
                    _ui.update {
                        it.copy(status = if (_player.value?.isPlaying == true) PlayerStatus.Playing else PlayerStatus.Paused)
                    }
                }
                Player.STATE_ENDED -> onEnded()
                Player.STATE_IDLE -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _ui.update {
                val status = when {
                    isPlaying -> PlayerStatus.Playing
                    it.status == PlayerStatus.Playing && _player.value?.playbackState == Player.STATE_READY -> PlayerStatus.Paused
                    else -> it.status
                }
                it.copy(isPlaying = isPlaying, status = status)
            }
            if (!isPlaying) saveNow()
        }

        override fun onPlayerError(error: PlaybackException) = onError(error)

        override fun onTracksChanged(tracks: Tracks) = updateTracks(tracks)
    }

    private val analytics = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            Log.i(TAG, "Video decoder: $decoderName (init ${initializationDurationMs} ms, mode $decoderMode)")
            _ui.update { it.copy(info = it.info.copy(videoDecoder = decoderName)) }
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            Log.i(TAG, "Audio decoder: $decoderName")
            _ui.update { it.copy(info = it.info.copy(audioDecoder = decoderName)) }
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            Log.i(TAG, "Video format: ${format.sampleMimeType} ${format.codecs} ${format.width}x${format.height} @${format.frameRate} ${format.bitrate}bps color=${format.colorInfo}")
            _ui.update { it.copy(info = it.info.copy(videoFormat = format)) }
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            Log.i(TAG, "Audio format: ${format.sampleMimeType} ${format.channelCount}ch ${format.sampleRate}Hz lang=${format.language}")
            _ui.update { it.copy(info = it.info.copy(audioFormat = format)) }
        }

        override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
            this@PlayerViewModel.droppedFrames += droppedFrames
            val total = this@PlayerViewModel.droppedFrames
            _ui.update { it.copy(info = it.info.copy(droppedFrames = total)) }
        }

        override fun onBandwidthEstimate(
            eventTime: AnalyticsListener.EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long,
        ) {
            _ui.update { it.copy(info = it.info.copy(bandwidthBps = bitrateEstimate)) }
        }
    }

    private fun updateTracks(tracks: Tracks) {
        if (trackRulesPending) applyTrackRules(tracks)
        val audio = ArrayList<TrackOption>()
        val text = ArrayList<TrackOption>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            val kind = when (group.type) {
                C.TRACK_TYPE_AUDIO -> TrackKind.AUDIO
                C.TRACK_TYPE_TEXT -> TrackKind.TEXT
                else -> return@forEachIndexed
            }
            for (i in 0 until group.length) {
                val f = group.getTrackFormat(i)
                val option = TrackOption(
                    kind = kind,
                    groupIndex = groupIndex,
                    trackIndex = i,
                    language = f.language?.takeIf { it != C.LANGUAGE_UNDETERMINED },
                    label = f.label,
                    mimeType = f.sampleMimeType,
                    codecs = f.codecs,
                    channels = f.channelCount,
                    bitrate = f.bitrate,
                    width = f.width,
                    height = f.height,
                    selected = group.isTrackSelected(i),
                    supported = group.isTrackSupported(i, true),
                    forced = f.selectionFlags and C.SELECTION_FLAG_FORCED != 0,
                )
                if (kind == TrackKind.AUDIO) audio += option else text += option
            }
        }
        val textOff = text.none { it.selected }
        _ui.update { it.copy(audioTracks = audio, textTracks = text, subtitlesOff = textOff) }
    }

    private companion object {
        const val TAG = "PopTV-Player"
        const val LIVE_STALL_MS = 15_000L
        const val VOD_STALL_MS = 30_000L
        const val BANNER_MS = 5_000L
        const val ZAP_DEBOUNCE_MS = 350L
        const val NUMBER_ENTRY_MS = 2_000L
        const val ORIGINAL_LOOKUP_MS = 2_000L
    }
}
