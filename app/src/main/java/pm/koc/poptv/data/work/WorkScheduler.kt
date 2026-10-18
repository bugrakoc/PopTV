package pm.koc.poptv.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import pm.koc.poptv.data.prefs.SettingsRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules (or cancels) the periodic playlist and guide refreshes according to the settings. */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val workManager get() = WorkManager.getInstance(context)

    suspend fun schedule() {
        if (settings.playlistUrl() == null) return
        val s = settings.current()
        enqueue(PLAYLIST_WORK, s.playlistRefreshHours) { hours ->
            PeriodicWorkRequestBuilder<PlaylistRefreshWorker>(hours.toLong(), TimeUnit.HOURS)
                .setInitialDelay(hours.toLong(), TimeUnit.HOURS)
        }
        enqueue(EPG_WORK, s.epgRefreshHours) { hours ->
            PeriodicWorkRequestBuilder<EpgRefreshWorker>(hours.toLong(), TimeUnit.HOURS)
                .setInitialDelay(hours.toLong(), TimeUnit.HOURS)
        }
    }

    private fun enqueue(
        name: String,
        hours: Int,
        builder: (Int) -> PeriodicWorkRequest.Builder,
    ) {
        if (hours <= 0) {
            workManager.cancelUniqueWork(name)
            return
        }
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = builder(hours).setConstraints(constraints).addTag(name + "_" + hours).build()
        // UPDATE keeps the current schedule when nothing changed and applies a new interval otherwise.
        workManager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(PLAYLIST_WORK)
        workManager.cancelUniqueWork(EPG_WORK)
    }

    private companion object {
        const val PLAYLIST_WORK = "playlist_refresh"
        const val EPG_WORK = "epg_refresh"
    }
}
