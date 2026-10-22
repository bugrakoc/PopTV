package pm.koc.poptv.ui.phone

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pm.koc.poptv.R
import pm.koc.poptv.data.repo.ImportProgress
import pm.koc.poptv.data.repo.ImportState
import pm.koc.poptv.ui.components.localizedMessage
import pm.koc.poptv.ui.login.LoginViewModel
import pm.koc.poptv.ui.theme.Background
import pm.koc.poptv.ui.theme.SurfaceRaised

@Composable
fun PhoneLoginScreen(
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

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SurfaceRaised, Background)))
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            Image(
                painter = painterResource(R.drawable.banner),
                contentDescription = null,
                modifier = Modifier.size(width = 192.dp, height = 108.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.login_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
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
            Spacer(Modifier.height(32.dp))
            Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val keyboard = LocalSoftwareKeyboardController.current
    val message = when {
        invalid -> stringResource(R.string.error_invalid_url)
        error != null -> error
        else -> null
    }
    fun submit() {
        keyboard?.hide()
        onSubmit()
    }
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text(stringResource(R.string.login_url_label)) },
        placeholder = { Text(stringResource(R.string.login_url_hint), maxLines = 1) },
        leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
        isError = message != null,
        supportingText = message?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { submit() }),
        maxLines = 4,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = ::submit, enabled = url.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (error != null) R.string.action_retry else R.string.login_button))
    }
}

@Composable
private fun ImportProgressView(progress: ImportProgress, onCancel: () -> Unit) {
    val context = LocalContext.current
    val title = when (progress.phase) {
        ImportProgress.Phase.CONNECTING -> R.string.import_connecting
        ImportProgress.Phase.DOWNLOADING -> R.string.import_downloading
        ImportProgress.Phase.SAVING -> R.string.import_saving
    }
    Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(16.dp))
    if (progress.phase == ImportProgress.Phase.DOWNLOADING && progress.totalBytes > 0) {
        LinearProgressIndicator(
            progress = { progress.bytesRead.toFloat() / progress.totalBytes },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(12.dp))
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
    Spacer(Modifier.height(20.dp))
    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
}
