package dk.eatwindow.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import dk.eatwindow.EatingWindow
import dk.eatwindow.EatingWindow.FastStage
import dk.eatwindow.EatingWindow.Status
import dk.eatwindow.History
import dk.eatwindow.Reminders
import dk.eatwindow.clockTime
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private enum class Phase { Ready, Eating, LastCall, Fasting, FastDone }

/** A start that would cut the fast under way short, waiting for the user to confirm it. */
private sealed interface EarlyStart {
    data object Now : EarlyStart
    data class At(val millis: Long) : EarlyStart
}

private fun phaseOf(status: Status) = when (status) {
    Status.NotStarted -> Phase.Ready
    is Status.Open -> if (status.isLastCall) Phase.LastCall else Phase.Eating
    is Status.Fasting -> if (status.isDone) Phase.FastDone else Phase.Fasting
}

@Composable
fun WindowScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    // Re-read storage whenever it changes, including from the Quick Settings tile.
    var stored by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val stop = Reminders.observe(context) { stored++ }
        onDispose { stop() }
    }
    val windows = remember(stored) { Reminders.windows(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmingEnd by remember { mutableStateOf(false) }
    var earlyStart by remember { mutableStateOf<EarlyStart?>(null) }
    val preset = remember(stored) { Reminders.presetStart(context) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingLastBite by remember { mutableStateOf(false) }
    var pickingPreset by remember { mutableStateOf(false) }
    var showingHistory by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val latest = windows.lastOrNull()
    val status = EatingWindow.status(latest, now)
    val phase = phaseOf(status)
    // A dialog about the phase that just ended no longer applies.
    LaunchedEffect(phase) {
        confirmingEnd = false
        earlyStart = null
    }
    val isOpen = status is Status.Open
    val fasts = History.fasts(windows, now)
    val colors = MaterialTheme.colorScheme
    val accent = when (phase) {
        Phase.Ready, Phase.Eating -> colors.primary
        Phase.LastCall -> colors.secondary
        Phase.Fasting, Phase.FastDone -> colors.tertiary
    }
    val milestones = if (latest != null && status is Status.Fasting) {
        val lastBite = latest.end
        listOf(
            "LAST BITE" to lastBite,
            "HALFWAY" to lastBite + EatingWindow.fastLength.inWholeMilliseconds / 2,
            "FAST DONE" to EatingWindow.fastDoneAt(lastBite),
        )
    } else {
        // Before a window exists, preview the times it would have if started now.
        val windowStart = latest?.start ?: now
        listOf(
            (if (latest == null) "START" else "STARTED") to windowStart,
            "LAST CALL" to EatingWindow.warningAt(windowStart),
            "CLOSES" to EatingWindow.closesAt(windowStart),
        )
    }

    fun confirmHaptic() = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP
    )

    // Scrolls when the screen is shorter than the content, as in landscape; otherwise the spacers fill the height.
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    DateTimeFormatter.ofPattern("EEEE d MMMM").format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())).uppercase(),
                    style = Eyebrow,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { pickingPreset = true }) {
                    Icon(
                        if (preset != null) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                        contentDescription = preset?.let { "Daily start reminder at ${clockTime(context, it)}" } ?: "Set a daily start reminder",
                        tint = if (preset != null) colors.primary else colors.onSurfaceVariant,
                    )
                }
                if (windows.isNotEmpty()) {
                    StreakPill(History.streak(fasts), onClick = { showingHistory = true })
                }
            }
            AnimatedContent(
                targetState = phase to (status as? Status.Fasting)?.stage,
                transitionSpec = {
                    (fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 6 }) togetherWith fadeOut(tween(200))
                },
                label = "heading",
            ) { (shown, stage) ->
                Heading(shown, stage)
            }

            Spacer(Modifier.weight(1f))
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val ringSize = min(maxWidth, 320.dp)
                Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
                    ProgressRing(
                        progress = when (status) {
                            Status.NotStarted -> 0f
                            is Status.Open -> status.progress
                            is Status.Fasting -> status.progress
                        },
                        color = accent,
                        lastCallShare = if (status is Status.Fasting) 0f else EatingWindow.lastCallShare,
                        modifier = Modifier.fillMaxSize(),
                    )
                    RingCenter(status, milestones.last().second, ringSize.value)
                }
            }
            Spacer(Modifier.weight(1f))

            Timeline(milestones, now, accent, isPreview = latest == null)
            Spacer(Modifier.height(16.dp))

            Crossfade(targetState = isOpen, label = "action") { open ->
                if (open) {
                    OutlinedButton(
                        onClick = { confirmingEnd = true },
                        modifier = ActionButton,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, colors.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
                    ) { Text("End window early", style = ActionText) }
                } else {
                    Button(
                        onClick = {
                            if (phase == Phase.Fasting) {
                                earlyStart = EarlyStart.Now
                            } else {
                                confirmHaptic()
                                Reminders.start(context)
                            }
                        },
                        modifier = ActionButton,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                    ) { Text("Start eating window", style = ActionText) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                SecondaryAction(
                    when {
                        isOpen -> "Change start time"
                        latest == null -> "Started earlier? Set the time"
                        else -> "Started earlier?"
                    },
                    onClick = { pickingStart = true },
                )
                if (status is Status.Fasting) SecondaryAction("Ate something?", onClick = { pickingLastBite = true })
            }
        }
    }

    if (pickingStart) {
        TimeDialog(
            title = if (isOpen) "Change start time" else "When did you start eating?",
            initial = localTime(if (isOpen) latest!!.start else now),
            confirmLabel = "Set start",
            onDismiss = { pickingStart = false },
            onPick = { time ->
                // The picked clock time means its latest occurrence, so a start is never in the future.
                val at = EatingWindow.startAt(time.hour, time.minute, System.currentTimeMillis(), ZoneId.systemDefault())
                when {
                    isOpen -> { confirmHaptic(); Reminders.moveStart(context, at) }
                    // Like Start, a start that ends the fast before 16 hours asks first.
                    latest != null && status is Status.Fasting && at < EatingWindow.fastDoneAt(latest.end) ->
                        earlyStart = EarlyStart.At(at)
                    else -> { confirmHaptic(); Reminders.start(context, at) }
                }
                pickingStart = false
            },
        )
    }

    if (pickingPreset) {
        TimeDialog(
            title = "Daily start reminder",
            message = "Each day at this time we'll remind you to start your window, unless you already have.",
            initial = preset ?: LocalTime.NOON,
            confirmLabel = "Save",
            clearLabel = if (preset != null) "Turn off" else null,
            onClear = {
                Reminders.setPresetStart(context, null)
                pickingPreset = false
            },
            onDismiss = { pickingPreset = false },
            onPick = { time ->
                confirmHaptic()
                Reminders.setPresetStart(context, time)
                pickingPreset = false
            },
        )
    }

    if (pickingLastBite) {
        TimeDialog(
            title = "When did you last eat?",
            message = "Your 16-hour fast restarts from then.",
            initial = localTime(now),
            confirmLabel = "Restart fast",
            onDismiss = { pickingLastBite = false },
            onPick = { time ->
                confirmHaptic()
                // Like a start, the picked clock time means its latest occurrence.
                Reminders.endAt(context, EatingWindow.startAt(time.hour, time.minute, System.currentTimeMillis(), ZoneId.systemDefault()))
                pickingLastBite = false
            },
        )
    }

    if (showingHistory) {
        HistorySheet(fasts, now, onDismiss = { showingHistory = false })
    }

    val pendingStart = earlyStart
    if (pendingStart != null && status is Status.Fasting && latest != null) {
        val streak = History.streak(fasts)
        val (title, consequence) = when (pendingStart) {
            EarlyStart.Now -> "Your fast isn't done yet" to
                "It has ${hoursMinutes(status.remaining)} to go, until ${clockTime(context, EatingWindow.fastDoneAt(latest.end))}. " +
                "Starting now ends this fast short"
            is EarlyStart.At -> "That cuts your fast short" to
                "Starting at ${clockTime(context, pendingStart.millis)} ends this fast after " +
                "${hoursMinutes((pendingStart.millis - latest.end).coerceAtLeast(0).milliseconds)}, before 16 hours"
        }
        AlertDialog(
            onDismissRequest = { earlyStart = null },
            title = { Text(title) },
            text = { Text(consequence + if (streak > 0) " and your streak of $streak starts over." else ".") },
            confirmButton = {
                TextButton(onClick = {
                    confirmHaptic()
                    when (pendingStart) {
                        EarlyStart.Now -> Reminders.start(context)
                        is EarlyStart.At -> Reminders.start(context, pendingStart.millis)
                    }
                    earlyStart = null
                }) { Text("Start anyway", color = colors.secondary, style = ActionText) }
            },
            dismissButton = {
                TextButton(onClick = { earlyStart = null }) {
                    Text("Keep fasting", color = colors.onSurface, style = ActionText)
                }
            },
        )
    }

    if (confirmingEnd) {
        AlertDialog(
            onDismissRequest = { confirmingEnd = false },
            title = { Text("End today's window?") },
            text = { Text("Your fast starts now. We'll let you know when 16 hours are up.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmHaptic()
                    Reminders.endAt(context)
                    confirmingEnd = false
                }) { Text("End window", color = colors.secondary, style = ActionText) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingEnd = false }) {
                    Text("Keep going", color = colors.onSurface, style = ActionText)
                }
            },
        )
    }
}

@Composable
private fun RowScope.SecondaryAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StreakPill(streak: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(CircleShape)
            .clickable(onClickLabel = "Show history", onClick = onClick)
            .background(colors.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(if (streak > 0) colors.primary else colors.outline, CircleShape))
        Text(
            if (streak > 0) "$streak in a row" else "History",
            style = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, fontFeatureSettings = "tnum"),
            color = colors.onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * The fasting lines claim only what human studies show. The shift from stored sugar to fat and ketones
 * typically starts beyond 12 hours (de Cabo & Mattson, NEJM 2019). 16:8 trials show modest fat loss
 * (about 1 kg, PMC10002957) and lower fasting insulin (PMC10594936). Autophagy and longevity are left
 * out: there is no human evidence for them at 16 hours.
 */
@Composable
private fun Heading(phase: Phase, stage: FastStage?) {
    val (title, subtitle) = when (phase) {
        Phase.Ready -> "Ready when\nyou are" to "Start with your first bite. You get 8 hours to eat, then 16 to fast."
        Phase.Eating -> "Eating window" to "Enjoy your meals. We'll nudge you an hour before it closes."
        Phase.LastCall -> "Last call" to "Less than an hour left. Make your final meal count."
        Phase.Fasting -> "Fasting" to when (stage) {
            FastStage.Digesting, null -> "Your body is still using your last meal. Blood sugar and insulin settle as it's absorbed."
            FastStage.OnReserves -> "Insulin is falling, and your liver is releasing stored sugar to keep your energy steady."
            FastStage.SwitchingToFat -> "Your liver's sugar stores are running low, so your body turns to fat for fuel and starts making ketones."
        }
        Phase.FastDone -> "Fast complete" to "16 hours done. Kept up over weeks, fasts like this help trim body fat and lower fasting insulin."
    }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 46.sp),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun RingCenter(status: Status, endsAt: Long, ringSizeDp: Float) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val (label, time, caption) = when (status) {
        Status.NotStarted -> Triple("8-HOUR WINDOW", EatingWindow.length, "ready to start")
        is Status.Open -> Triple("TIME LEFT", status.remaining, "until ${clockTime(context, endsAt)}")
        is Status.Fasting -> Triple(
            "FASTED",
            status.fasted,
            if (status.isDone) "16-hour goal reached" else "${hoursMinutes(status.remaining)} to go",
        )
    }
    // Numerals scale with the ring rather than the font setting, so they always fit inside it.
    val numeralSize = with(LocalDensity.current) { (ringSizeDp * 0.17f).dp.toSp() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Eyebrow, color = colors.onSurfaceVariant)
        Text(
            clock(time),
            style = TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Light,
                fontSize = numeralSize,
                letterSpacing = (-1).sp,
                fontFeatureSettings = "tnum",
            ),
            color = if (status == Status.NotStarted) colors.onSurfaceVariant else colors.onSurface,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        Text(caption, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun Timeline(milestones: List<Pair<String, Long>>, now: Long, accent: Color, isPreview: Boolean) {
    val context = LocalContext.current
    // The next milestone to reach is highlighted; passed ones recede.
    val next = if (isPreview) 0 else milestones.indexOfFirst { it.second > now }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(24.dp))
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        milestones.forEachIndexed { i, (label, at) ->
            if (i > 0) Box(Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (isPreview && i == 0) "Now" else clockTime(context, at),
                    style = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, fontFeatureSettings = "tnum"),
                    color = when {
                        i == next -> accent
                        next == -1 || i < next -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun localTime(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()

private fun clock(d: Duration) = d.toComponents { h, m, s, _ -> "%d:%02d:%02d".format(h, m, s) }

private fun hoursMinutes(d: Duration) = d.toComponents { h, m, _, _ -> if (h == 0L) "$m m" else "$h h %02d m".format(m) }

private val ActionText = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
private val ActionButton = Modifier.fillMaxWidth().height(60.dp)
