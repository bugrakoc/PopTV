package pm.koc.poptv.ui.menu

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import pm.koc.poptv.R
import pm.koc.poptv.data.db.PlaybackPositionEntity
import pm.koc.poptv.data.db.ShowEntity
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.repo.CategoryRepository
import pm.koc.poptv.data.repo.LibraryRepository
import pm.koc.poptv.data.repo.PlaybackRepository
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.domain.ItemKind
import pm.koc.poptv.domain.ResumePolicy
import pm.koc.poptv.ui.components.ConfirmDialog
import pm.koc.poptv.ui.components.ContextMenu
import pm.koc.poptv.ui.components.MenuAction
import pm.koc.poptv.ui.components.formatPosition
import javax.inject.Inject

/** State and actions behind the long-press menus of channels, movies, episodes and shows. */
@HiltViewModel
class ItemMenuViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val playback: PlaybackRepository,
    private val categories: CategoryRepository,
) : ViewModel() {
    fun isFavorite(key: String): Flow<Boolean> = library.isFavorite(key)
    fun position(key: String): Flow<PlaybackPositionEntity?> = playback.observePosition(key)

    fun toggleFavorite(key: String, kind: ItemKind) {
        viewModelScope.launch { library.toggleFavorite(key, kind) }
    }

    fun setWatched(stream: StreamEntity, watched: Boolean) {
        viewModelScope.launch { playback.setWatched(stream, watched) }
    }

    fun hideCategory(group: String) {
        viewModelScope.launch { categories.hide(group) }
    }
}

/**
 * Long-press menu for a channel, movie or episode.
 *
 * @param onPlay plays the item; `true` starts from the beginning.
 * @param onHideCategory replaces the default hiding (e.g. to also change the selected category).
 */
@Composable
fun StreamMenu(
    stream: StreamEntity,
    title: String,
    onDismiss: () -> Unit,
    onPlay: (startOver: Boolean) -> Unit,
    onOpenDetails: (() -> Unit)? = null,
    onOpenShow: (() -> Unit)? = null,
    extraActions: List<MenuAction> = emptyList(),
    onHideCategory: ((String) -> Unit)? = null,
    viewModel: ItemMenuViewModel = hiltViewModel(),
) {
    val favorite by remember(stream.key) { viewModel.isFavorite(stream.key) }.collectAsStateWithLifecycle(false)
    val position by remember(stream.key) { viewModel.position(stream.key) }.collectAsStateWithLifecycle(null)
    val actions = ArrayList<MenuAction>()

    if (stream.type == ContentType.LIVE) {
        actions += MenuAction(stringResource(R.string.player_play), Icons.Filled.PlayArrow) { onPlay(false) }
        actions += favoriteAction(favorite) { viewModel.toggleFavorite(stream.key, ItemKind.CHANNEL) }
    } else {
        val p = position
        val watched = p != null && (p.watched || ResumePolicy.isWatched(p.positionMs, p.durationMs))
        val resumeAt = ResumePolicy.startPosition(p?.positionMs, p?.durationMs, p?.watched ?: false)
        if (resumeAt > 0) {
            actions += MenuAction(stringResource(R.string.detail_resume_at, formatPosition(resumeAt)), Icons.Filled.PlayArrow) { onPlay(false) }
            actions += MenuAction(stringResource(R.string.detail_start_over), Icons.Filled.Replay) { onPlay(true) }
        } else {
            actions += MenuAction(stringResource(R.string.player_play), Icons.Filled.PlayArrow) { onPlay(true) }
        }
        if (onOpenDetails != null) actions += MenuAction(stringResource(R.string.menu_details), Icons.Filled.Info, onClick = onOpenDetails)
        if (onOpenShow != null) actions += MenuAction(stringResource(R.string.home_go_to_show), Icons.Filled.Tv, onClick = onOpenShow)
        if (stream.type == ContentType.MOVIE) {
            actions += favoriteAction(favorite) { viewModel.toggleFavorite(stream.key, ItemKind.MOVIE) }
        }
        actions += MenuAction(
            stringResource(if (watched) R.string.detail_mark_unwatched else R.string.detail_mark_watched),
            if (watched) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
        ) { viewModel.setWatched(stream, !watched) }
    }
    actions += extraActions
    MenuWithHideCategory(title, stream.groupTitle, actions, onDismiss, onHideCategory ?: viewModel::hideCategory)
}

/** Long-press menu for a show. */
@Composable
fun ShowMenu(
    show: ShowEntity,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    viewModel: ItemMenuViewModel = hiltViewModel(),
) {
    val favorite by remember(show.seriesKey) { viewModel.isFavorite(show.seriesKey) }.collectAsStateWithLifecycle(false)
    val actions = listOf(
        MenuAction(stringResource(R.string.home_go_to_show), Icons.Filled.Tv, onClick = onOpen),
        favoriteAction(favorite) { viewModel.toggleFavorite(show.seriesKey, ItemKind.SHOW) },
    )
    MenuWithHideCategory(show.name, show.groupTitle, actions, onDismiss, viewModel::hideCategory)
}

@Composable
private fun favoriteAction(favorite: Boolean, onToggle: () -> Unit) = MenuAction(
    stringResource(if (favorite) R.string.detail_remove_favorite else R.string.detail_add_favorite),
    if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
    onClick = onToggle,
)

/** Shows [actions] plus "Hide category" (when the item has one), which asks for confirmation first. */
@Composable
private fun MenuWithHideCategory(
    title: String,
    group: String,
    actions: List<MenuAction>,
    onDismiss: () -> Unit,
    hide: (String) -> Unit,
) {
    var confirmHide by remember { mutableStateOf(false) }
    if (confirmHide) {
        ConfirmDialog(
            title = stringResource(R.string.live_hide_category_title, group),
            message = stringResource(R.string.live_hide_category_message),
            confirmLabel = stringResource(R.string.action_hide),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { hide(group); onDismiss() },
            onDismiss = onDismiss,
        )
        return
    }
    val all = if (group.isBlank()) {
        actions
    } else {
        actions + MenuAction(stringResource(R.string.menu_hide_category), Icons.Filled.VisibilityOff, keepOpen = true) { confirmHide = true }
    }
    ContextMenu(title, all, onDismiss)
}
