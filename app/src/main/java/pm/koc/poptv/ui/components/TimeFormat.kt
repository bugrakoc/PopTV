package pm.koc.poptv.ui.components

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Date

/** Locale- and 12/24h-aware time formatting. */
class TimeFormatter(private val context: Context) {
    private val timeFormat = DateFormat.getTimeFormat(context)

    fun time(epochMs: Long): String = timeFormat.format(Date(epochMs))

    fun range(start: Long, end: Long): String = "${time(start)} – ${time(end)}"

    fun dayAndTime(epochMs: Long): String = DateUtils.formatDateTime(
        context, epochMs,
        DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_SHOW_TIME,
    )

    fun day(epochMs: Long): String = DateUtils.formatDateTime(
        context, epochMs,
        DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL,
    )

    fun dateTime(epochMs: Long): String = DateUtils.formatDateTime(
        context, epochMs,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )
}

@Composable
fun rememberTimeFormatter(): TimeFormatter {
    val context = LocalContext.current
    return remember(context) { TimeFormatter(context) }
}

/** "1:02:03" / "12:34" playback positions. */
fun formatPosition(ms: Long): String = DateUtils.formatElapsedTime((ms.coerceAtLeast(0)) / 1000)
