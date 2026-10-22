package pm.koc.poptv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import pm.koc.poptv.R
import pm.koc.poptv.ui.LocalIsTv
import pm.koc.poptv.ui.phone.PhoneTextInputDialog

/** Dialog with a single text field plus Save / Cancel (and an optional extra action like "Reset"). */
@Composable
fun TextInputDialog(
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
    if (!LocalIsTv.current) {
        PhoneTextInputDialog(title, initial, placeholder, onSave, onDismiss, message, keyboardType, secret, extraLabel, onExtra)
        return
    }
    var value by remember { mutableStateOf(initial) }
    val saveFocus = remember { FocusRequester() }
    TvDialog(onDismissRequest = onDismiss, title = title) {
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }
        TvTextField(
            value = value,
            onValueChange = { value = it },
            placeholder = placeholder,
            keyboardType = keyboardType,
            secret = secret,
            startEditing = true,
            onImeAction = { saveFocus.safeRequestFocus() },
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onSave(value.trim()) }, modifier = Modifier.focusRequester(saveFocus)) {
                Text(stringResource(R.string.action_save))
            }
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            if (extraLabel != null && onExtra != null) {
                OutlinedButton(onClick = onExtra) { Text(extraLabel) }
            }
        }
    }
}
