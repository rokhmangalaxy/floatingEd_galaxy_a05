package com.emrobinso.floatpic.feature.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.emrobinso.floatpic.core.model.EditorState
import com.emrobinso.floatpic.core.model.Transform
import com.emrobinso.floatpic.core.render.EditorRenderer
import com.emrobinso.floatpic.core.render.TextPainter
import com.emrobinso.floatpic.ui.Haptics
import kotlin.math.abs
import kotlin.math.max

/** Preview width used as the reference "output" coordinate space (see docs/04). */
private const val REF_W = 1080f

/**
 * The visible aspect window. Background + image are rendered by [EditorRenderer], the same
 * renderer export uses, so preview == result.
 */
@Composable
fun EditorViewport(
    state: EditorState,
    bitmap: Bitmap?,
    haptics: Haptics,
    showGuides: Boolean,
    onPreview: (Transform) -> Unit,
    onCommit: (Transform) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (bitmap == null) {
        Box(modifier)
        return
    }

    val aspect = EditorRenderer.aspectRatio(state, bitmap.width, bitmap.height, 0)
    val outH = if (aspect <= 0f) REF_W else REF_W / aspect

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val stateRef by rememberUpdatedState(state)
    val hapticsRef by rememberUpdatedState(haptics)

    fun clampOffset(t: Transform): Transform {
        val base = EditorRenderer.baseScale(stateRef, REF_W, outH, bitmap.width, bitmap.height)
        val s = base * t.scale
        val imgW = bitmap.width * s
        val imgH = bitmap.height * s
        val pad = stateRef.paddingDp * (REF_W / 1080f)
        val availW = max(1f, REF_W - pad * 2)
        val availH = max(1f, outH - pad * 2)
        val limX = (imgW - availW) / 2f
        val limY = (imgH - availH) / 2f
        return t.copy(
            offsetX = if (limX <= 0f) 0f else t.offsetX.coerceIn(-limX, limX),
            offsetY = if (limY <= 0f) 0f else t.offsetY.coerceIn(-limY, limY),
        )
    }

    Box(
        modifier = modifier.fillMaxSize().onSizeChanged { viewportSize = it },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(if (aspect <= 0f) 1f else aspect)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0B0B0D))
                .pointerInput(bitmap) {
                    awaitEachGesture {
                        val start = stateRef.transform
                        var accScale = 1f
                        var accRot = 0f
                        var accPan = Offset.Zero
                        var moved = false
                        var limitHit = false
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } < 1) break
                            val zoom = event.calculateZoom()
                            val rot = event.calculateRotation()
                            val pan = event.calculatePan()
                            if (zoom != 1f || rot != 0f || abs(pan.x) + abs(pan.y) > 0.01f) {
                                moved = true
                                accScale = (accScale * zoom).coerceIn(0.2f, 20f)
                                accRot += rot
                                accPan += pan

                                val px = if (viewportSize.width > 1) viewportSize.width.toFloat() else 1f
                                val factor = REF_W / px
                                val rawScale = (start.scale * accScale).coerceIn(1f, 10f)
                                val raw = start.copy(
                                    scale = rawScale,
                                    rotation = start.rotation + accRot,
                                    offsetX = start.offsetX + accPan.x * factor,
                                    offsetY = start.offsetY + accPan.y * factor,
                                )
                                val clamped = clampOffset(raw)
                                if (!limitHit &&
                                    (abs(clamped.offsetX - raw.offsetX) > 1f ||
                                        abs(clamped.offsetY - raw.offsetY) > 1f)
                                ) {
                                    limitHit = true
                                    hapticsRef.tick()
                                }
                                if (rawScale != start.scale * accScale) {
                                    if (!limitHit) { limitHit = true; hapticsRef.tick() }
                                } else if (limitHit) {
                                    limitHit = false
                                }
                                onPreview(clamped)
                            }
                        } while (event.changes.any { it.pressed })

                        if (moved) {
                            val latest = stateRef.transform
                            val snapped = TextPainter.snapRotation(latest.rotation)
                            val delta = snapped - latest.rotation
                            if (abs(delta) > 0.01f) hapticsRef.snap()
                            onCommit(clampOffset(latest.copy(rotation = snapped)))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            val t = stateRef.transform
                            val next = if (t.scale > 1.6f) 1f else 2f
                            hapticsRef.snap()
                            onCommit(clampOffset(t.copy(scale = next)))
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (size.width < 1f || size.height < 1f) return@Canvas
                drawIntoCanvas { canvas ->
                    EditorRenderer.drawInto(
                        canvas = canvas.nativeCanvas,
                        state = state,
                        bitmap = bitmap,
                        outW = size.width.toInt(),
                        outH = size.height.toInt(),
                    )
                }
                if (showGuides) drawGuides()
            }
        }
    }
}

private fun DrawScope.drawGuides() {
    val c = Color.White.copy(alpha = 0.30f)
    val w = 1.dp.toPx()
    drawLine(c, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), w)
    drawLine(c, Offset(size.width * 2 / 3f, 0f), Offset(size.width * 2 / 3f, size.height), w)
    drawLine(c, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), w)
    drawLine(c, Offset(0f, size.height * 2 / 3f), Offset(size.width, size.height * 2 / 3f), w)
    drawCircle(Color.White.copy(alpha = 0.18f), radius = 3.dp.toPx(), center = center)
}
