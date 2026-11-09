package dk.eatwindow

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

class DailyStartTest {
    private val zone = ZoneOffset.UTC
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()
    private val now = at("2026-09-25T10:00:00Z")
    private val noon = LocalTime.of(12, 0)

    private fun nextAt(time: LocalTime, from: Long = now) =
        Instant.ofEpochMilli(DailyStart.nextAt(time, from, zone)).toString()

    @Test
    fun `preset later today is due today`() {
        assertEquals("2026-09-25T12:00:00Z", nextAt(noon))
    }

    @Test
    fun `preset already passed today is due tomorrow`() {
        assertEquals("2026-09-26T09:00:00Z", nextAt(LocalTime.of(9, 0)))
    }

    @Test
    fun `preset at this very moment is due tomorrow`() {
        assertEquals("2026-09-26T12:00:00Z", nextAt(noon, from = at("2026-09-25T12:00:00Z")))
    }

    private val copenhagen = java.time.ZoneId.of("Europe/Copenhagen")

    @Test
    fun `preset in the hour skipped by the spring change is due at that clock time the next day`() {
        // Clocks went from 02:00 to 03:00 on 29 March; at 05:00 the next 02:30 is on the 30th, at UTC+2.
        val next = DailyStart.nextAt(LocalTime.of(2, 30), at("2026-03-29T03:00:00Z"), copenhagen)
        assertEquals("2026-03-30T00:30:00Z", Instant.ofEpochMilli(next).toString())
    }

    @Test
    fun `preset in the hour repeated by the autumn change is due once, at its first occurrence`() {
        // Clocks went from 03:00 back to 02:00 on 25 October. Just after the first 02:30, the next is the 26th.
        val next = DailyStart.nextAt(LocalTime.of(2, 30), at("2026-10-25T00:30:00Z"), copenhagen)
        assertEquals("2026-10-26T01:30:00Z", Instant.ofEpochMilli(next).toString())
    }

    /** A window that started at [iso] and ran its full eight hours. */
    private fun windowFrom(iso: String) = at(iso).let { History.Window(it, EatingWindow.closesAt(it)) }

    @Test
    fun `nudges when no window has ever started`() {
        assertEquals(true, DailyStart.shouldNudge(latest = null, nowMillis = now, zone = zone))
    }

    @Test
    fun `nudges once the fast after an earlier day's window is done`() {
        // 09:00 to 17:00 yesterday, so 17 hours fasted by 10:00.
        assertEquals(true, DailyStart.shouldNudge(windowFrom("2026-09-24T09:00:00Z"), now, zone))
    }

    @Test
    fun `stays quiet when a window already started today`() {
        assertEquals(false, DailyStart.shouldNudge(windowFrom("2026-09-25T08:00:00Z"), now, zone))
    }

    @Test
    fun `stays quiet while yesterday's window is still open`() {
        // Started 20:00 yesterday, so it runs until 04:00 today.
        assertEquals(false, DailyStart.shouldNudge(windowFrom("2026-09-24T20:00:00Z"), at("2026-09-25T01:00:00Z"), zone))
    }

    @Test
    fun `stays quiet while the fast still has time to go`() {
        // 12:00 to 20:00 yesterday, so 14 of 16 hours fasted by 10:00; the fast-complete alert comes later.
        assertEquals(false, DailyStart.shouldNudge(windowFrom("2026-09-24T12:00:00Z"), now, zone))
    }
}
