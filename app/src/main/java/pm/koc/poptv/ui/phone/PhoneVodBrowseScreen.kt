package pm.koc.poptv.ui.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import pm.koc.poptv.R
import pm.koc.poptv.data.db.ShowEntity
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.repo.SortOrder
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.ui.components.Choice
import pm.koc.poptv.ui.components.ChoiceDialog
import pm.koc.poptv.ui.menu.ShowMenu
import pm.koc.poptv.ui.menu.StreamMenu
import pm.koc.poptv.ui.vod.VodBrowseViewModel

private val GridPadding = PaddingValues(horizontal = PhoneHorizontalPadding, vertical = 8.dp)

/** Movies (type MOVIE) or TV shows (type SERIES) poster grid with a category picker. */
@Composable
fun PhoneVodBrowseScreen(
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
    var pickerOpen by remember { mutableStateOf(false) }
    var movieMenu by remember { mutableStateOf<StreamEntity?>(null) }
    var showMenu by remember { mutableStateOf<ShowEntity?>(null) }
    val gridState = rememberLazyGridState()
    val uncategorized = stringResource(R.string.category_uncategorized)

    PhoneScreen(
        title = stringResource(if (type == ContentType.MOVIE) R.string.nav_movies else R.string.nav_shows),
        showBack = false,
        actions = {
            IconButton(onClick = { sortDialog = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort)) }
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.nav_search)) }
        },
    ) {
        val label = if (selected == null) {
            stringResource(R.string.filter_all)
        } else {
            categories.firstOrNull { it.name == selected }?.displayName?.ifBlank { uncategorized } ?: selected.orEmpty()
        }
        CategoryButton(label, onClick = { pickerOpen = true }, modifier = Modifier.padding(horizontal = PhoneHorizontalPadding, vertical = 4.dp))

        if (type == ContentType.MOVIE) {
            val items = viewModel.movies.collectAsLazyPagingItems()
            ScrollToTopOnCategoryChange(selected, items) { gridState.scrollToItem(0) }
            if (items.itemCount == 0) {
                PhoneEmptyState(stringResource(R.string.vod_empty))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = GridPadding,
                ) {
                    items(items.itemCount, key = items.itemKey { it.key }) { i ->
                        val m = items[i]
                        if (m == null) {
                            Spacer(Modifier.aspectRatio(2f / 3f))
                        } else {
                            PhonePosterCard(
                                title = if (original) m.name else m.displayName,
                                imageUrl = m.logo,
                                subtitle = m.year?.toString(),
                                onClick = { onOpenMovie(m.key) },
                                onLongClick = { movieMenu = m },
                                width = null,
                            )
                        }
                    }
                }
            }
        } else {
            val items = viewModel.shows.collectAsLazyPagingItems()
            ScrollToTopOnCategoryChange(selected, items) { gridState.scrollToItem(0) }
            if (items.itemCount == 0) {
                PhoneEmptyState(stringResource(R.string.vod_empty))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = GridPadding,
                ) {
                    items(items.itemCount, key = items.itemKey { it.seriesKey }) { i ->
                        val show = items[i]
                        if (show == null) {
                            Spacer(Modifier.aspectRatio(2f / 3f))
                        } else {
                            PhonePosterCard(
                                title = show.name,
                                imageUrl = show.logo,
                                // 0 for Xtream shows whose episodes have not been loaded yet
                                subtitle = if (show.episodeCount == 0) null else pluralStringResource(R.plurals.episodes_count, show.episodeCount, show.episodeCount),
                                onClick = { onOpenShow(show.seriesKey) },
                                onLongClick = { showMenu = show },
                                width = null,
                            )
                        }
                    }
                }
            }
        }
    }

    if (pickerOpen) {
        val options = buildList {
            add(CategoryOption(null, stringResource(R.string.filter_all)))
            categories.forEach { add(CategoryOption(it.name, it.displayName.ifBlank { uncategorized }, it.itemCount)) }
        }
        CategorySheet(
            options = options,
            selected = selected,
            onSelect = { viewModel.selectCategory(it) },
            onDismiss = { pickerOpen = false },
        )
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
