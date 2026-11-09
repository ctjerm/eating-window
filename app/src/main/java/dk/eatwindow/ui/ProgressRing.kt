package dk.eatwindow.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

/**
 * The eating window as a clock-like ring, filling clockwise from the top.
 * The final [lastCallShare] of the track is tinted so the last hour is visible before it arrives.
 */
@Composable
fun ProgressRing(progress: Float, color: Color, lastCallShare: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val lastCall = MaterialTheme.colorScheme.secondary
    val core = MaterialTheme.colorScheme.background
    val sweep by animateFloatAsState(progress, tween(900, easing = FastOutSlowInEasing), label = "sweep")
    val tint by animateColorAsState(color, tween(600), label = "tint")

    Canvas(modifier) {
        val stroke = size.minDimension * 0.052f
        // Leave room for the glow around the head dot.
        val radius = (size.minDimension - stroke * 2.6f) / 2
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)

        rotate(-90f) {
            drawCircle(track, radius, style = Stroke(stroke))
            drawArc(
                lastCall.copy(alpha = 0.22f), 360f * (1 - lastCallShare), 360f * lastCallShare,
                useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke),
            )
            if (sweep <= 0.0005f) return@rotate

            // Fades in from the start. Flat ends: the head dot covers the leading edge.
            val head = sweep.coerceIn(0.001f, 0.999f)
            val brush = Brush.sweepGradient(
                0f to tint.copy(alpha = 0.35f), head to tint, 1f to tint.copy(alpha = 0.35f), center = center,
            )
            drawArc(
                brush, 0f, 360f * head, useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(stroke),
            )

            val angle = Math.toRadians(360.0 * head)
            val dot = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
            drawCircle(tint.copy(alpha = 0.18f), stroke * 1.25f, dot)
            drawCircle(tint, stroke * 0.72f, dot)
            drawCircle(core, stroke * 0.26f, dot)
        }
    }
}
