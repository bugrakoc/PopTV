package pm.koc.poptv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import dagger.hilt.android.AndroidEntryPoint
import pm.koc.poptv.ui.LocalIsTv
import pm.koc.poptv.ui.isTelevision
import pm.koc.poptv.ui.nav.PopTvNavHost
import pm.koc.poptv.ui.phone.PhoneNavHost
import pm.koc.poptv.ui.theme.PopTVPhoneTheme
import pm.koc.poptv.ui.theme.PopTVTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Debug/setup convenience: `adb shell am start -n <pkg>/pm.koc.poptv.MainActivity --es playlist_url "<url>"`
     * pre-fills the login field. It never logs in by itself.
     */
    private val prefillUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefillUrl.value = intent?.getStringExtra(EXTRA_PLAYLIST_URL)
        val tv = isTelevision()
        setContent {
            CompositionLocalProvider(LocalIsTv provides tv) {
                if (tv) {
                    PopTVTheme { PopTvNavHost(prefillUrl = prefillUrl.value) }
                } else {
                    PopTVPhoneTheme { PhoneNavHost(prefillUrl = prefillUrl.value) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_PLAYLIST_URL)?.let { prefillUrl.value = it }
    }

    companion object {
        const val EXTRA_PLAYLIST_URL = "playlist_url"
    }
}
