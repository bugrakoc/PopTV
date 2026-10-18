package pm.koc.poptv.ui.login

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import pm.koc.poptv.R
import pm.koc.poptv.data.repo.ImportProgress
import pm.koc.poptv.data.repo.ImportState
import pm.koc.poptv.ui.components.ProgressBar
import pm.koc.poptv.ui.components.TvTextField
import pm.koc.poptv.ui.components.localizedMessage
import pm.koc.poptv.ui.components.safeRequestFocus
import pm.koc.poptv.ui.theme.Background
import pm.koc.poptv.ui.theme.SurfaceRaised

@Composable
fun LoginScreen(
    prefillUrl: String?,
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val url by viewModel.url.collectAsStateWithLifecycle()
    val invalid by viewModel.invalidUrl.collectAsStateWithLifecycle()
    val state by viewModel.importState.collectAsStateWithLifecycle()

    LaunchedEffect(prefillUrl) { viewModel.prefill(prefillUrl) }
    LaunchedEffect(state) {
        val s = state
        if (s is ImportState.Success && s.isLogin) {
            viewModel.acknowledge()
            onLoggedIn()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(SurfaceRaised, Background))),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(56.dp),
        ) {
            Column(Modifier.widthIn(max = 360.dp)) {
                Image(
                    painter = painterResource(R.drawable.banner),
                    contentDescription = null,
                    modifier = Modifier.size(width = 240.dp, height = 135.dp),
                )
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.width(560.dp)) {
                when (val s = state) {
                    is ImportState.Running -> ImportProgressView(s.progress, onCancel = viewModel::cancel)
                    else -> LoginForm(
                        url = url,
                        invalid = invalid,
                        error = (s as? ImportState.Failed)?.takeIf { it.isLogin }?.error?.localizedMessage(),
                        onUrlChange = viewModel::onUrlChange,
                        onSubmit = viewModel::submit,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginForm(
    url: String,
    invalid: Boolean,
    error: String?,
    onUrlChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val buttonFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    Text(stringResource(R.string.login_url_label), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(10.dp))
    TvTextField(
        value = url,
        onValueChange = onUrlChange,
        placeholder = stringResource(R.string.login_url_hint),
        leadingIcon = Icons.Filled.Link,
        keyboardType = KeyboardType.Uri,
        imeAction = ImeAction.Go,
        onImeAction = { buttonFocus.safeRequestFocus() },
        modifier = Modifier.focusRequester(fieldFocus),
    )
    val message = when {
        invalid -> stringResource(R.string.error_invalid_url)
        error != null -> error
        else -> null
    }
    if (message != null) {
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(20.dp))
    Button(onClick = onSubmit, modifier = Modifier.focusRequester(buttonFocus)) {
        Text(stringResource(if (error != null) R.string.action_retry else R.string.login_button))
    }
    Spacer(Modifier.height(24.dp))
    Text(
        stringResource(R.string.login_tip),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LaunchedEffect(Unit) {
        if (url.isEmpty()) fieldFocus.safeRequestFocus() else buttonFocus.safeRequestFocus()
    }
}

@Composable
private fun ImportProgressView(progress: ImportProgress, onCancel: () -> Unit) {
    val context = LocalContext.current
    val cancelFocus = remember { FocusRequester() }
    val title = when (progress.phase) {
        ImportProgress.Phase.CONNECTING -> R.string.import_connecting
        ImportProgress.Phase.DOWNLOADING -> R.string.import_downloading
        ImportProgress.Phase.SAVING -> R.string.import_saving
    }
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(20.dp))
    val fraction = if (progress.phase == ImportProgress.Phase.DOWNLOADING && progress.totalBytes > 0) {
        progress.bytesRead.toFloat() / progress.totalBytes
    } else {
        null
    }
    ProgressBar(fraction, height = 6.dp)
    Spacer(Modifier.height(14.dp))
    if (progress.bytesRead > 0) {
        val read = Formatter.formatShortFileSize(context, progress.bytesRead)
        val sizeText = if (progress.totalBytes > 0) {
            stringResource(R.string.import_bytes_of, read, Formatter.formatShortFileSize(context, progress.totalBytes))
        } else {
            stringResource(R.string.import_bytes, read)
        }
        Text(sizeText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (progress.entries > 0) {
        Text(
            pluralStringResource(R.plurals.import_entries, progress.entries, progress.entries),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(24.dp))
    OutlinedButton(onClick = onCancel, modifier = Modifier.focusRequester(cancelFocus)) {
        Text(stringResource(R.string.action_cancel))
    }
    LaunchedEffect(Unit) { cancelFocus.safeRequestFocus() }
}
