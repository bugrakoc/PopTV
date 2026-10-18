package pm.koc.poptv.ui.components

import androidx.compose.ui.focus.FocusRequester

/**
 * Requests focus, retrying for a few frames while lazy lists finish their first layout.
 * Returns true once focus was granted.
 */
suspend fun FocusRequester.requestFocusWithRetry(attempts: Int = 10, delayMs: Long = 60): Boolean {
    repeat(attempts) {
        kotlinx.coroutines.delay(delayMs)
        if (safeRequestFocus()) return true
    }
    return false
}

/** Requests focus without crashing when the target is not (yet) attached. */
fun FocusRequester.safeRequestFocus(): Boolean =
    try {
        requestFocus()
        true
    } catch (_: IllegalStateException) {
        false
    }
