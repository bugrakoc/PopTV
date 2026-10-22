package pm.koc.poptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Phone theme: Material 3 with the PopTV palette. It is nested inside [PopTVTheme] so shared components that
 * read the TV theme (images, progress bars, player overlays) look the same on both.
 */
@Composable
fun PopTVPhoneTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = Accent,
        onPrimary = OnBackground,
        primaryContainer = AccentContainer,
        onPrimaryContainer = OnBackground,
        secondary = AccentWarm,
        onSecondary = Background,
        secondaryContainer = SurfaceHigh,
        onSecondaryContainer = OnBackground,
        tertiary = AccentWarm,
        background = Background,
        onBackground = OnBackground,
        surface = Background,
        onSurface = OnBackground,
        surfaceVariant = SurfaceRaised,
        onSurfaceVariant = OnSurfaceMuted,
        surfaceContainerLowest = Background,
        surfaceContainerLow = SurfaceDark,
        surfaceContainer = SurfaceDark,
        surfaceContainerHigh = SurfaceRaised,
        surfaceContainerHighest = SurfaceHigh,
        inverseSurface = OnBackground,
        inverseOnSurface = Background,
        outline = OnSurfaceMuted,
        outlineVariant = SurfaceHigh,
        error = ErrorRed,
        scrim = Scrim,
    )
    PopTVTheme {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
