package pm.koc.poptv.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import pm.koc.poptv.ui.LocalIsTv
import androidx.compose.material3.FilterChip as PhoneFilterChip
import androidx.compose.material3.ListItem as PhoneListItem
import androidx.compose.material3.ListItemDefaults as PhoneListItemDefaults
import androidx.compose.material3.Switch as PhoneSwitch
import androidx.compose.material3.Text as PhoneText
import androidx.tv.material3.FilterChip as TvFilterChip
import androidx.tv.material3.ListItem as TvListItem
import androidx.tv.material3.Switch as TvSwitch
import androidx.tv.material3.Text as TvText

/** List row for settings-style screens: a focusable TV list item, or a Material list item on phones. */
@Composable
fun AdaptiveListItem(
    headline: String,
    supporting: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    if (LocalIsTv.current) {
        TvListItem(
            selected = false,
            onClick = onClick ?: {},
            headlineContent = { TvText(headline) },
            supportingContent = supporting?.let { { TvText(it, maxLines = 2) } },
            trailingContent = trailing,
            modifier = modifier,
        )
    } else {
        PhoneListItem(
            headlineContent = { PhoneText(headline) },
            supportingContent = supporting?.let { { PhoneText(it, maxLines = 3) } },
            trailingContent = trailing,
            colors = PhoneListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        )
    }
}

@Composable
fun AdaptiveSwitchRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val tv = LocalIsTv.current
    AdaptiveListItem(
        headline = title,
        supporting = detail,
        onClick = { onChange(!checked) },
        trailing = {
            if (tv) TvSwitch(checked = checked, onCheckedChange = null) else PhoneSwitch(checked = checked, onCheckedChange = onChange)
        },
    )
}

@Composable
fun AdaptiveFilterChip(selected: Boolean, onClick: () -> Unit, label: String) {
    if (LocalIsTv.current) {
        TvFilterChip(selected = selected, onClick = onClick) { TvText(label) }
    } else {
        PhoneFilterChip(selected = selected, onClick = onClick, label = { PhoneText(label) })
    }
}
