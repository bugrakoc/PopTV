package pm.koc.poptv.ui.phone

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import pm.koc.poptv.R
import pm.koc.poptv.ui.components.ProgressBar
import pm.koc.poptv.ui.components.RemoteImage
import pm.koc.poptv.ui.theme.SuccessGreen

val PhoneHorizontalPadding = 16.dp
private val CardShape = RoundedCornerShape(10.dp)

/** Top app bar + content. [showBack] adds an up arrow that acts like the system back button. */
@Composable
fun PhoneScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showBack: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                },
                navigationIcon = { if (showBack) BackButton() },
                actions = actions,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), content = content)
    }
}

@Composable
fun BackButton() {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    IconButton(onClick = { dispatcher?.onBackPressed() }) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}

/** Centered message for empty / loading states. */
@Composable
fun PhoneEmptyState(message: String, modifier: Modifier = Modifier, detail: String? = null) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (detail != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(horizontal = PhoneHorizontalPadding, vertical = 8.dp),
    )
}

/** 2:3 poster with title (and optional subtitle / progress / watched marker) below. */
@Composable
fun PhonePosterCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 112.dp,
    subtitle: String? = null,
    progress: Float? = null,
    watched: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        (if (width != null) modifier.width(width) else modifier)
            .clip(CardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            RemoteImage(
                url = imageUrl,
                fallbackText = title,
                shape = CardShape,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                textStyle = MaterialTheme.typography.titleLarge,
            )
            if (watched) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.detail_watched),
                    tint = SuccessGreen,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp),
                )
            }
            if (progress != null && progress > 0f) {
                ProgressBar(progress, height = 3.dp, modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** 16:9 channel card (logo centered) for horizontal rows. */
@Composable
fun PhoneChannelCard(
    title: String,
    logo: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier.width(144.dp).clip(CardShape).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        RemoteImage(
            url = logo,
            fallbackText = title,
            shape = CardShape,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            textStyle = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One entry of a [CategorySheet]. */
data class CategoryOption(val key: String?, val label: String, val count: Int? = null, val hideable: Boolean = false)

/** Button showing the selected category; opens the picker. */
@Composable
fun CategoryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(start = 16.dp, end = 8.dp)) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
    }
}

/** Category picker as a bottom sheet; long-press offers to hide a playlist category. */
@Composable
fun CategorySheet(
    options: List<CategoryOption>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    onLongPress: ((String) -> Unit)? = null,
) {
    val state = rememberModalBottomSheetState()
    val selectedIndex = options.indexOfFirst { it.key == selected }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 3).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        LazyColumn(state = listState, modifier = Modifier.navigationBarsPadding()) {
            items(options, key = { it.key ?: "\u0000all" }) { option ->
                val isSelected = option.key == selected
                ListItem(
                    headlineContent = {
                        Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = option.count?.let { { Text(it.toString(), style = MaterialTheme.typography.labelMedium) } },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    ),
                    modifier = Modifier.combinedClickable(
                        onClick = { onSelect(option.key); onDismiss() },
                        onLongClick = if (option.hideable && onLongPress != null && option.key != null) {
                            { onLongPress(option.key) }
                        } else {
                            null
                        },
                    ),
                )
            }
        }
    }
}

/**
 * Starts the list at the top when [category] changes. Paged lists swap their content only after a debounce,
 * and a lazy list keeps showing an item that is in both the old and new content, so scroll again once the new
 * items have arrived. The first composition is left alone (it may restore a position or show the playing
 * channel).
 */
@Composable
fun <T : Any> ScrollToTopOnCategoryChange(category: Any?, items: LazyPagingItems<T>, scrollToTop: suspend () -> Unit) {
    var shown by remember { mutableStateOf(category) }
    LaunchedEffect(category) {
        if (category == shown) return@LaunchedEffect
        shown = category
        scrollToTop()
        withTimeoutOrNull(3_000) {
            snapshotFlow { items.itemCount to items.itemSnapshotList.firstOrNull() }.drop(1).first()
        }
        scrollToTop()
    }
}

/** Bottom system bar height, for screens shown without the bottom navigation bar. */
@Composable
fun navigationBarHeight(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** Spacing and padding of horizontal card rows. */
val CardRowArrangement = Arrangement.spacedBy(12.dp)
val CardRowPadding = PaddingValues(horizontal = PhoneHorizontalPadding)
