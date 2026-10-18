package pm.koc.poptv.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import pm.koc.poptv.data.db.AppDatabase
import pm.koc.poptv.data.net.UserAgentHolder
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Process-wide scope for work that must outlive a screen (imports started from the UI). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides fun streamDao(db: AppDatabase) = db.streamDao()
    @Provides fun categoryDao(db: AppDatabase) = db.categoryDao()
    @Provides fun showDao(db: AppDatabase) = db.showDao()
    @Provides fun overrideDao(db: AppDatabase) = db.overrideDao()
    @Provides fun favoriteDao(db: AppDatabase) = db.favoriteDao()
    @Provides fun historyDao(db: AppDatabase) = db.historyDao()
    @Provides fun positionDao(db: AppDatabase) = db.positionDao()
    @Provides fun epgDao(db: AppDatabase) = db.epgDao()
    @Provides fun metadataDao(db: AppDatabase) = db.metadataDao()

    @Provides
    @Singleton
    fun provideOkHttp(userAgent: UserAgentHolder): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            // Providers often bounce between http and https; follow both directions.
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.header("User-Agent") != null) {
                    chain.proceed(request)
                } else {
                    chain.proceed(request.newBuilder().header("User-Agent", userAgent.value).build())
                }
            }
            .build()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }
}
