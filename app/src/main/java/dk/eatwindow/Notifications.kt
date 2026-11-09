package dk.eatwindow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import dk.eatwindow.EatingWindow.Status
import java.time.LocalTime
import java.time.ZoneId

/**
 * One notification slot that follows the day: a silent live countdown while the window is open, an alert
 * when last call begins, the fast's countdown from the moment the kitchen closes, and an alert when the
 * fast is complete. The optional nudge to start has a slot of its own, so it never hides that alert.
 */
object Notifications {
    private const val ID = 1
    private const val NUDGE_ID = 2
    private const val ALERTS = "eating_window"
    private const val COUNTDOWN = "countdown"

    /** Shows the open window's countdown; with [alert], also sounds the last-call reminder. */
    fun showWindow(context: Context, window: History.Window, alert: Boolean) {
        val status = EatingWindow.status(window, System.currentTimeMillis()) as? Status.Open ?: return
        val closes = window.end
        val (title, text) = when {
            alert -> "Last call · 1 hour left" to "Your eating window closes at ${clockTime(context, closes)}. Time for your final meal."
            status.isLastCall -> "Last call" to "Closes at ${clockTime(context, closes)}. Make your final meal count."
            else -> "Eating window open" to "Closes at ${clockTime(context, closes)}. We'll nudge you an hour before."
        }
        post(context, builder(context, if (alert) ALERTS else COUNTDOWN, title, text).countingDownTo(closes))
    }

    /** Shows the fast's countdown to 16 hours; with [alert], also sounds the kitchen's-closed reminder. */
    fun showFast(context: Context, lastBite: Long, alert: Boolean) {
        val done = EatingWindow.fastDoneAt(lastBite)
        val (title, text) =
            if (alert) "Kitchen's closed" to "Your fast has begun. 16 hours takes you to ${clockTime(context, done)}."
            else "Fasting" to "Your 16-hour fast is done at ${clockTime(context, done)}."
        post(context, builder(context, if (alert) ALERTS else COUNTDOWN, title, text).countingDownTo(done))
    }

    fun showFastDone(context: Context, streak: Int) {
        val text = "16 hours without food. Start your window with your first bite." +
            if (streak > 1) " That's $streak fasts in a row." else ""
        post(
            context,
            builder(context, ALERTS, "Fast complete", text)
                .setAutoCancel(true)
                .addAction(Notification.Action.Builder(null, "Start now", Reminders.startIntent(context, 20)).build()),
        )
    }

    /** Reminds the user to start at their usual time; either button starts the window. */
    fun showNudge(context: Context, usual: LocalTime) {
        val zone = ZoneId.systemDefault()
        val usualToday = EatingWindow.startAt(usual.hour, usual.minute, System.currentTimeMillis(), zone)
        val text = "It's your usual start time. Tap Start now with your first bite to get your 8 hours."
        post(
            context,
            builder(context, ALERTS, "Ready for your eating window?", text)
                .setAutoCancel(true)
                .addAction(Notification.Action.Builder(null, "Start now", Reminders.startIntent(context, 20)).build())
                .addAction(
                    Notification.Action.Builder(
                        null, "Started at ${clockTime(context, usualToday)}", Reminders.startIntent(context, 21, usualToday),
                    ).build()
                ),
            NUDGE_ID,
        )
    }

    fun clear(context: Context) = manager(context).cancelAll()

    fun clearNudge(context: Context) = manager(context).cancel(NUDGE_ID)

    private fun builder(context: Context, channel: String, title: String, text: String) =
        Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(Reminders.openApp(context))

    private fun Notification.Builder.countingDownTo(at: Long) = setOngoing(true)
        .setWhen(at)
        .setShowWhen(true)
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
        // Asks for a Live Update chip; honoured from Android 16 QPR2 (API 36.1), ignored before.
        .addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })

    private fun post(context: Context, builder: Notification.Builder, id: Int = ID) {
        manager(context).createNotificationChannels(
            listOf(
                NotificationChannel(ALERTS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Last call, kitchen's closed and fast complete"
                },
                NotificationChannel(COUNTDOWN, "Countdown", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Time left in your eating window or fast"
                    setShowBadge(false)
                },
            )
        )
        manager(context).notify(id, builder.build())
    }

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
}
