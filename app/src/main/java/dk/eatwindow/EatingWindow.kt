package dk.eatwindow

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/** Pure timing rules for the 8-hour eating window and the 16-hour fast after it. Times are epoch millis. */
object EatingWindow {
    val length = 8.hours
    private val warningAfter = 7.hours

    /** Share of the window that is the final "last call" stretch. */
    val lastCallShare = ((length - warningAfter) / length).toFloat()

    val fastLength = 16.hours
    fun fastDoneAt(lastBite: Long) = lastBite + fastLength.inWholeMilliseconds

    /** Where things stand after the [latest] window, whose end is when it closes or, if cut short, when it ended. */
    fun status(latest: History.Window?, nowMillis: Long): Status = when {
        latest == null -> Status.NotStarted
        nowMillis < latest.end -> Status.Open((latest.end - nowMillis).milliseconds)
        else -> Status.Fasting((nowMillis - latest.end).milliseconds)
    }

    fun warningAt(startMillis: Long) = startMillis + warningAfter.inWholeMilliseconds
    fun closesAt(startMillis: Long) = startMillis + length.inWholeMilliseconds

    /** The latest moment at or before now that shows [hour]:[minute] on the clock, either time it shows when the clocks go back. */
    fun startAt(hour: Int, minute: Int, nowMillis: Long, zone: ZoneId): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val time = LocalTime.of(hour, minute)
        // Newest first: the later reading of a repeated time, then the earlier one.
        fun on(date: LocalDate) = ZonedDateTime.of(date, time, zone)
            .let { listOf(it.withLaterOffsetAtOverlap(), it.withEarlierOffsetAtOverlap()) }
        val today = now.toLocalDate()
        return (on(today) + on(today.minusDays(1))).first { it <= now }.toInstant().toEpochMilli()
    }

    sealed interface Status {
        data object NotStarted : Status

        data class Open(val remaining: Duration) : Status {
            /** Share of the window already used, 0..1. */
            val progress get() = (1 - remaining / length).toFloat()
            val isLastCall get() = remaining <= length - warningAfter
        }

        data class Fasting(val fasted: Duration) : Status {
            val remaining get() = (fastLength - fasted).coerceAtLeast(Duration.ZERO)
            /** Share of the fast already done, 0..1. */
            val progress get() = (fasted / fastLength).toFloat().coerceAtMost(1f)
            val isDone get() = fasted >= fastLength
            val stage get() = FastStage.entries.last { fasted >= it.from }
        }
    }

    /** What the body runs on as a fast goes on, from [from] hours after the last bite. Timings vary by person. */
    enum class FastStage(val from: Duration) { Digesting(0.hours), OnReserves(4.hours), SwitchingToFat(12.hours) }
}
