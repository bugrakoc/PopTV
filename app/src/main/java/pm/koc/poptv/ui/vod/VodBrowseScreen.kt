package pm.koc.poptv.ui.vod

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import kotlinx.coroutines.delay
import pm.koc.poptv.R
import pm.koc.poptv.data.db.ShowEntity
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.repo.SortOrder
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.ui.components.Choice
import pm.koc.poptv.ui.components.ChoiceDialog
import pm.koc.poptv.ui.components.EmptyState
import pm.koc.poptv.ui.components.PosterCard
import pm.koc.poptv.ui.components.ScreenScaffold
import pm.koc.poptv.ui.components.safeRequestFocus
import pm.koc.poptv.ui.live.CategoryItem
import pm.koc.poptv.ui.menu.ShowMenu
import pm.koc.poptv.ui.menu.StreamMenu

/** Movies (type MOVIE) or TV shows (type SERIES) poster grid with a category sidebar. */
@Composable
fun VodBrowseScreen(
    type: ContentType,
    onOpenMovie: (String) -> Unit,
    onOpenShow: (String) -> Unit,
    onPlay: (streamKey: String, startOver: Boolean) -> Unit,
    onSearch: () -> Unit,
    viewModel: VodBrowseViewModel = hiltViewModel(),
) {
    LaunchedEffect(type) { viewModel.setType(type) }
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selected by viewModel.category.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val original by viewModel.showOriginalNames.collectAsStateWithLifecycle()
    var sortDialog by rememberSaveable { mutableStateOf(false) }
    var lastFocused by rememberSaveable { mutableStateOf<String?>(null) }
    var movieMenu by remember { mutableStateOf<StreamEntity?>(null) }
    var showMenu by remember { mutableStateOf<ShowEntity?>(null) }
    val gridFocus = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    ScreenScaffold(
        title = stringResource(if (type == ContentType.MOVIE) R.string.nav_movies else R.string.nav_shows),
        actions = {
            IconButton(onClick = { sortDialog = true }) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort))
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.nav_search))
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.width(250.dp).fillMaxHeight().focusRestorer(categoryFocus),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "all") {
                    CategoryItem(
                        title = stringResource(R.string.filter_all),
                        count = null,
                        selected = selected == null,
                        onClick = { viewModel.selectCategory(null); gridFocus.safeRequestFocus() },
                        modifier = Modifier
                            .then(if (selected == null) Modifier.focusRequester(categoryFocus) else Modifier)
                            .onFocusChanged { if (it.isFocused) viewModel.selectCategory(null) },
                    )
                }
                items(categories, key = { it.id }) { cat ->
                    CategoryItem(
                        title = cat.displayName.ifBlank { stringResource(R.string.category_uncategorized) },
                        count = cat.itemCount,
                        selected = selected == cat.name,
                        onClick = { viewModel.selectCategory(cat.name); gridFocus.safeRequestFocus() },
                        modifier = Modifier
                            .then(if (selected == cat.name) Modifier.focusRequester(categoryFocus) else Modifier)
                            .onFocusChanged { if (it.isFocused) viewModel.selectCategory(cat.name) },
                    )
                }
            }

            Box(Modifier.weight(1f)) {
                if (type == ContentType.MOVIE) {
                    val items = viewModel.movies.collectAsLazyPagingItems()
                    if (items.itemCount == 0) {
                        EmptyState(stringResource(R.string.vod_empty))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(150.dp),
                            state = gridState,
                            modifier = Modifier.fillMaxWidth().focusRestorer(gridFocus),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            contentPadding = PaddingValues(8.dp),
                        ) {
                            items(items.itemCount, key = items.itemKey { it.key }) { i ->
                                val m = items[i]
                                if (m == null) {
                                    Spacer(Modifier.aspectRatio(2f / 3f))
                                } else {
                                    PosterCard(
                                        title = if (original) m.name else m.displayName,
                                        imageUrl = m.logo,
                                        subtitle = m.year?.toString(),
                                        onClick = { onOpenMovie(m.key) },
                                        onLongClick = { movieMenu = m },
                                        width = 150.dp,
                                        modifier = Modifier
                                            .then(if (m.key == lastFocused || (lastFocused == null && i == 0)) Modifier.focusRequester(gridFocus) else Modifier)
                                            .onFocusChanged { if (it.isFocused) lastFocused = m.key },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val items = viewModel.shows.collectAsLazyPagingItems()
                    if (items.itemCount == 0) {
                        EmptyState(stringResource(R.string.vod_empty))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(150.dp),
                            state = gridState,
                            modifier = Modifier.fillMaxWidth().focusRestorer(gridFocus),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            contentPadding = PaddingValues(8.dp),
                        ) {
                            items(items.itemCount, key = items.itemKey { it.seriesKey }) { i ->
                                val show = items[i]
                                if (show == null) {
                                    Spacer(Modifier.aspectRatio(2f / 3f))
                                } else {
                                    PosterCard(
                                        title = show.name,
                                        imageUrl = show.logo,
                                        // 0 for Xtream shows whose episodes have not been loaded yet
                                        subtitle = if (show.episodeCount == 0) null else pluralStringResource(R.plurals.episodes_count, show.episodeCount, show.episodeCount),
                                        onClick = { onOpenShow(show.seriesKey) },
                                        onLongClick = { showMenu = show },
                                        width = 150.dp,
                                        modifier = Modifier
                                            .then(if (show.seriesKey == lastFocused || (lastFocused == null && i == 0)) Modifier.focusRequester(gridFocus) else Modifier)
                                            .onFocusChanged { if (it.isFocused) lastFocused = show.seriesKey },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        repeat(10) {
            delay(80)
            val ok = if (lastFocused != null) gridFocus.safeRequestFocus() || categoryFocus.safeRequestFocus() else categoryFocus.safeRequestFocus()
            if (ok) return@LaunchedEffect
        }
    }

    movieMenu?.let { m ->
        StreamMenu(
            stream = m,
            title = if (original) m.name else m.displayName,
            onDismiss = { movieMenu = null },
            onPlay = { startOver -> onPlay(m.key, startOver) },
            onOpenDetails = { onOpenMovie(m.key) },
        )
    }
    showMenu?.let { sh -> ShowMenu(sh, onDismiss = { showMenu = null }, onOpen = { onOpenShow(sh.seriesKey) }) }

    if (sortDialog) {
        ChoiceDialog(
            title = stringResource(R.string.sort),
            choices = listOf(
                Choice(SortOrder.PLAYLIST, stringResource(R.string.sort_playlist)),
                Choice(SortOrder.NAME, stringResource(R.string.sort_name)),
                Choice(SortOrder.NEWEST, stringResource(R.string.sort_newest)),
                Choice(SortOrder.YEAR, stringResource(R.string.sort_year)),
            ),
            selected = sort,
            onSelect = { viewModel.setSort(it); sortDialog = false },
            onDismiss = { sortDialog = false },
        )
    }
}
