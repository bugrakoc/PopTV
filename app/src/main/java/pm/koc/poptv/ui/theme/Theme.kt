package pm.koc.poptv.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** PopTV is dark-only: TVs are watched in dim rooms and the content is the focus. */
@Composable
fun PopTVTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = Accent,
        onPrimary = OnBackground,
        primaryContainer = AccentContainer,
        onPrimaryContainer = OnBackground,
        secondary = AccentWarm,
        onSecondary = Background,
        tertiary = AccentWarm,
        background = Background,
        onBackground = OnBackground,
        surface = SurfaceDark,
        onSurface = OnBackground,
        surfaceVariant = SurfaceRaised,
        onSurfaceVariant = OnSurfaceMuted,
        inverseSurface = OnBackground,
        inverseOnSurface = Background,
        border = FocusBorder,
        borderVariant = SurfaceHigh,
        error = ErrorRed,
        scrim = Scrim,
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
