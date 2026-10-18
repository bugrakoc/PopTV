package pm.koc.poptv.data.net

import pm.koc.poptv.data.prefs.AppSettings
import javax.inject.Inject
import javax.inject.Singleton

/** Current User-Agent from settings, read synchronously by OkHttp interceptors and the player. */
@Singleton
class UserAgentHolder @Inject constructor() {
    @Volatile
    var value: String = AppSettings.DEFAULT_USER_AGENT
}
