package pm.koc.poptv.ui.phone

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pm.koc.poptv.R
import pm.koc.poptv.core.SearchQuery
import pm.koc.poptv.data.db.ShowEntity
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.repo.LiveContext
import pm.koc.poptv.domain.ContentType
import pm.koc.poptv.ui.components.safeRequestFocus
import pm.koc.poptv.ui.menu.ShowMenu
import pm.koc.poptv.ui.menu.StreamMenu
import pm.koc.poptv.ui.search.SearchViewModel

@Composable
fun PhoneSearchScreen(
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
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldFocus = remember { FocusRequester() }

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

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = PhoneHorizontalPadding, top = 8.dp, bottom = 8.dp)) {
            BackButton()
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    when {
                        query.isNotEmpty() -> IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                        }
                        voiceAvailable -> IconButton(onClick = {
                            try {
                                voice.launch(voiceIntent)
                            } catch (_: ActivityNotFoundException) {
                            }
                        }) {
                            Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.search_voice))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.weight(1f).focusRequester(fieldFocus),
            )
        }
        val r = results
        when {
            r == null -> PhoneEmptyState(
                if (query.isBlank()) stringResource(R.string.search_prompt) else pluralStringResource(R.plurals.search_too_short, SearchQuery.MIN_LENGTH, SearchQuery.MIN_LENGTH),
            )
            r.isEmpty -> PhoneEmptyState(stringResource(R.string.search_no_results, query.trim()))
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (r.channels.isNotEmpty()) item(key = "channels") {
                    ResultRow(stringResource(R.string.search_channels, r.channels.size)) {
                        items(r.channels, key = { it.key }) { s ->
                            PhoneChannelCard(
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
                            PhonePosterCard(
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
                            PhonePosterCard(title = sh.name, imageUrl = sh.logo, onClick = { onOpenShow(sh.seriesKey) }, onLongClick = { showMenu = sh })
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (query.isEmpty() && fieldFocus.safeRequestFocus()) keyboard?.show()
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
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(title)
        LazyRow(horizontalArrangement = CardRowArrangement, contentPadding = CardRowPadding, content = content)
    }
}
