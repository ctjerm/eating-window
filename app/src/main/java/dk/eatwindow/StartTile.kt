package dk.eatwindow

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dk.eatwindow.EatingWindow.Status

/** Quick Settings tile: starts a window with one tap once the fast is done, or opens the app. */
class StartTile : TileService() {
    override fun onStartListening() = render()

    override fun onClick() {
        // An open window or a fast with time to go needs the app: to end the window, or to warn before starting.
        if (Reminders.status(this).let { it == Status.NotStarted || it is Status.Fasting && it.isDone }) {
            Reminders.start(this)
            render()
        } else {
            openApp()
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val latest = Reminders.latest(this)
        val status = EatingWindow.status(latest, System.currentTimeMillis())
        tile.state = if (status is Status.Open) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            val end = latest?.end ?: 0L
            tile.subtitle = when {
                status is Status.Open -> "Until ${clockTime(this, end)}"
                status is Status.Fasting && !status.isDone -> "Fasting until ${clockTime(this, EatingWindow.fastDoneAt(end))}"
                else -> "Tap to start"
            }
        }
        tile.updateTile()
    }

    private fun openApp() {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(Reminders.openApp(this))
        } else {
            // Lint flags the Intent overload for throwing from API 34; this branch only runs below it.
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
