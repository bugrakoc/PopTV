package pm.koc.poptv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import pm.koc.poptv.ui.LocalIsTv
import pm.koc.poptv.ui.phone.PhoneConfirmDialog

/** A basic TV dialog: title, optional body, and the given content. */
@Composable
fun TvDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier.widthIn(min = 360.dp, max = 640.dp).ignorePressesStartedElsewhere(),
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(28.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

/** Confirmation dialog with a primary and a dismiss button; the primary button gets initial focus. */
@Composable
fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
) {
    if (!LocalIsTv.current) {
        PhoneConfirmDialog(title, confirmLabel, dismissLabel, onConfirm, onDismiss, message)
        return
    }
    val focus = remember { FocusRequester() }
    TvDialog(onDismissRequest = onDismiss, title = title) {
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onConfirm, modifier = Modifier.focusRequester(focus)) { Text(confirmLabel) }
            OutlinedButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    }
    LaunchedEffect(Unit) { focus.safeRequestFocus() }
}
