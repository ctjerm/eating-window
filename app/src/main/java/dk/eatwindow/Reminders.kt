package dk.eatwindow

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import dk.eatwindow.EatingWindow.Status
import java.time.LocalTime
import java.time.ZoneId

/**
 * Entry point for every change to the window, from the app, the Quick Settings tile or the system:
 * stores the start time and history, and keeps the alarms and the status notification in step.
 */
object Reminders {
    private const val PREFS = "eating_window"
    private const val KEY_START = "start"
    private const val KEY_HISTORY = "history"
    private const val KEY_PRESET = "preset"
    private const val EXTRA_KIND = "kind"
    private const val EXTRA_AT = "at"
    const val ACTION_START = "dk.eatwindow.START"
    private const val NUDGE = 0
    private const val WARNING = 7
    private const val CLOSED = 8
    private const val FAST_DONE = 16

    fun startTime(context: Context): Long? =
        prefs(context).getLong(KEY_START, 0L).takeIf { it > 0L }

    /** Finished windows, oldest first. The current window is not included. */
    fun history(context: Context) = History.decode(prefs(context).getString(KEY_HISTORY, "")!!)

    /** Every window, oldest first; the current one ends when it closes. */
    fun windows(context: Context) =
        history(context) + listOfNotNull(startTime(context)?.let { History.Window(it, EatingWindow.closesAt(it)) })

    fun latest(context: Context) = windows(context).lastOrNull()

    fun status(context: Context, now: Long = System.currentTimeMillis()) = EatingWindow.status(latest(context), now)

    fun streak(context: Context, now: Long = System.currentTimeMillis()) = History.streak(History.fasts(windows(context), now))

    /**
     * Starts a new window, filing the previous one in the history. [at] may be in the past,
     * but not before the previous window ended: windows never overlap.
     */
    fun start(context: Context, at: Long = System.currentTimeMillis()): Long {
        val windows = windows(context)
        val start = maxOf(at, minOf(windows.lastOrNull()?.end ?: at, System.currentTimeMillis()))
        prefs(context).edit()
            // A window still open when the next one starts ends there.
            .putString(KEY_HISTORY, History.encode(windows.map { it.copy(end = minOf(it.end, start)) }))
            .putLong(KEY_START, start)
            .apply()
        arm(context)
        return start
    }

    /** Corrects the current window's start, e.g. when the first bite came before the tap. */
    fun moveStart(context: Context, to: Long) {
        val start = maxOf(to, history(context).lastOrNull()?.end ?: to)
        prefs(context).edit().putLong(KEY_START, start).apply()
        arm(context)
    }

    /** Ends the latest window at [at]: early, or later than it closed after one more bite. The fast runs from there. */
    fun endAt(context: Context, at: Long = System.currentTimeMillis()) {
        val windows = windows(context).ifEmpty { return }
        prefs(context).edit()
            .putString(KEY_HISTORY, History.encode(History.endLatest(windows, at)))
            .remove(KEY_START)
            .apply()
        arm(context)
    }

    /** The usual daily start time to be nudged at, if one is set. */
    fun presetStart(context: Context): LocalTime? =
        prefs(context).getInt(KEY_PRESET, -1).takeIf { it >= 0 }?.let { LocalTime.ofSecondOfDay(it * 60L) }

    fun setPresetStart(context: Context, time: LocalTime?) {
        prefs(context).edit()
            .apply { if (time == null) remove(KEY_PRESET) else putInt(KEY_PRESET, time.toSecondOfDay() / 60) }
            .apply()
        // A nudge still showing would offer the old time, or start a window after the reminder was turned off.
        Notifications.clearNudge(context)
        scheduleNudge(context)
    }

    /**
     * Re-arms any alarms still in the future; alarms are wiped on reboot and on force-stop. After a time
     * zone change it moves the daily nudge to the clock time in the new zone and redraws the notification's times.
     */
    fun reschedule(context: Context) {
        scheduleNudge(context)
        schedule(context)
        showCountdown(context, alert = false)
    }

    /** Calls [onChange] whenever the window or history changes; returns a function that stops observing. */
    fun observe(context: Context, onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
        return { prefs(context).unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** A new, moved or ended window makes earlier alarms and notifications stale. */
    private fun arm(context: Context) {
        cancelAlarms(context)
        Notifications.clear(context)
        schedule(context)
        showCountdown(context, alert = false)
    }

    private fun schedule(context: Context) {
        val latest = latest(context) ?: return
        val now = System.currentTimeMillis()
        // Every window has a fast after it; only one still open has its warnings to come.
        val alarms = mapOf(FAST_DONE to EatingWindow.fastDoneAt(latest.end)) +
            if (latest.end > now) mapOf(WARNING to EatingWindow.warningAt(latest.start), CLOSED to latest.end) else emptyMap()
        alarms.filterValues { it > now }.forEach { (kind, at) -> setAlarm(context, kind, at) }
    }

    /** The ongoing countdown for the open window or the fast under way; a fast that is done has none. */
    private fun showCountdown(context: Context, alert: Boolean) {
        val latest = latest(context) ?: return
        when (val status = EatingWindow.status(latest, System.currentTimeMillis())) {
            is Status.Open -> Notifications.showWindow(context, latest, alert)
            is Status.Fasting -> if (!status.isDone) Notifications.showFast(context, latest.end, alert)
            Status.NotStarted -> {}
        }
    }

    private fun setAlarm(context: Context, kind: Int, at: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = alarmIntent(context, kind)
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            // Alarm-clock alarms: OEM battery managers (seen on OnePlus) stretch setExact* alarms
            // by up to 75% of their delay, or drop them, but leave these on time.
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, openApp(context)), intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    private fun scheduleNudge(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, NUDGE))
        val time = presetStart(context) ?: return
        setAlarm(context, NUDGE, DailyStart.nextAt(time, System.currentTimeMillis(), ZoneId.systemDefault()))
    }

    private fun cancelAlarms(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        listOf(WARNING, CLOSED, FAST_DONE).forEach { alarms.cancel(alarmIntent(context, it)) }
    }

    internal fun onAlarm(context: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        val status = status(context, now)
        when (intent.getIntExtra(EXTRA_KIND, -1)) {
            NUDGE -> {
                scheduleNudge(context)
                val time = presetStart(context) ?: return
                if (DailyStart.shouldNudge(latest(context), now, ZoneId.systemDefault())) {
                    Notifications.showNudge(context, time)
                }
            }
            WARNING -> if (status is Status.Open) showCountdown(context, alert = true)
            CLOSED -> if (status is Status.Fasting && !status.isDone) showCountdown(context, alert = true)
            FAST_DONE -> if (status is Status.Fasting && status.isDone) Notifications.showFastDone(context, streak(context, now))
        }
    }

    /** Starts a window from a notification button, now or at a given past moment. */
    fun startIntent(context: Context, requestCode: Int, at: Long? = null): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_START).apply { at?.let { putExtra(EXTRA_AT, it) } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal fun onStartIntent(context: Context, intent: Intent) =
        start(context, intent.getLongExtra(EXTRA_AT, System.currentTimeMillis()))

    private fun alarmIntent(context: Context, kind: Int) = PendingIntent.getBroadcast(
        context,
        kind,
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_KIND, kind),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED -> Reminders.reschedule(context)
            Reminders.ACTION_START -> Reminders.onStartIntent(context, intent)
            else -> Reminders.onAlarm(context, intent)
        }
    }
}
