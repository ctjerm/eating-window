package dk.eatwindow

import dk.eatwindow.EatingWindow.Status
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Rules for the optional daily nudge to start the window at a preset time. */
object DailyStart {
    /**
     * The next moment after now that shows [time] on the clock. A time the clocks skip falls an hour later;
     * a time they repeat counts once, at its first occurrence.
     */
    fun nextAt(time: LocalTime, nowMillis: Long, zone: ZoneId): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val today = now.toLocalDate()
        return listOf(today, today.plusDays(1)).map { ZonedDateTime.of(it, time, zone) }.first { it > now }
            .toInstant().toEpochMilli()
    }

    /** Nudge unless a window already started today, or the fast is still under way: its own alert comes when done. */
    fun shouldNudge(latest: History.Window?, nowMillis: Long, zone: ZoneId): Boolean {
        if (latest == null) return true
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return Instant.ofEpochMilli(latest.start).atZone(zone).toLocalDate() != today &&
            (EatingWindow.status(latest, nowMillis) as? Status.Fasting)?.isDone == true
    }
}
