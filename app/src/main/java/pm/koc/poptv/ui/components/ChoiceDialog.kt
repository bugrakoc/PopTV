package pm.koc.poptv.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import pm.koc.poptv.ui.LocalIsTv
import pm.koc.poptv.ui.phone.PhoneChoiceDialog

data class Choice<T>(val value: T, val label: String, val detail: String? = null)

/** Single-choice list dialog; the current choice is focused initially. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!LocalIsTv.current) {
        PhoneChoiceDialog(title, choices, selected, onSelect, onDismiss)
        return
    }
    val focus = remember { FocusRequester() }
    val selectedIndex = choices.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    TvDialog(onDismissRequest = onDismiss, title = title) {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            itemsIndexed(choices) { index, choice ->
                ListItem(
                    selected = choice.value == selected,
                    onClick = { onSelect(choice.value) },
                    headlineContent = { Text(choice.label) },
                    supportingContent = choice.detail?.let { { Text(it) } },
                    trailingContent = if (choice.value == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    modifier = if (index == selectedIndex) Modifier.focusRequester(focus) else Modifier,
                )
            }
        }
    }
    LaunchedEffect(Unit) { focus.safeRequestFocus() }
}
