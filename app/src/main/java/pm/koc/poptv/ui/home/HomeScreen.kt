package pm.koc.poptv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import pm.koc.poptv.R
import pm.koc.poptv.ui.components.ConfirmDialog

@Composable
fun HomeScreen(
    onOpenLive: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenShows: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    val activity = LocalActivity.current
    BackHandler { confirmExit = true }

    Column(Modifier.fillMaxSize().padding(48.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onOpenLive) { Text(stringResource(R.string.nav_live)) }
            Button(onClick = onOpenMovies) { Text(stringResource(R.string.nav_movies)) }
            Button(onClick = onOpenShows) { Text(stringResource(R.string.nav_shows)) }
            Button(onClick = onOpenGuide) { Text(stringResource(R.string.nav_guide)) }
            Button(onClick = onOpenSearch) { Text(stringResource(R.string.nav_search)) }
            Button(onClick = onOpenSettings) { Text(stringResource(R.string.nav_settings)) }
        }
    }

    if (confirmExit) {
        ConfirmDialog(
            title = stringResource(R.string.exit_title),
            confirmLabel = stringResource(R.string.exit_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { activity?.finish() },
            onDismiss = { confirmExit = false },
        )
    }
}
