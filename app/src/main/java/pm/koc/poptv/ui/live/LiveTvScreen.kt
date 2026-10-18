package pm.koc.poptv.ui.live

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import pm.koc.poptv.R
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.ui.components.ScreenScaffold

@Composable
fun LiveTvScreen(
    onPlay: (StreamEntity, String) -> Unit,
    onSearch: () -> Unit,
    viewModel: ChannelBrowserViewModel = hiltViewModel(),
) {
    ScreenScaffold(
        title = stringResource(R.string.nav_live),
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.nav_search))
            }
        },
    ) {
        ChannelBrowser(viewModel = viewModel, onChannelClick = onPlay)
    }
}
