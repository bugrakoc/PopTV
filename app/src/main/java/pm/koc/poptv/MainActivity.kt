package pm.koc.poptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import pm.koc.poptv.ui.nav.PopTvNavHost
import pm.koc.poptv.ui.theme.PopTVTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PopTVTheme {
                PopTvNavHost()
            }
        }
    }
}
