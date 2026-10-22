package pm.koc.poptv.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import pm.koc.poptv.ui.LocalIsTv
import pm.koc.poptv.ui.phone.PhoneActionSheet

/** One entry of a [ContextMenu]. Choosing it closes the menu unless [keepOpen] (it opens a follow-up dialog). */
data class MenuAction(
    val label: String,
    val icon: ImageVector,
    val keepOpen: Boolean = false,
    val onClick: () -> Unit,
)

/** Long-press menu: a dialog listing [actions], the first one focused (a bottom sheet on phones). */
@Composable
fun ContextMenu(title: String, actions: List<MenuAction>, onDismiss: () -> Unit) {
    if (!LocalIsTv.current) {
        PhoneActionSheet(title, actions, onDismiss)
        return
    }
    TvDialog(onDismissRequest = onDismiss, title = title) {
        val first = remember { FocusRequester() }
        actions.forEachIndexed { i, action ->
            ListItem(
                selected = false,
                onClick = {
                    if (!action.keepOpen) onDismiss()
                    action.onClick()
                },
                leadingContent = { Icon(action.icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
                headlineContent = { Text(action.label) },
                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
        LaunchedEffect(Unit) { first.safeRequestFocus() }
    }
}
