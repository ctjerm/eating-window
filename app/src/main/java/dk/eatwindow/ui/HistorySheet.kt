package dk.eatwindow.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dk.eatwindow.EatingWindow
import dk.eatwindow.History
import dk.eatwindow.clockTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayStyle
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

private const val RECENT = 14

/** Streak, the past week and recent fasts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(fasts: List<History.Fast>, now: Long, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val colors = MaterialTheme.colorScheme
    val streak = History.streak(fasts)
    val best = History.best(fasts)
    // A fast counts for the day it reached 16 hours.
    val kept = fasts.filter { it.complete }.mapTo(HashSet()) { date(EatingWindow.fastDoneAt(it.start), zone) }
    val today = date(now, zone)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surfaceContainerLow) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp)) {
            item {
                Text("YOUR STREAK", style = Eyebrow, color = colors.onSurfaceVariant)
                Text(
                    fastCount(streak),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp),
                    color = colors.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    if (best > streak) "Best so far: ${fastCount(best)}" else if (streak > 0) "Your best yet. Keep it going." else "Fast for 16 hours to begin one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    (6 downTo 0).map { today.minusDays(it.toLong()) }.forEach { day ->
                        DayDot(day, done = day in kept, isToday = day == today)
                    }
                }
                Text(
                    "RECENT FASTS",
                    style = Eyebrow,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp, bottom = 8.dp),
                )
                if (fasts.isEmpty()) {
                    Text(
                        "Your fasts will show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            val recent = fasts.asReversed().take(RECENT)
            itemsIndexed(recent) { i, fast ->
                FastRow(fast, zone, first = i == 0, last = i == recent.lastIndex)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DayDot(day: LocalDate, done: Boolean, isToday: Boolean) {
    val colors = MaterialTheme.colorScheme
    val name = day.dayOfWeek.getDisplayName(DayStyle.FULL, Locale.getDefault())
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$name: ${if (done) "fast completed" else "no fast completed"}" },
    ) {
        Text(
            day.dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.getDefault()),
            style = Eyebrow,
            color = if (isToday) colors.onSurface else colors.onSurfaceVariant,
        )
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(if (done) colors.primary else colors.surfaceContainerHighest)
                .then(if (isToday && !done) Modifier.border(BorderStroke(1.5.dp, colors.primary), CircleShape) else Modifier),
        )
    }
}

@Composable
private fun FastRow(fast: History.Fast, zone: ZoneId, first: Boolean, last: Boolean) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val corner = 20.dp
    val shape = RoundedCornerShape(
        topStart = if (first) corner else 0.dp, topEnd = if (first) corner else 0.dp,
        bottomStart = if (last) corner else 0.dp, bottomEnd = if (last) corner else 0.dp,
    )
    Column(Modifier.fillMaxWidth().background(colors.surfaceContainer, shape)) {
        if (!first) Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(colors.outlineVariant))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                DateTimeFormatter.ofPattern("EEE d MMM").format(Instant.ofEpochMilli(fast.end).atZone(zone)),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${clockTime(context, fast.start)} – ${if (fast.ongoing) "now" else clockTime(context, fast.end)}",
                style = Numerals,
                color = colors.onSurfaceVariant,
            )
            Text(
                (fast.end - fast.start).milliseconds.toComponents { h, m, _, _ -> "%dh %02dm".format(h, m) },
                style = Numerals.copy(fontWeight = FontWeight.SemiBold),
                // Short fasts, the ones that broke a streak, stand out.
                color = when {
                    fast.ongoing -> colors.primary
                    fast.complete -> colors.onSurface
                    else -> colors.secondary
                },
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

private fun date(millis: Long, zone: ZoneId) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

private fun fastCount(n: Int) = if (n == 1) "1 fast" else "$n fasts"

private val Numerals = TextStyle(fontFamily = Sans, fontSize = 15.sp, fontFeatureSettings = "tnum")
