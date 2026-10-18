package pm.koc.poptv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pm.koc.poptv.data.net.UserAgentHolder
import pm.koc.poptv.data.prefs.SettingsRepository
import pm.koc.poptv.data.work.WorkScheduler
import pm.koc.poptv.di.ApplicationScope
import javax.inject.Inject

@HiltAndroidApp
class PopTvApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var userAgent: UserAgentHolder
    @Inject lateinit var workScheduler: WorkScheduler
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            settings.settings.map { it.userAgent }.distinctUntilChanged().collect { userAgent.value = it }
        }
        appScope.launch {
            // Make sure periodic refreshes exist (e.g. after an app update) with the configured intervals.
            settings.settings.map { it.playlistRefreshHours to it.epgRefreshHours }
                .distinctUntilChanged()
                .collect { workScheduler.schedule() }
        }
    }
}
