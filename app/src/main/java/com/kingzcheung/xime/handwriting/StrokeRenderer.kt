package com.kingzcheung.xime.handwriting

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** A pen, not a variable-radius marker. Width is density independent and bounded on tablets. */
fun DrawScope.renderStrokes(
    strokes: List<List<StrokePoint>>,
    currentStroke: List<StrokePoint>,
    color: Color,
    transparentPaper: Boolean = false,
) {
    val width = 3.5.dp.toPx()
    strokes.forEach { drawPenStroke(it, color, width, transparentPaper) }
    drawPenStroke(currentStroke, color, width, transparentPaper)
}

private fun DrawScope.drawPenStroke(points: List<StrokePoint>, color: Color, width: Float, transparentPaper: Boolean) {
    if (points.isEmpty()) return
    val first = Offset(points.first().x, points.first().y)
    if (points.size == 1) {
        drawCircle(color.copy(alpha = color.alpha * .72f), radius = width / 2, center = first)
        return
    }
    val path = Path().apply {
        moveTo(first.x, first.y)
        for (i in 1 until points.lastIndex) {
            val point = points[i]
            val next = points[i + 1]
            quadraticBezierTo(point.x, point.y, (point.x + next.x) / 2, (point.y + next.y) / 2)
        }
        lineTo(points.last().x, points.last().y)
    }
    val bounds = path.getBounds()
    // A bounding-box direction also works for closed loops where start == end.
    val start = bounds.topLeft
    val end = bounds.bottomRight.let { if ((it - start).getDistance() < 1f) start + Offset(1f, 1f) else it }
    if (transparentPaper) {
        // Only unknown host/image backgrounds get a narrow translucent separation layer.
        // Solid keyboard paper has no rim, black outline, highlight or glow.
        val separation = if (color.luminance() > .35f) Color.Black else Color.White
        drawPath(path, separation.copy(alpha = color.alpha * .55f),
            style = Stroke(width + 1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    // A low-alpha shoulder gives the edge a soft transition without a hard contour.
    // Compose Canvas uses anti-aliased Paint; render at the device density, never into a tiny bitmap.
    drawPath(path, color.copy(alpha = color.alpha * .14f),
        style = Stroke(width + 1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    val brush = Brush.linearGradient(
        0f to color.copy(alpha = color.alpha * .55f),
        .22f to color.copy(alpha = color.alpha * .92f),
        .72f to color.copy(alpha = color.alpha * .96f),
        1f to color.copy(alpha = color.alpha * .65f),
        start = start, end = end,
    )
    drawPath(path, brush, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    // A restrained inner tint separates the pen face from its shoulder, without a white rim.
    val center = androidx.compose.ui.graphics.lerp(color, Color.White, .16f)
    drawPath(path, center.copy(alpha = color.alpha * .16f),
        style = Stroke(width * .38f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
