package pm.koc.poptv

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pm.koc.poptv.data.net.UserAgentHolder
import pm.koc.poptv.data.repo.CategoryRepository
import pm.koc.poptv.data.prefs.SettingsRepository
import pm.koc.poptv.data.work.WorkScheduler
import pm.koc.poptv.di.ApplicationScope
import javax.inject.Inject

@HiltAndroidApp
class PopTvApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var userAgent: UserAgentHolder
    @Inject lateinit var workScheduler: WorkScheduler
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var okHttp: OkHttpClient
    @Inject lateinit var categories: CategoryRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    /** Coil: shared OkHttp client (custom User-Agent), bounded memory cache, on-disk logo/poster cache. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttp })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
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
        appScope.launch {
            // Keep the stored library in line with the adult filter: after toggling it, and on the first start
            // after an update that introduced it (that library still shows everything).
            combine(settings.settings, settings.state, settings.isLoggedIn) { s, state, loggedIn ->
                if (loggedIn && s.hideAdult != state.adultFilterApplied) s.hideAdult else null
            }.distinctUntilChanged().collect { hide -> if (hide != null) categories.applyAdultFilter(hide) }
        }
    }
}
