package pm.koc.poptv.data.repo

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pm.koc.poptv.data.db.AppDatabase
import pm.koc.poptv.data.net.AppError
import pm.koc.poptv.data.prefs.SettingsRepository
import pm.koc.poptv.data.work.WorkScheduler
import pm.koc.poptv.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ImportState {
    data object Idle : ImportState
    data class Running(val progress: ImportProgress, val isLogin: Boolean) : ImportState
    data class Success(val entries: Int, val isLogin: Boolean) : ImportState
    data class Failed(val error: AppError, val isLogin: Boolean) : ImportState
}

/** Entry point for logging in, refreshing and logging out. Only one import runs at a time. */
@Singleton
class PlaylistRepository @Inject constructor(
    private val importer: PlaylistImporter,
    private val settings: SettingsRepository,
    private val db: AppDatabase,
    private val workScheduler: WorkScheduler,
    private val epg: EpgRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()
    private var job: Job? = null

    /** Imports [url]; on success it is stored (encrypted) as the playlist. */
    fun login(url: String) = start(url.trim(), isLogin = true)

    /** Re-imports the stored playlist in the background (UI-triggered). */
    fun refreshNow() {
        scope.launch {
            val url = settings.playlistUrl() ?: return@launch
            start(url, isLogin = false)
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = ImportState.Idle
    }

    fun acknowledge() {
        if (_state.value !is ImportState.Running) _state.value = ImportState.Idle
    }

    private fun start(url: String, isLogin: Boolean) {
        if (_state.value is ImportState.Running) return
        _state.value = ImportState.Running(ImportProgress(ImportProgress.Phase.CONNECTING), isLogin)
        job = scope.launch {
            val result = runImport(url, isLogin) { progress ->
                _state.value = ImportState.Running(progress, isLogin)
            }
            _state.value = result.fold(
                onSuccess = { ImportState.Success(it, isLogin) },
                onFailure = { e ->
                    if (e is CancellationException) ImportState.Idle else ImportState.Failed(AppError.from(e), isLogin)
                },
            )
        }
    }

    /** Background refresh used by the worker. */
    suspend fun refreshFromWorker(): Result<Int> {
        val url = settings.playlistUrl() ?: return Result.failure(AppError.InvalidUrl())
        return runImport(url, isLogin = false) {}
    }

    private suspend fun runImport(url: String, isLogin: Boolean, onProgress: (ImportProgress) -> Unit): Result<Int> =
        mutex.withLock {
            try {
                val summary = importer.import(url, onProgress)
                if (isLogin) {
                    settings.setPlaylistUrl(url)
                    settings.setEpgNoticeShown(false)
                }
                settings.setHeaderEpgUrls(summary.epgUrls)
                settings.setLastPlaylistRefresh(System.currentTimeMillis(), summary.entries)
                workScheduler.schedule()
                // The guide is refreshed after login, and re-matched after every refresh.
                if (isLogin) epg.refreshInBackground() else epg.rematch()
                Result.success(summary.entries)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Import failed: ${e.javaClass.simpleName} ${e.message}")
                Result.failure(AppError.from(e))
            }
        }

    /** Removes the playlist and all local data (favorites, history, positions, guide, settings). */
    suspend fun logout() {
        job?.cancel()
        epg.cancel()
        workScheduler.cancelAll()
        withContext(Dispatchers.IO) { db.clearAllTables() }
        settings.clearAll()
        _state.value = ImportState.Idle
    }

    private companion object {
        const val TAG = "PlaylistRepository"
    }
}
