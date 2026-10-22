package pm.koc.poptv.ui.phone

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import pm.koc.poptv.R
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.ui.live.ChannelBrowserViewModel

@Composable
fun PhoneLiveScreen(
    onPlay: (StreamEntity, String) -> Unit,
    onSearch: () -> Unit,
    viewModel: ChannelBrowserViewModel = hiltViewModel(),
) {
    PhoneScreen(
        title = stringResource(R.string.nav_live),
        showBack = false,
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.nav_search)) }
        },
    ) {
        PhoneChannelList(viewModel = viewModel, onChannelClick = onPlay)
    }
}
