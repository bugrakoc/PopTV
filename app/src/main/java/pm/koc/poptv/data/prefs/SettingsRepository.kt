package pm.koc.poptv.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "poptv_settings")

/**
 * Settings and small app state in DataStore. Secrets (playlist URL, guide URLs, TMDB key) are stored only
 * as Keystore-encrypted ciphertext via [SecretCipher].
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: SecretCipher,
) {
    private val store = context.dataStore

    private val prefs: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: Flow<AppSettings> = prefs.map { it.toSettings() }.distinctUntilChanged()
    val state: Flow<AppState> = prefs.map { it.toState() }.distinctUntilChanged()

    val isLoggedIn: Flow<Boolean> = prefs.map { it[K.playlistUrl] != null }.distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()
    suspend fun currentState(): AppState = state.first()

    // ---- Secrets ----

    suspend fun playlistUrl(): String? = prefs.first()[K.playlistUrl]?.let(cipher::decrypt)
    suspend fun setPlaylistUrl(url: String) = putSecret(K.playlistUrl, url)

    /** Guide URLs announced in the playlist header (may contain credentials). */
    suspend fun headerEpgUrls(): List<String> =
        prefs.first()[K.headerEpgUrls]?.let(cipher::decrypt)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
    suspend fun setHeaderEpgUrls(urls: List<String>) = putSecret(K.headerEpgUrls, urls.joinToString("\n"))

    suspend fun manualEpgUrl(): String = prefs.first()[K.manualEpgUrl]?.let(cipher::decrypt).orEmpty()
    suspend fun setManualEpgUrl(url: String) = putSecret(K.manualEpgUrl, url.trim())
    val hasManualEpgUrl: Flow<Boolean> = prefs.map { it[K.manualEpgUrl] != null }.distinctUntilChanged()

    suspend fun tmdbKey(): String = prefs.first()[K.tmdbKey]?.let(cipher::decrypt).orEmpty()
    suspend fun setTmdbKey(key: String) = putSecret(K.tmdbKey, key.trim())
    val hasTmdbKey: Flow<Boolean> = prefs.map { it[K.tmdbKey] != null }.distinctUntilChanged()

    private suspend fun putSecret(key: Preferences.Key<String>, value: String) {
        store.edit { if (value.isEmpty()) it.remove(key) else it[key] = cipher.encrypt(value) }
    }

    // ---- Settings ----

    suspend fun setUserAgent(value: String) = store.edit {
        it[K.userAgent] = value.trim().ifEmpty { AppSettings.DEFAULT_USER_AGENT }
    }
    suspend fun setPlaylistRefreshHours(value: Int) = store.edit { it[K.playlistRefreshHours] = value }
    suspend fun setEpgRefreshHours(value: Int) = store.edit { it[K.epgRefreshHours] = value }
    suspend fun setEpgOffsetMinutes(value: Int) = store.edit { it[K.epgOffset] = value }
    suspend fun setDecoderMode(value: DecoderMode) = store.edit { it[K.decoderMode] = value.name }
    suspend fun setTunneling(value: Boolean) = store.edit { it[K.tunneling] = value }
    suspend fun setBufferMode(value: BufferMode) = store.edit { it[K.bufferMode] = value.name }
    suspend fun setSubtitleSize(value: SubtitleSize) = store.edit { it[K.subtitleSize] = value.name }
    suspend fun setSubtitleColor(value: SubtitleColor) = store.edit { it[K.subtitleColor] = value.name }
    suspend fun setSubtitleBackground(value: Boolean) = store.edit { it[K.subtitleBackground] = value }
    suspend fun setAutoplayNext(value: Boolean) = store.edit { it[K.autoplayNext] = value }
    suspend fun setShowOriginalNames(value: Boolean) = store.edit { it[K.showOriginalNames] = value }
    suspend fun setInvertChannelKeys(value: Boolean) = store.edit { it[K.invertChannelKeys] = value }
    suspend fun setHideAdult(value: Boolean) = store.edit { it[K.hideAdult] = value }

    // ---- State ----

    suspend fun setLastPlaylistRefresh(time: Long, entries: Int) = store.edit {
        it[K.lastPlaylistRefresh] = time
        it[K.playlistEntryCount] = entries
    }
    suspend fun setLastEpgRefresh(time: Long) = store.edit { it[K.lastEpgRefresh] = time }
    suspend fun setEpgStatus(status: EpgStatus) = store.edit { it[K.epgStatus] = status.name }
    suspend fun setEpgNoticeShown(shown: Boolean) = store.edit { it[K.epgNoticeShown] = shown }
    suspend fun setAdultFilterApplied(value: Boolean) = store.edit { it[K.adultFilterApplied] = value }

    /** Channel zapping memory (live channel keys; not secret). */
    suspend fun lastChannels(): Pair<String?, String?> = prefs.first().let { it[K.lastChannel] to it[K.previousChannel] }
    suspend fun pushLastChannel(key: String) = store.edit {
        val current = it[K.lastChannel]
        if (current != key) {
            if (current != null) it[K.previousChannel] = current
            it[K.lastChannel] = key
        }
    }

    /** Removes everything (log out). */
    suspend fun clearAll() = store.edit { it.clear() }

    private fun Preferences.toSettings() = AppSettings(
        userAgent = this[K.userAgent] ?: AppSettings.DEFAULT_USER_AGENT,
        playlistRefreshHours = this[K.playlistRefreshHours] ?: 24,
        epgRefreshHours = this[K.epgRefreshHours] ?: 12,
        epgOffsetMinutes = this[K.epgOffset] ?: 0,
        decoderMode = enumOr(this[K.decoderMode], DecoderMode.HARDWARE),
        tunneling = this[K.tunneling] ?: false,
        bufferMode = enumOr(this[K.bufferMode], BufferMode.NORMAL),
        subtitleSize = enumOr(this[K.subtitleSize], SubtitleSize.NORMAL),
        subtitleColor = enumOr(this[K.subtitleColor], SubtitleColor.WHITE),
        subtitleBackground = this[K.subtitleBackground] ?: true,
        autoplayNext = this[K.autoplayNext] ?: true,
        showOriginalNames = this[K.showOriginalNames] ?: false,
        invertChannelKeys = this[K.invertChannelKeys] ?: false,
        hideAdult = this[K.hideAdult] ?: true,
    )

    private fun Preferences.toState() = AppState(
        lastPlaylistRefresh = this[K.lastPlaylistRefresh] ?: 0,
        lastEpgRefresh = this[K.lastEpgRefresh] ?: 0,
        epgStatus = enumOr(this[K.epgStatus], EpgStatus.UNKNOWN),
        epgNoticeShown = this[K.epgNoticeShown] ?: false,
        playlistEntryCount = this[K.playlistEntryCount] ?: 0,
        adultFilterApplied = this[K.adultFilterApplied] ?: false,
    )

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    private object K {
        val playlistUrl = stringPreferencesKey("playlist_url_enc")
        val headerEpgUrls = stringPreferencesKey("header_epg_urls_enc")
        val manualEpgUrl = stringPreferencesKey("manual_epg_url_enc")
        val tmdbKey = stringPreferencesKey("tmdb_key_enc")

        val userAgent = stringPreferencesKey("user_agent")
        val playlistRefreshHours = intPreferencesKey("playlist_refresh_hours")
        val epgRefreshHours = intPreferencesKey("epg_refresh_hours")
        val epgOffset = intPreferencesKey("epg_offset_minutes")
        val decoderMode = stringPreferencesKey("decoder_mode")
        val tunneling = booleanPreferencesKey("tunneling")
        val bufferMode = stringPreferencesKey("buffer_mode")
        val subtitleSize = stringPreferencesKey("subtitle_size")
        val subtitleColor = stringPreferencesKey("subtitle_color")
        val subtitleBackground = booleanPreferencesKey("subtitle_background")
        val autoplayNext = booleanPreferencesKey("autoplay_next")
        val showOriginalNames = booleanPreferencesKey("show_original_names")
        val invertChannelKeys = booleanPreferencesKey("invert_channel_keys")
        val hideAdult = booleanPreferencesKey("hide_adult")

        val lastPlaylistRefresh = longPreferencesKey("last_playlist_refresh")
        val playlistEntryCount = intPreferencesKey("playlist_entry_count")
        val adultFilterApplied = booleanPreferencesKey("adult_filter_applied")
        val lastEpgRefresh = longPreferencesKey("last_epg_refresh")
        val epgStatus = stringPreferencesKey("epg_status")
        val epgNoticeShown = booleanPreferencesKey("epg_notice_shown")
        val lastChannel = stringPreferencesKey("last_channel")
        val previousChannel = stringPreferencesKey("previous_channel")
    }
}
