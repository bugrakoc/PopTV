package pm.koc.poptv.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import pm.koc.poptv.R
import pm.koc.poptv.data.prefs.AppSettings
import pm.koc.poptv.data.prefs.BufferMode
import pm.koc.poptv.data.prefs.DecoderMode
import pm.koc.poptv.data.prefs.EpgStatus
import pm.koc.poptv.data.prefs.SubtitleColor
import pm.koc.poptv.data.prefs.SubtitleSize
import pm.koc.poptv.data.repo.EpgRefreshState
import pm.koc.poptv.data.repo.ImportState
import pm.koc.poptv.ui.components.Choice
import pm.koc.poptv.ui.components.ChoiceDialog
import pm.koc.poptv.ui.components.ConfirmDialog
import pm.koc.poptv.ui.components.ScreenScaffold
import pm.koc.poptv.ui.components.TextInputDialog
import pm.koc.poptv.ui.components.localizedMessage
import pm.koc.poptv.ui.components.requestFocusWithRetry
import pm.koc.poptv.ui.player.decoderModeLabel

private enum class SettingDialog {
    PLAYLIST_INTERVAL, EPG_INTERVAL, EPG_OFFSET, EPG_URL, DECODER, BUFFER, USER_AGENT,
    SUBTITLE_SIZE, SUBTITLE_COLOR, TMDB_KEY, LOGOUT,
}

@Composable
fun SettingsScreen(
    onOpenCategories: () -> Unit,
    onOpenAbout: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.values.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val epgState by viewModel.epgState.collectAsStateWithLifecycle()
    val hasTmdb by viewModel.hasTmdbKey.collectAsStateWithLifecycle()
    val hasManualEpg by viewModel.hasManualEpg.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<SettingDialog?>(null) }
    var manualEpgInitial by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { viewModel.loggedOut.collect { onLoggedOut() } }
    LaunchedEffect(Unit) { firstFocus.requestFocusWithRetry() }


    ScreenScaffold(title = stringResource(R.string.nav_settings)) {
        LazyColumn(state = rememberLazyListState(), contentPadding = PaddingValues(bottom = 32.dp)) {
            // ---- Playlist ----
            header(R.string.settings_section_playlist)
            item {
                val running = importState is ImportState.Running
                val failed = (importState as? ImportState.Failed)?.takeIf { !it.isLogin }
                SettingRow(
                    title = stringResource(R.string.settings_refresh_playlist),
                    value = when {
                        running -> stringResource(R.string.home_updating_playlist)
                        failed != null -> failed.error.localizedMessage()
                        else -> stringResource(
                            R.string.settings_last_refresh,
                            ago(state.lastPlaylistRefresh),
                            pluralStringResource(R.plurals.import_entries, state.playlistEntryCount, state.playlistEntryCount),
                        )
                    },
                    onClick = { if (!running) viewModel.refreshPlaylist() },
                    modifier = Modifier.focusRequester(firstFocus),
                )
            }
            item { SettingRow(stringResource(R.string.settings_auto_refresh), hours(s.playlistRefreshHours)) { dialog = SettingDialog.PLAYLIST_INTERVAL } }
            item { SettingRow(stringResource(R.string.settings_logout), stringResource(R.string.settings_logout_detail)) { dialog = SettingDialog.LOGOUT } }

            // ---- EPG ----
            header(R.string.settings_section_epg)
            item {
                val epgValue = when (val e = epgState) {
                    is EpgRefreshState.Running -> pluralStringResource(R.plurals.settings_epg_loading, e.programmes, e.programmes)
                    is EpgRefreshState.Done -> pluralStringResource(R.plurals.settings_epg_done, e.programmes, e.programmes) + " · " +
                        pluralStringResource(R.plurals.settings_epg_matched, e.matchedChannels, e.matchedChannels)
                    EpgRefreshState.Unavailable -> stringResource(R.string.guide_unavailable)
                    EpgRefreshState.Idle -> if (state.epgStatus == EpgStatus.UNAVAILABLE) stringResource(R.string.guide_unavailable)
                    else stringResource(R.string.settings_last_refresh_short, ago(state.lastEpgRefresh))
                }
                SettingRow(stringResource(R.string.settings_refresh_epg), epgValue) {
                    if (epgState !is EpgRefreshState.Running) viewModel.refreshEpg()
                }
            }
            item { SettingRow(stringResource(R.string.settings_auto_refresh), hours(s.epgRefreshHours)) { dialog = SettingDialog.EPG_INTERVAL } }
            item {
                SettingRow(
                    stringResource(R.string.settings_epg_url),
                    stringResource(if (hasManualEpg) R.string.settings_epg_url_set else R.string.settings_epg_url_auto),
                ) {
                    scope.launch {
                        manualEpgInitial = viewModel.manualEpgUrl()
                        dialog = SettingDialog.EPG_URL
                    }
                }
            }
            item { SettingRow(stringResource(R.string.settings_epg_offset), offsetLabel(s.epgOffsetMinutes)) { dialog = SettingDialog.EPG_OFFSET } }

            // ---- Player ----
            header(R.string.settings_section_player)
            item { SettingRow(stringResource(R.string.settings_decoder), stringResource(decoderModeLabel(s.decoderMode))) { dialog = SettingDialog.DECODER } }
            item { SwitchRow(stringResource(R.string.settings_tunneling), stringResource(R.string.settings_tunneling_detail), s.tunneling, viewModel::setTunneling) }
            item { SettingRow(stringResource(R.string.settings_buffer), stringResource(bufferLabel(s.bufferMode))) { dialog = SettingDialog.BUFFER } }
            item { SettingRow(stringResource(R.string.settings_user_agent), s.userAgent) { dialog = SettingDialog.USER_AGENT } }
            item { SettingRow(stringResource(R.string.settings_subtitle_size), stringResource(subtitleSizeLabel(s.subtitleSize))) { dialog = SettingDialog.SUBTITLE_SIZE } }
            item { SettingRow(stringResource(R.string.settings_subtitle_color), stringResource(if (s.subtitleColor == SubtitleColor.WHITE) R.string.color_white else R.string.color_yellow)) { dialog = SettingDialog.SUBTITLE_COLOR } }
            item { SwitchRow(stringResource(R.string.settings_subtitle_background), null, s.subtitleBackground, viewModel::setSubtitleBackground) }
            item { SwitchRow(stringResource(R.string.settings_autoplay_next), stringResource(R.string.settings_autoplay_next_detail), s.autoplayNext, viewModel::setAutoplayNext) }
            item { SwitchRow(stringResource(R.string.settings_invert_channel_keys), stringResource(R.string.settings_invert_channel_keys_detail), s.invertChannelKeys, viewModel::setInvertChannelKeys) }

            // ---- Content ----
            header(R.string.settings_section_content)
            item { SettingRow(stringResource(R.string.settings_categories), stringResource(R.string.settings_categories_short), onClick = onOpenCategories) }
            item { SwitchRow(stringResource(R.string.settings_original_names), stringResource(R.string.settings_original_names_detail), s.showOriginalNames, viewModel::setShowOriginalNames) }

            // ---- Metadata ----
            header(R.string.settings_section_metadata)
            item { SettingRow(stringResource(R.string.settings_tmdb_key), stringResource(if (hasTmdb) R.string.settings_tmdb_key_set else R.string.settings_tmdb_key_none)) { dialog = SettingDialog.TMDB_KEY } }
            item { SettingRow(stringResource(R.string.settings_clear_metadata), null) { viewModel.clearMetadataCache() } }

            // ---- About ----
            header(R.string.settings_section_about)
            item { SettingRow(stringResource(R.string.settings_about), stringResource(R.string.settings_about_detail), onClick = onOpenAbout) }
        }
    }

    when (dialog) {
        SettingDialog.PLAYLIST_INTERVAL -> ChoiceDialog(
            title = stringResource(R.string.settings_auto_refresh),
            choices = AppSettings.PLAYLIST_REFRESH_CHOICES.map { Choice(it, hours(it)) },
            selected = s.playlistRefreshHours,
            onSelect = { viewModel.setPlaylistRefreshHours(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingDialog.EPG_INTERVAL -> ChoiceDialog(
            title = stringResource(R.string.settings_auto_refresh),
            choices = AppSettings.EPG_REFRESH_CHOICES.map { Choice(it, hours(it)) },
            selected = s.epgRefreshHours,
            onSelect = { viewModel.setEpgRefreshHours(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingDialog.EPG_OFFSET -> ChoiceDialog(
            title = stringResource(R.string.settings_epg_offset),
            choices = AppSettings.EPG_OFFSET_CHOICES.sorted().map { Choice(it, offsetLabel(it)) },
            selected = s.epgOffsetMinutes,
            onSelect = { viewModel.setEpgOffset(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingDialog.EPG_URL -> TextInputDialog(
            title = stringResource(R.string.settings_epg_url),
            message = stringResource(R.string.settings_epg_url_help),
            initial = manualEpgInitial,
            placeholder = "http://example.com/epg.xml.gz",
            keyboardType = KeyboardType.Uri,
            onSave = { viewModel.setManualEpgUrl(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingDialog.DECODER -> ChoiceDialog(
            title = stringResource(R.string.settings_decoder),
            choices = listOf(
                Choice(DecoderMode.HARDWARE, stringResource(R.string.decoder_hardware), stringResource(R.string.decoder_hardware_detail)),
                Choice(DecoderMode.SOFTWARE, stringResource(R.string.decoder_software), stringResource(R.string.decoder_software_detail)),
                Choice(DecoderMode.AUTO, stringResource(R.string.decoder_auto), stringResource(R.string.decoder_auto_detail)),
            ),
            selected = s.decoderMode,
            onSelect = { viewModel.setDecoder(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingDialog.BUFFER -> ChoiceDialog(
            title = stringResource(R.string.settings_buffer),
            choices = BufferMode.entries.map { Choice(it, stringResource(bufferLabel(it))) },
            selected = s.bufferMode,
            onSelect = { viewModel.setBuffer(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingDialog.USER_AGENT -> TextInputDialog(
            title = stringResource(R.string.settings_user_agent),
            message = stringResource(R.string.settings_user_agent_help),
            initial = s.userAgent,
            placeholder = AppSettings.DEFAULT_USER_AGENT,
            onSave = { viewModel.setUserAgent(it); dialog = null },
            onDismiss = { dialog = null },
            extraLabel = stringResource(R.string.action_reset),
            onExtra = { viewModel.setUserAgent(AppSettings.DEFAULT_USER_AGENT); dialog = null },
        )
        SettingDialog.SUBTITLE_SIZE -> ChoiceDialog(
            title = stringResource(R.string.settings_subtitle_size),
            choices = SubtitleSize.entries.map { Choice(it, stringResource(subtitleSizeLabel(it))) },
            selected = s.subtitleSize,
            onSelect = { viewModel.setSubtitleSize(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingDialog.SUBTITLE_COLOR -> ChoiceDialog(
            title = stringResource(R.string.settings_subtitle_color),
            choices = listOf(
                Choice(SubtitleColor.WHITE, stringResource(R.string.color_white)),
                Choice(SubtitleColor.YELLOW, stringResource(R.string.color_yellow)),
            ),
            selected = s.subtitleColor,
            onSelect = { viewModel.setSubtitleColor(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingDialog.TMDB_KEY -> TextInputDialog(
            title = stringResource(R.string.settings_tmdb_key),
            message = stringResource(R.string.settings_tmdb_help),
            initial = "",
            placeholder = stringResource(R.string.settings_tmdb_placeholder),
            secret = true,
            onSave = { viewModel.setTmdbKey(it); dialog = null },
            onDismiss = { dialog = null },
            extraLabel = stringResource(R.string.action_remove),
            onExtra = { viewModel.setTmdbKey(""); dialog = null },
        )
        SettingDialog.LOGOUT -> ConfirmDialog(
            title = stringResource(R.string.settings_logout_confirm_title),
            message = stringResource(R.string.settings_logout_confirm_message),
            confirmLabel = stringResource(R.string.settings_logout),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { dialog = null; viewModel.logout() },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun hours(h: Int): String =
    if (h <= 0) stringResource(R.string.settings_off) else pluralStringResource(R.plurals.every_hours, h, h)

@Composable
private fun ago(t: Long): String =
    if (t <= 0) stringResource(R.string.settings_never)
    else DateUtils.getRelativeTimeSpanString(t, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

private fun LazyListScope.header(res: Int) {
    item(key = "h$res") {
        Text(
            stringResource(res),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun SettingRow(title: String, value: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(title) },
        supportingContent = value?.let { { Text(it, maxLines = 2) } },
        modifier = modifier,
    )
}

@Composable
private fun SwitchRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        selected = false,
        onClick = { onChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = detail?.let { { Text(it, maxLines = 2) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun offsetLabel(minutes: Int): String = when {
    minutes == 0 -> stringResource(R.string.settings_epg_offset_none)
    minutes % 60 == 0 -> stringResource(R.string.settings_epg_offset_hours, if (minutes > 0) "+${minutes / 60}" else "${minutes / 60}")
    else -> stringResource(R.string.settings_epg_offset_minutes, if (minutes > 0) "+$minutes" else "$minutes")
}

private fun bufferLabel(mode: BufferMode): Int = when (mode) {
    BufferMode.SMALL -> R.string.buffer_small
    BufferMode.NORMAL -> R.string.buffer_normal
    BufferMode.LARGE -> R.string.buffer_large
}

private fun subtitleSizeLabel(size: SubtitleSize): Int = when (size) {
    SubtitleSize.SMALL -> R.string.size_small
    SubtitleSize.NORMAL -> R.string.size_normal
    SubtitleSize.LARGE -> R.string.size_large
    SubtitleSize.XLARGE -> R.string.size_xlarge
}
