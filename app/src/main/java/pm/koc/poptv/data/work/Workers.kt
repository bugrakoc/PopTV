package pm.koc.poptv.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import pm.koc.poptv.data.net.AppError
import pm.koc.poptv.data.repo.EpgRepository
import pm.koc.poptv.data.repo.PlaylistRepository

@HiltWorker
class PlaylistRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val playlists: PlaylistRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = playlists.refreshFromWorker()
        return result.fold(
            onSuccess = { Result.success() },
            onFailure = { e ->
                Log.w("PlaylistRefreshWorker", "Refresh failed: ${e.javaClass.simpleName}")
                // Transient network problems are retried with WorkManager's backoff; others wait for next period.
                if (e is AppError.Network || e is AppError.Timeout || e is AppError.NoHost) {
                    if (runAttemptCount < 3) Result.retry() else Result.failure()
                } else {
                    Result.failure()
                }
            },
        )
    }
}

@HiltWorker
class EpgRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val epg: EpgRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        if (epg.refresh()) Result.success() else if (runAttemptCount < 2) Result.retry() else Result.failure()
}
