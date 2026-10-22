package pm.koc.poptv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import pm.koc.poptv.R
import pm.koc.poptv.domain.CategoryOverride
import pm.koc.poptv.ui.LocalIsTv
import pm.koc.poptv.ui.components.AdaptiveFilterChip
import pm.koc.poptv.ui.components.AdaptiveListItem
import pm.koc.poptv.ui.components.Choice
import pm.koc.poptv.ui.components.ChoiceDialog
import pm.koc.poptv.ui.components.EmptyState
import pm.koc.poptv.ui.components.ProgressBar
import pm.koc.poptv.ui.components.ScreenScaffold
import pm.koc.poptv.ui.components.label

@Composable
fun CategorySettingsScreen(viewModel: CategorySettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    ScreenScaffold(
        title = stringResource(R.string.settings_categories),
        subtitle = stringResource(R.string.settings_categories_help),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = if (LocalIsTv.current) 0.dp else 16.dp)) {
            items(CategorySettingsViewModel.Filter.entries) { f ->
                AdaptiveFilterChip(selected = state.filter == f, onClick = { viewModel.setFilter(f) }, label = stringResource(f.labelRes()))
            }
        }
        Spacer(Modifier.height(8.dp))
        if (state.busy || state.loading) ProgressBar(null) else Spacer(Modifier.height(4.dp))
        Spacer(Modifier.height(8.dp))

        val visible = state.visible
        if (!state.loading && visible.isEmpty()) {
            EmptyState(stringResource(R.string.settings_categories_empty))
            return@ScreenScaffold
        }
        LazyColumn(state = rememberLazyListState(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(visible, key = { it.groupTitle }) { group ->
                val detected = group.detectedType.label()
                AdaptiveListItem(
                    headline = group.groupTitle.ifBlank { stringResource(R.string.category_uncategorized) },
                    supporting = pluralStringResource(R.plurals.items_count, group.count, group.count),
                    onClick = { editing = group.groupTitle },
                    trailing = {
                        Row {
                            Text(
                                if (group.override == null) stringResource(R.string.type_auto, detected) else group.override.label(),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (group.override == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
    }

    val group = state.groups.firstOrNull { it.groupTitle == editing }
    if (group != null) {
        val choices = buildList<Choice<CategoryOverride?>> {
            add(Choice<CategoryOverride?>(null, stringResource(R.string.type_auto, group.detectedType.label())))
            CategoryOverride.entries.forEach { add(Choice(it, it.label())) }
        }
        ChoiceDialog(
            title = group.groupTitle.ifBlank { stringResource(R.string.category_uncategorized) },
            choices = choices,
            selected = group.override,
            onSelect = {
                viewModel.setOverride(group.groupTitle, it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private fun CategorySettingsViewModel.Filter.labelRes(): Int = when (this) {
    CategorySettingsViewModel.Filter.ALL -> R.string.filter_all
    CategorySettingsViewModel.Filter.LIVE -> R.string.type_live
    CategorySettingsViewModel.Filter.MOVIE -> R.string.type_movie
    CategorySettingsViewModel.Filter.SERIES -> R.string.type_series
    CategorySettingsViewModel.Filter.HIDDEN -> R.string.type_hidden
    CategorySettingsViewModel.Filter.CHANGED -> R.string.filter_changed
}
