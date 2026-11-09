package dk.eatwindow

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Built per call: the locale and Android's 24-hour setting can change while the app runs.
private fun short(context: Context) = DateTimeFormatter.ofPattern(
    DateFormat.getBestDateTimePattern(Locale.getDefault(), if (DateFormat.is24HourFormat(context)) "Hm" else "hm")
)

/** Wall-clock time in the user's zone, 24-hour or 12-hour as set in Android, e.g. "20:04" or "8:04 PM". */
fun clockTime(context: Context, millis: Long): String =
    short(context).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

fun clockTime(context: Context, time: LocalTime): String = short(context).format(time)
