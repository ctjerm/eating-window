package dk.eatwindow

import dk.eatwindow.History.Fast
import dk.eatwindow.History.Window
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryTest {
    private val hour = 3_600_000L

    /** A window between two hours counted from the epoch. */
    private fun window(from: Int, to: Int) = Window(from * hour, to * hour)

    private fun fasts(vararg windows: Window, now: Int) = History.fasts(windows.toList(), now * hour)

    @Test
    fun `stored windows read back unchanged`() {
        val windows = listOf(Window(1, 2), Window(30, 40))
        assertEquals(windows, History.decode(History.encode(windows)))
    }

    @Test
    fun `nothing stored reads back as no windows`() {
        assertEquals(emptyList<Window>(), History.decode(""))
    }

    @Test
    fun `the time between two windows is a fast, and the time since the last one is a fast under way`() {
        assertEquals(
            listOf(Fast(8 * hour, 24 * hour, ongoing = false), Fast(32 * hour, 40 * hour, ongoing = true)),
            fasts(window(0, 8), window(24, 32), now = 40),
        )
    }

    @Test
    fun `an open window has no fast after it yet`() {
        assertEquals(emptyList<Fast>(), fasts(window(0, 8), now = 5))
    }

    @Test
    fun `a fast of sixteen hours is complete`() {
        assertEquals(true, Fast(0, 16 * hour, ongoing = false).complete)
    }

    @Test
    fun `a fast a minute short of sixteen hours is not complete`() {
        assertEquals(false, Fast(0, 16 * hour - 60_000, ongoing = false).complete)
    }

    @Test
    fun `no fasts is no streak`() {
        assertEquals(0, History.streak(emptyList()))
    }

    @Test
    fun `completed fasts in a row make the streak`() {
        assertEquals(3, History.streak(fasts(window(0, 8), window(24, 32), window(48, 56), now = 74)))
    }

    @Test
    fun `a fast still under way neither counts nor breaks the streak`() {
        assertEquals(2, History.streak(fasts(window(0, 8), window(24, 32), window(48, 56), now = 60)))
    }

    @Test
    fun `starting a window early breaks the streak`() {
        // The fast from hour 8 to 20 is four hours short.
        assertEquals(2, History.streak(fasts(window(0, 8), window(20, 28), window(44, 52), now = 70)))
    }

    @Test
    fun `best streak is the longest run of completed fasts`() {
        val fasts = listOf(16, 16, 16, 10, 16).map { Fast(0, it * hour, ongoing = false) }
        assertEquals(3, History.best(fasts))
    }

    @Test
    fun `a bite after closing moves the end of the latest window`() {
        assertEquals(listOf(window(0, 8), window(24, 34)), History.endLatest(listOf(window(0, 8), window(24, 32)), 34 * hour))
    }

    @Test
    fun `a bite after a completed fast keeps that fast and starts the next fast from the bite`() {
        // The fast from hour 32 had reached 16 hours by the snack at hour 49.
        val windows = History.endLatest(listOf(window(0, 8), window(24, 32)), 49 * hour)
        assertEquals(listOf(window(0, 8), window(24, 32), window(49, 49)), windows)
        assertEquals(2, History.streak(History.fasts(windows, 50 * hour)))
    }

    @Test
    fun `a window cannot end before it started`() {
        assertEquals(listOf(window(24, 24)), History.endLatest(listOf(window(24, 32)), 20 * hour))
    }
}
