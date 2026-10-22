package pm.koc.poptv.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import pm.koc.poptv.BuildConfig
import pm.koc.poptv.R
import pm.koc.poptv.ui.LocalIsTv
import pm.koc.poptv.ui.components.AdaptiveListItem
import pm.koc.poptv.ui.components.ScreenScaffold

@Composable
fun AboutScreen() {
    val licenses = stringArrayResource(R.array.open_source_licenses)
    ScreenScaffold(
        title = stringResource(R.string.settings_about),
        subtitle = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
    ) {
        // The TV scaffold already pads its content; the phone one leaves room for edge-to-edge lists.
        val textPadding = Modifier.padding(horizontal = if (LocalIsTv.current) 0.dp else 16.dp)
        Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodyMedium, modifier = textPadding)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.about_tmdb_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = textPadding)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.about_licenses), style = MaterialTheme.typography.titleMedium, modifier = textPadding)
        LazyColumn {
            items(licenses.toList()) { entry ->
                val name = entry.substringBefore('|')
                val license = entry.substringAfter('|', "")
                AdaptiveListItem(headline = name, supporting = license, onClick = null)
            }
        }
    }
}
