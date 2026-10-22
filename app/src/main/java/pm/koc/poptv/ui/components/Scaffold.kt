package pm.koc.poptv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import pm.koc.poptv.ui.LocalIsTv
import pm.koc.poptv.ui.phone.PhoneScreen

/** TV overscan-safe padding (5% of 960x540dp roughly). */
val ScreenHorizontalPadding = 48.dp
val ScreenVerticalPadding = 27.dp

@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!LocalIsTv.current) {
        PhoneScreen(title = title, modifier = modifier, subtitle = subtitle, actions = { actions() }, content = content)
        return
    }
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = ScreenHorizontalPadding, vertical = ScreenVerticalPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            actions()
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

/** Centered message for empty / loading states. */
@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier, detail: String? = null, action: @Composable () -> Unit = {}) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, style = MaterialTheme.typography.titleLarge)
            if (detail != null) {
                Spacer(Modifier.height(8.dp))
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}
