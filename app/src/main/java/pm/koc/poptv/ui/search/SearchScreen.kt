package pm.koc.poptv.ui.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import pm.koc.poptv.R
import pm.koc.poptv.core.SearchQuery
import pm.koc.poptv.data.db.ShowEntity
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.repo.LiveContext
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.ui.components.ChannelCard
import pm.koc.poptv.ui.components.EmptyState
import pm.koc.poptv.ui.components.PosterCard
import pm.koc.poptv.ui.components.ScreenScaffold
import pm.koc.poptv.ui.components.TvTextField
import pm.koc.poptv.ui.menu.ShowMenu
import pm.koc.poptv.ui.menu.StreamMenu

@Composable
fun SearchScreen(
    onPlayChannel: (StreamEntity, String) -> Unit,
    onOpenMovie: (String) -> Unit,
    onOpenShow: (String) -> Unit,
    onPlay: (streamKey: String, startOver: Boolean) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    var streamMenu by remember { mutableStateOf<StreamEntity?>(null) }
    var showMenu by remember { mutableStateOf<ShowEntity?>(null) }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val original by viewModel.showOriginalNames.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(viewModel::onQueryChange)
        }
    }
    val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
    }
    val voiceAvailable = remember(context) { voiceIntent.resolveActivity(context.packageManager) != null }

    fun title(s: StreamEntity) = if (original) s.name else s.displayName

    ScreenScaffold(title = stringResource(R.string.nav_search)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = stringResource(R.string.search_hint),
                leadingIcon = Icons.Filled.Search,
                startEditing = query.isEmpty(),
                modifier = Modifier.weight(1f),
            )
            if (voiceAvailable) {
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = {
                    try {
                        voice.launch(voiceIntent)
                    } catch (_: ActivityNotFoundException) {
                    }
                }) {
                    Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.search_voice))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        val r = results
        when {
            r == null -> EmptyState(
                if (query.isBlank()) stringResource(R.string.search_prompt) else pluralStringResource(R.plurals.search_too_short, SearchQuery.MIN_LENGTH, SearchQuery.MIN_LENGTH),
            )
            r.isEmpty -> EmptyState(stringResource(R.string.search_no_results, query.trim()))
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (r.channels.isNotEmpty()) item(key = "channels") {
                    ResultRow(stringResource(R.string.search_channels, r.channels.size)) {
                        items(r.channels, key = { it.key }) { s ->
                            ChannelCard(
                                title = title(s),
                                logo = s.logo,
                                subtitle = s.groupTitle,
                                onClick = { onPlayChannel(s, LiveContext.ALL) },
                                onLongClick = { streamMenu = s },
                            )
                        }
                    }
                }
                if (r.movies.isNotEmpty()) item(key = "movies") {
                    ResultRow(stringResource(R.string.search_movies, r.movies.size)) {
                        items(r.movies, key = { it.key }) { s ->
                            PosterCard(
                                title = title(s),
                                imageUrl = s.logo,
                                subtitle = s.year?.toString(),
                                onClick = { onOpenMovie(s.key) },
                                onLongClick = { streamMenu = s },
                            )
                        }
                    }
                }
                if (r.shows.isNotEmpty()) item(key = "shows") {
                    ResultRow(stringResource(R.string.search_shows, r.shows.size)) {
                        items(r.shows, key = { it.seriesKey }) { sh ->
                            PosterCard(title = sh.name, imageUrl = sh.logo, onClick = { onOpenShow(sh.seriesKey) }, onLongClick = { showMenu = sh })
                        }
                    }
                }
            }
        }
    }

    streamMenu?.let { s ->
        StreamMenu(
            stream = s,
            title = title(s),
            onDismiss = { streamMenu = null },
            onPlay = { startOver -> if (s.type == ContentType.LIVE) onPlayChannel(s, LiveContext.ALL) else onPlay(s.key, startOver) },
            onOpenDetails = if (s.type == ContentType.MOVIE) ({ onOpenMovie(s.key) }) else null,
        )
    }
    showMenu?.let { sh -> ShowMenu(sh, onDismiss = { showMenu = null }, onOpen = { onOpenShow(sh.seriesKey) }) }
}

@Composable
private fun ResultRow(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
            modifier = Modifier.focusRestorer(),
            content = content,
        )
    }
}
