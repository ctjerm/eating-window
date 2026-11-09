package dk.eatwindow

import dk.eatwindow.EatingWindow.FastStage
import dk.eatwindow.EatingWindow.Status
import dk.eatwindow.History.Window
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import java.time.Instant
import java.time.ZoneOffset

class EatingWindowTest {
    private val start = 1_000_000L
    private fun at(offset: kotlin.time.Duration) = start + offset.inWholeMilliseconds

    /** A window that runs its full eight hours. */
    private val window = Window(start, at(8.hours))
    private fun status(now: kotlin.time.Duration) = EatingWindow.status(window, at(now))
    private fun fast(now: kotlin.time.Duration) = status(now) as Status.Fasting

    @Test
    fun `nothing is started before the first window`() {
        assertEquals(Status.NotStarted, EatingWindow.status(latest = null, nowMillis = start))
    }

    @Test
    fun `window is open with the time left until eight hours have passed`() {
        assertEquals(Status.Open(remaining = 7.hours + 30.minutes), status(30.minutes))
    }

    @Test
    fun `fast begins the moment the window closes`() {
        assertEquals(Status.Fasting(fasted = 0.hours), status(8.hours))
    }

    @Test
    fun `fast counts the time since the window closed`() {
        assertEquals(Status.Fasting(fasted = 1.hours + 30.minutes), status(9.hours + 30.minutes))
    }

    @Test
    fun `fast begins when a window is ended early`() {
        assertEquals(Status.Fasting(fasted = 1.hours), EatingWindow.status(Window(start, at(3.hours)), at(4.hours)))
    }

    @Test
    fun `progress is the share of the window that has passed`() {
        assertEquals(0.25f, (status(2.hours) as Status.Open).progress, 0.0001f)
    }

    @Test
    fun `final hour is last call`() {
        assertEquals(true, (status(7.hours) as Status.Open).isLastCall)
    }

    @Test
    fun `before the final hour is not last call`() {
        assertEquals(false, (status(7.hours - 1.minutes) as Status.Open).isLastCall)
    }

    @Test
    fun `fast progress is the share of sixteen hours fasted`() {
        assertEquals(0.25f, fast(8.hours + 4.hours).progress, 0.0001f)
    }

    @Test
    fun `fast progress stays full beyond sixteen hours`() {
        assertEquals(1f, fast(8.hours + 30.hours).progress, 0.0001f)
    }

    @Test
    fun `fast shows the time still to go`() {
        assertEquals(4.hours, fast(8.hours + 12.hours).remaining)
    }

    @Test
    fun `fast is not done a minute before sixteen hours`() {
        assertEquals(false, fast(8.hours + 16.hours - 1.minutes).isDone)
    }

    @Test
    fun `fast is done after sixteen hours`() {
        assertEquals(true, fast(8.hours + 16.hours).isDone)
    }

    @Test
    fun `fast is done sixteen hours after the last bite`() {
        assertEquals(at(24.hours), EatingWindow.fastDoneAt(at(8.hours)))
    }

    @Test
    fun `body is still digesting the last meal in the first four hours`() {
        assertEquals(FastStage.Digesting, fast(8.hours + 3.hours + 59.minutes).stage)
    }

    @Test
    fun `body runs on its stored sugar from four hours`() {
        assertEquals(FastStage.OnReserves, fast(8.hours + 4.hours).stage)
    }

    @Test
    fun `body is still on stored sugar a minute before twelve hours`() {
        assertEquals(FastStage.OnReserves, fast(8.hours + 11.hours + 59.minutes).stage)
    }

    @Test
    fun `body switches toward fat from twelve hours`() {
        assertEquals(FastStage.SwitchingToFat, fast(8.hours + 12.hours).stage)
    }

    @Test
    fun `warning fires after seven hours`() {
        assertEquals(at(7.hours), EatingWindow.warningAt(start))
    }

    @Test
    fun `window closes after eight hours`() {
        assertEquals(at(8.hours), EatingWindow.closesAt(start))
    }

    private val noon = Instant.parse("2026-09-22T12:00:45Z").toEpochMilli()
    private fun startAt(hour: Int, minute: Int) =
        Instant.ofEpochMilli(EatingWindow.startAt(hour, minute, noon, ZoneOffset.UTC)).toString()

    @Test
    fun `picked start time earlier today is today`() {
        assertEquals("2026-09-22T09:30:00Z", startAt(9, 30))
    }

    @Test
    fun `picked start time later than now is yesterday`() {
        assertEquals("2026-09-21T13:00:00Z", startAt(13, 0))
    }

    @Test
    fun `picked start time in the current minute is now`() {
        assertEquals("2026-09-22T12:00:00Z", startAt(12, 0))
    }

    @Test
    fun `picked start time in the repeated autumn hour is its latest occurrence that has passed`() {
        // Clocks went from 03:00 back to 02:00 on 25 October. At the second 02:40, only the first 02:45 has passed.
        val now = Instant.parse("2026-10-25T01:40:00Z").toEpochMilli()
        val start = EatingWindow.startAt(2, 45, now, java.time.ZoneId.of("Europe/Copenhagen"))
        assertEquals("2026-10-25T00:45:00Z", Instant.ofEpochMilli(start).toString())
    }
}
