package pm.koc.poptv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pm.koc.poptv.R
import pm.koc.poptv.domain.CategoryOverride
import pm.koc.poptv.domain.ContentType

@Composable
fun ContentType.label(): String = stringResource(
    when (this) {
        ContentType.LIVE -> R.string.type_live
        ContentType.MOVIE -> R.string.type_movie
        ContentType.SERIES -> R.string.type_series
    }
)

@Composable
fun CategoryOverride.label(): String = stringResource(
    when (this) {
        CategoryOverride.LIVE -> R.string.type_live
        CategoryOverride.MOVIE -> R.string.type_movie
        CategoryOverride.SERIES -> R.string.type_series
        CategoryOverride.HIDDEN -> R.string.type_hidden
    }
)
