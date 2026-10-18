package pm.koc.poptv.ui.components

import androidx.compose.ui.focus.FocusRequester

/** Requests focus without crashing when the target is not (yet) attached. */
fun FocusRequester.safeRequestFocus(): Boolean =
    try {
        requestFocus()
        true
    } catch (_: IllegalStateException) {
        false
    }
