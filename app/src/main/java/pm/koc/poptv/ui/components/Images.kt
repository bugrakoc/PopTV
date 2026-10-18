package pm.koc.poptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import pm.koc.poptv.core.TextNormalizer
import pm.koc.poptv.ui.theme.SurfaceHigh

/**
 * Remote image with a lettered placeholder shown until (and unless) the image loads. Coil sizes the request
 * to the composable's constraints, so large posters are downsampled.
 */
@Composable
fun RemoteImage(
    url: String?,
    fallbackText: String,
    modifier: Modifier = Modifier,
    shape: Shape,
    contentScale: ContentScale = ContentScale.Fit,
    placeholderColor: Color = SurfaceHigh,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(modifier.clip(shape).background(placeholderColor), contentAlignment = Alignment.Center) {
        if (!loaded) {
            Text(
                initials(fallbackText),
                style = textStyle.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                onState = { loaded = it is AsyncImagePainter.State.Success },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun initials(text: String): String {
    val words = TextNormalizer.searchKey(text).split(' ').filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}
