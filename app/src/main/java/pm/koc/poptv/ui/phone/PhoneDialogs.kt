package pm.koc.poptv.ui.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pm.koc.poptv.R
import pm.koc.poptv.ui.components.Choice
import pm.koc.poptv.ui.components.MenuAction
import pm.koc.poptv.ui.components.safeRequestFocus

/** Long-press menu as a bottom sheet. */
@Composable
fun PhoneActionSheet(title: String, actions: List<MenuAction>, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            actions.forEach { action ->
                ListItem(
                    headlineContent = { Text(action.label) },
                    leadingContent = { Icon(action.icon, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        action.onClick()
                        if (!action.keepOpen) {
                            scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
                        }
                    },
                )
            }
        }
    }
}

/** Single-choice list; choosing an entry selects it right away. */
@Composable
fun <T> PhoneChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedIndex = choices.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), state = listState) {
                items(choices) { choice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(choice.value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice.value == selected, onClick = { onSelect(choice.value) })
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                            if (choice.detail != null) {
                                Text(choice.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun PhoneConfirmDialog(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = message?.let { { Text(it) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

@Composable
fun PhoneTextInputDialog(
    title: String,
    initial: String,
    placeholder: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    extraLabel: String? = null,
    onExtra: (() -> Unit)? = null,
) {
    var value by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (message != null) {
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { onSave(value.trim()) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value.trim()) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = {
            Row {
                if (extraLabel != null && onExtra != null) {
                    TextButton(onClick = onExtra) { Text(extraLabel) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
    LaunchedEffect(Unit) { focus.safeRequestFocus() }
}
