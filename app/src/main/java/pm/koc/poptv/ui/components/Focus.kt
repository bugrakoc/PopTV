package pm.koc.poptv.ui.components

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

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

/** OK on a remote, Enter on a keyboard. */
fun isSelectKey(keyCode: Int): Boolean =
    keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
        keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER

/**
 * Drops OK events that belong to a press which started outside this subtree. A long press opens a menu on
 * key down; without this the held repeats and the release would reach the menu's first item and select it.
 * Put it on the root of a dialog (a separate window that only sees the tail of such a press).
 */
fun Modifier.ignorePressesStartedElsewhere(): Modifier = composed {
    val armed = remember { BooleanArray(1) }
    onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        if (!isSelectKey(native.keyCode)) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (native.repeatCount == 0) armed[0] = true
                !armed[0]
            }
            KeyEventType.KeyUp -> {
                val started = armed[0]
                armed[0] = false
                !started
            }
            else -> false
        }
    }
}
