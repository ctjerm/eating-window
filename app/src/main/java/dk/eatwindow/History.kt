package dk.eatwindow

/** Past eating windows, the fasts between them, and the streak of fasts that reached 16 hours. */
object History {
    data class Window(val start: Long, val end: Long)

    /** Time without food after a window: until the next window started, or until now while [ongoing]. */
    data class Fast(val start: Long, val end: Long, val ongoing: Boolean) {
        val complete get() = end - start >= EatingWindow.fastLength.inWholeMilliseconds
    }

    /** The fasts between [windows], oldest first, and the one under way once the latest window has ended. */
    fun fasts(windows: List<Window>, nowMillis: Long) =
        windows.zipWithNext { a, b -> Fast(a.end, b.start, ongoing = false) } +
            listOfNotNull(windows.lastOrNull()?.takeIf { it.end <= nowMillis }?.let { Fast(it.end, nowMillis, ongoing = true) })

    /** Completed fasts in a row, up to the latest; a fast still under way neither counts nor breaks it. */
    fun streak(fasts: List<Fast>) =
        fasts.asReversed().dropWhile { it.ongoing && !it.complete }.takeWhile { it.complete }.size

    fun best(fasts: List<Fast>) = fasts.runningFold(0) { run, fast -> if (fast.complete) run + 1 else 0 }.max()

    /**
     * Ends the latest window at [at], e.g. early or with a bite after it closed, but never before it started.
     * A bite once the fast after it was complete starts an empty window instead, so that fast is kept.
     */
    fun endLatest(windows: List<Window>, at: Long): List<Window> {
        val latest = windows.last()
        if (at >= EatingWindow.fastDoneAt(latest.end)) return windows + Window(at, at)
        return windows.dropLast(1) + latest.copy(end = maxOf(at, latest.start))
    }

    /** Stored as one "start,end" line per window, oldest first. */
    fun encode(windows: List<Window>) = windows.joinToString("\n") { "${it.start},${it.end}" }

    fun decode(text: String) = text.lines().filter { it.isNotBlank() }.map { line ->
        line.split(',').let { (start, end) -> Window(start.toLong(), end.toLong()) }
    }
}
