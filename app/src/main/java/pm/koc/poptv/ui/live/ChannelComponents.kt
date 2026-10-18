package pm.koc.poptv.ui.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import pm.koc.poptv.R
import pm.koc.poptv.data.db.StreamEntity
import pm.koc.poptv.data.repo.Programme
import pm.koc.poptv.ui.components.ProgressBar
import pm.koc.poptv.ui.components.RemoteImage
import pm.koc.poptv.ui.components.rememberTimeFormatter
import pm.koc.poptv.ui.theme.AccentWarm

private val RowShape = RoundedCornerShape(10.dp)

@Composable
fun listItemColors(selected: Boolean = false) = ClickableSurfaceDefaults.colors(
    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
    focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
    focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
    pressedContainerColor = MaterialTheme.colorScheme.inverseSurface,
    pressedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
)

@Composable
fun CategoryItem(
    title: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RowShape),
        colors = listItemColors(selected),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (count != null) {
                Spacer(Modifier.width(8.dp))
                Text(count.toString(), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * One channel: number, logo, name, current programme with progress and the next programme.
 * [loadProgrammes] is called lazily for channels that have a guide match.
 */
@Composable
fun ChannelRow(
    stream: StreamEntity,
    title: String,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    loadProgrammes: suspend (String) -> List<Programme>,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    showNext: Boolean = true,
) {
    val programmes by produceState(initialValue = emptyList<Programme>(), stream.epgChannelId) {
        value = stream.epgChannelId?.let { loadProgrammes(it) }.orEmpty()
    }
    val time = rememberTimeFormatter()
    val now = System.currentTimeMillis()
    val current = programmes.firstOrNull { it.isLive(now) }
    val next = programmes.firstOrNull { it.start >= (current?.end ?: now) }

    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RowShape),
        colors = listItemColors(isCurrent),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.015f),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stream.number.takeIf { it > 0 }?.toString().orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(44.dp),
                maxLines = 1,
            )
            RemoteImage(
                url = stream.logo,
                fallbackText = title,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.size(width = 76.dp, height = 46.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (current != null) {
                    Text(
                        "${time.time(current.start)}  ${current.title}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    ProgressBar(current.progress(now), height = 3.dp)
                    if (showNext && next != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            stringResource(R.string.epg_next_format, time.time(next.start), next.title),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (isCurrent) {
                Icon(Icons.Filled.Equalizer, contentDescription = stringResource(R.string.live_now_playing), modifier = Modifier.size(20.dp))
            }
            if (isFavorite) {
                Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.favorite), tint = AccentWarm, modifier = Modifier.size(20.dp))
            }
        }
    }
}
