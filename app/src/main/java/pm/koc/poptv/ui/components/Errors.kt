package pm.koc.poptv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pm.koc.poptv.R
import pm.koc.poptv.data.net.AppError

@Composable
fun AppError.localizedMessage(): String = when (this) {
    is AppError.InvalidUrl -> stringResource(R.string.error_invalid_url)
    is AppError.Unauthorized -> stringResource(R.string.error_unauthorized, code)
    is AppError.NotFound -> stringResource(R.string.error_not_found)
    is AppError.Http -> stringResource(R.string.error_http, code)
    is AppError.NoHost -> stringResource(R.string.error_no_host)
    is AppError.Timeout -> stringResource(R.string.error_timeout)
    is AppError.Ssl -> stringResource(R.string.error_ssl)
    is AppError.Network -> stringResource(R.string.error_network)
    is AppError.EmptyPlaylist -> stringResource(R.string.error_empty_playlist)
    is AppError.InvalidPlaylist -> stringResource(R.string.error_invalid_playlist)
    is AppError.InvalidGuide -> stringResource(R.string.error_invalid_guide)
    is AppError.Storage -> stringResource(R.string.error_storage)
    is AppError.Unknown -> stringResource(R.string.error_unknown)
}
