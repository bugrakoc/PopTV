package pm.koc.poptv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.remember
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import pm.koc.poptv.ui.theme.SuccessGreen

private val CardShape = RoundedCornerShape(10.dp)

/** 2:3 poster with title (and optional subtitle / progress / watched marker) below. */
@Composable
fun PosterCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 150.dp,
    subtitle: String? = null,
    progress: Float? = null,
    watched: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier.width(width)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interaction,
            shape = ClickableSurfaceDefaults.shape(CardShape),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.border))),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            colors = ClickableSurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant, focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            Box(Modifier.fillMaxSize()) {
                RemoteImage(
                    url = imageUrl,
                    fallbackText = title,
                    shape = CardShape,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    textStyle = MaterialTheme.typography.headlineSmall,
                )
                if (watched) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp),
                    )
                }
                if (progress != null && progress > 0f) {
                    ProgressBar(progress, height = 4.dp, modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = if (focused) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** 16:9 channel card (logo centered) for home rows. */
@Composable
fun ChannelCard(
    title: String,
    logo: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier.width(196.dp)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = ClickableSurfaceDefaults.shape(CardShape),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.border))),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            colors = ClickableSurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant, focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            RemoteImage(
                url = logo,
                fallbackText = title,
                shape = CardShape,
                modifier = Modifier.fillMaxSize().padding(14.dp),
                placeholderColor = androidx.compose.ui.graphics.Color.Transparent,
                textStyle = MaterialTheme.typography.headlineSmall,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
