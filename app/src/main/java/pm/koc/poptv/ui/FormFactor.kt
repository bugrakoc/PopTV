package pm.koc.poptv.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True on TVs (D-pad UI with Compose for TV), false on phones and tablets (touch UI with Material 3).
 * Shared components such as dialogs and menus pick their look from this.
 */
val LocalIsTv = staticCompositionLocalOf { true }

fun Context.isTelevision(): Boolean {
    val uiMode = getSystemService(UiModeManager::class.java)?.currentModeType
    return uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}
