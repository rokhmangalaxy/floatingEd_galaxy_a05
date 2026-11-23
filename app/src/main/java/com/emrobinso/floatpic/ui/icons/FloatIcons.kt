package com.emrobinso.floatpic.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn 24dp icons (no vector assets), each stroke uses the passed [tint]
 * so a single code path works for both themes.
 */
object FloatIcons {

    private const val VIEW = 24f

    private fun DrawScope.line(
        x1: Float, y1: Float, x2: Float, y2: Float, s: Float,
        color: Color, w: Float = 1.8f,
    ) = drawLine(color, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), w * s, StrokeCap.Round)

    private fun DrawScope.roundRectStroke(
        x: Float, y: Float, wF: Float, hF: Float, r: Float, s: Float, color: Color, sw: Float = 1.8f,
    ) = drawRoundRect(
        color, Offset(x * s, y * s), Size(wF * s, hF * s),
        CornerRadius(r * s), style = Stroke(width = sw * s),
    )

    @Composable
    fun Crop(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            line(6f, 3f, 6f, 18f, s, c)
            line(3f, 6f, 18f, 6f, s, c)
            line(18f, 6f, 18f, 21f, s, c)
            line(6f, 18f, 21f, 18f, s, c)
        }
    }

    /** Relative position: arrows pushing the content inside its frame. */
    @Composable
    fun Position(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            // frame
            roundRectStroke(3f, 3f, 18f, 18f, 3.5f, s, c.copy(alpha = 0.45f), 1.5f)
            // image block, offset toward top-left
            drawRoundRect(
                color = c,
                topLeft = Offset(6.4f * s, 6.4f * s),
                size = Size(6.6f * s, 6.6f * s),
                cornerRadius = CornerRadius(1.6f * s),
            )
            // two nudge arrows (right + down) showing relative movement
            line(13.6f, 9.7f, 18.4f, 9.7f, s, c, 1.6f)
            line(18.4f, 9.7f, 16.6f, 7.9f, s, c, 1.6f)
            line(18.4f, 9.7f, 16.6f, 11.5f, s, c, 1.6f)
            line(9.7f, 13.6f, 9.7f, 18.4f, s, c, 1.6f)
            line(9.7f, 18.4f, 7.9f, 16.6f, s, c, 1.6f)
            line(9.7f, 18.4f, 11.5f, 16.6f, s, c, 1.6f)
        }
    }

    /** Pick an image from the gallery. */
    @Composable
    fun Gallery(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            roundRectStroke(3f, 4.5f, 18f, 15f, 3f, s, c, 1.7f)
            drawCircle(c, radius = 1.8f * s, center = Offset(8f * s, 9.5f * s))
            val p = Path().apply {
                moveTo(4.4f * s, 17.4f * s)
                lineTo(9.8f * s, 12.2f * s)
                lineTo(13.4f * s, 15.6f * s)
                lineTo(15.6f * s, 13.4f * s)
                lineTo(19.6f * s, 17.4f * s)
                close()
            }
            drawPath(p, c)
        }
    }

    @Composable
    fun Background(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            roundRectStroke(3f, 3f, 18f, 18f, 4f, s, c)
            drawCircle(c, radius = 3.4f * s, center = Offset(8.5f * s, 8.5f * s))
            val p = Path().apply {
                moveTo(4.5f * s, 17.5f * s)
                lineTo(10.5f * s, 12f * s)
                lineTo(14.5f * s, 16f * s)
                lineTo(16.5f * s, 14f * s)
                lineTo(19.5f * s, 17.5f * s)
                close()
            }
            drawPath(p, c)
        }
    }

    @Composable
    fun TextIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            line(5f, 5f, 19f, 5f, s, c, 2.2f)
            line(12f, 5f, 12f, 19f, s, c, 2.2f)
            line(8.5f, 19f, 15.5f, 19f, s, c, 2.2f)
        }
    }

    @Composable
    fun Rotate(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            drawArc(
                color = c, startAngle = 40f, sweepAngle = 260f, useCenter = false,
                topLeft = Offset(4f * s, 4f * s), size = Size(16f * s, 16f * s),
                style = Stroke(width = 2f * s, cap = StrokeCap.Round),
            )
            line(15.2f, 2.6f, 19.2f, 5.6f, s, c, 2f)
            line(19.2f, 5.6f, 16.2f, 9.4f, s, c, 2f)
        }
    }

    @Composable
    fun MirrorH(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            val p1 = Path().apply {
                moveTo(10f * s, 4f * s); lineTo(10f * s, 20f * s); lineTo(2.5f * s, 20f * s); close()
            }
            drawPath(p1, c)
            val p2 = Path().apply {
                moveTo(14f * s, 4f * s); lineTo(14f * s, 20f * s); lineTo(21.5f * s, 20f * s); close()
            }
            drawPath(p2, c.copy(alpha = 0.35f))
            drawLine(c, Offset(12f * s, 2f * s), Offset(12f * s, 22f * s), 1.2f * s,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(2f * s, 2f * s),
                ),
            )
        }
    }

    @Composable
    fun MirrorV(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            val p1 = Path().apply {
                moveTo(4f * s, 10f * s); lineTo(20f * s, 10f * s); lineTo(20f * s, 2.5f * s); close()
            }
            drawPath(p1, c)
            val p2 = Path().apply {
                moveTo(4f * s, 14f * s); lineTo(20f * s, 14f * s); lineTo(20f * s, 21.5f * s); close()
            }
            drawPath(p2, c.copy(alpha = 0.35f))
            drawLine(c, Offset(2f * s, 12f * s), Offset(22f * s, 12f * s), 1.2f * s,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(2f * s, 2f * s),
                ),
            )
        }
    }

    @Composable
    fun Undo(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            drawArc(c, 160f, 230f, false, Offset(4f * s, 5f * s), Size(16f * s, 16f * s),
                style = Stroke(1.9f * s, cap = StrokeCap.Round))
            line(4.6f, 8.6f, 4.6f, 3.6f, s, c, 1.9f)
            line(4.6f, 3.6f, 9.2f, 5.6f, s, c, 1.9f)
        }
    }

    @Composable
    fun Redo(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            drawArc(c, 150f, -230f, false, Offset(4f * s, 5f * s), Size(16f * s, 16f * s),
                style = Stroke(1.9f * s, cap = StrokeCap.Round))
            line(19.4f, 8.6f, 19.4f, 3.6f, s, c, 1.9f)
            line(19.4f, 3.6f, 14.8f, 5.6f, s, c, 1.9f)
        }
    }

    @Composable
    fun Close(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            line(6f, 6f, 18f, 18f, s, c, 2f)
            line(18f, 6f, 6f, 18f, s, c, 2f)
        }
    }

    /** Aspect ratio: a frame with a ratio glyph inside. */
    @Composable
    fun Ratio(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            roundRectStroke(3.2f, 5.2f, 17.6f, 13.6f, 3f, s, c, 1.7f)
            // a smaller 4:3 window inside, conveying "ratio choice"
            drawRoundRect(
                color = c.copy(alpha = 0.45f),
                topLeft = Offset(6.6f * s, 8.4f * s),
                size = Size(10.8f * s, 7.2f * s),
                cornerRadius = CornerRadius(1.4f * s),
                style = Stroke(width = 1.3f * s),
            )
            line(6.6f, 12f, 17.4f, 12f, s, c.copy(alpha = 0.45f), 1.1f)
        }
    }

    @Composable
    fun Sun(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            drawCircle(c, 4.2f * s, Offset(12f * s, 12f * s), style = Stroke(1.7f * s))
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val dx = kotlin.math.cos(a).toFloat()
                val dy = kotlin.math.sin(a).toFloat()
                line(12f + dx * 6.6f, 12f + dy * 6.6f, 12f + dx * 9f, 12f + dy * 9f, s, c, 1.6f)
            }
        }
    }

    @Composable
    fun Moon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            val outer = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(4f * s, 3.6f * s, 18f * s, 17.6f * s))
            }
            val cut = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(9.4f * s, 1.4f * s, 22.6f * s, 14.6f * s))
            }
            val moon = Path().apply {
                op(outer, cut, androidx.compose.ui.graphics.PathOperation.Difference)
            }
            drawPath(moon, c)
        }
    }

    @Composable
    fun History(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            // clock face with a gap at the upper-left + rewind arrow across that gap
            drawArc(
                color = c, startAngle = -230f, sweepAngle = 300f, useCenter = false,
                topLeft = Offset(4.2f * s, 4.2f * s), size = Size(15.6f * s, 15.6f * s),
                style = Stroke(1.7f * s, cap = StrokeCap.Round),
            )
            line(11.6f, 8.4f, 11.6f, 12.4f, s, c, 1.7f)
            line(11.6f, 12.4f, 14.4f, 14f, s, c, 1.7f)
            // arrow head pointing into the clock at ~10 o'clock
            line(4.6f, 5.2f, 8.4f, 4.4f, s, c, 1.6f)
            line(4.6f, 5.2f, 5.2f, 9f, s, c, 1.6f)
        }
    }

    @Composable
    fun Palette(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            drawCircle(c, 8.5f * s, Offset(12f * s, 12f * s), style = Stroke(1.8f * s))
            drawCircle(c, 1.6f * s, Offset(8.5f * s, 8.5f * s))
            drawCircle(c, 1.6f * s, Offset(15f * s, 9.5f * s))
            drawCircle(c, 1.6f * s, Offset(15.8f * s, 15f * s))
        }
    }

    @Composable
    fun Minimize(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            line(5f, 9f, 12f, 16f, s, c, 2f)
            line(12f, 16f, 19f, 9f, s, c, 2f)
        }
    }

    @Composable
    fun Share(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            drawCircle(c, 2.4f * s, Offset(17.5f * s, 5.5f * s))
            drawCircle(c, 2.4f * s, Offset(6.5f * s, 12f * s))
            drawCircle(c, 2.4f * s, Offset(17.5f * s, 18.5f * s))
            line(8.6f, 10.9f, 15.4f, 6.6f, s, c, 1.7f)
            line(8.6f, 13.1f, 15.4f, 17.4f, s, c, 1.7f)
        }
    }

    @Composable
    fun Save(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            val p = Path().apply {
                moveTo(12f * s, 3.5f * s); lineTo(12f * s, 14f * s)
                moveTo(7.5f * s, 9.5f * s); lineTo(12f * s, 14f * s); lineTo(16.5f * s, 9.5f * s)
            }
            drawPath(p, c, style = Stroke(1.9f * s, cap = StrokeCap.Round))
            line(4f, 17f, 4f, 19f, s, c, 1.9f)
            line(4f, 19f, 20f, 19f, s, c, 1.9f)
            line(20f, 19f, 20f, 17f, s, c, 1.9f)
        }
    }

    @Composable
    fun Check(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            line(4.5f, 12.5f, 9.5f, 17.5f, s, c, 2.2f)
            line(9.5f, 17.5f, 19.5f, 6.5f, s, c, 2.2f)
        }
    }

    @Composable
    fun Grid(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
        val c = if (tint == Color.Unspecified) Color(0xFF2A2F3A) else tint
        Canvas(modifier.size(22.dp)) {
            val s = size.minDimension / VIEW
            roundRectStroke(3.5f, 3.5f, 17f, 17f, 2f, s, c, 1.5f)
            line(9.2f, 3.5f, 9.2f, 20.5f, s, c, 1.2f)
            line(14.8f, 3.5f, 14.8f, 20.5f, s, c, 1.2f)
            line(3.5f, 9.2f, 20.5f, 9.2f, s, c, 1.2f)
            line(3.5f, 14.8f, 20.5f, 14.8f, s, c, 1.2f)
        }
    }
}
