package com.emrobinso.floatpic.feature.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.input.pointer.PointerEvent
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

/** Reference output width; all transforms are stored in this coordinate space. */
private const val REF_W = 1080f

/**
 * The visible aspect window. Background + image go through [EditorRenderer] — the same
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
    onTapOutside: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (bitmap == null) {
        Box(modifier)
        return
    }

    val aspect = EditorRenderer.aspectRatio(state, bitmap.width, bitmap.height, 0)
    var viewportWidth by remember { mutableStateOf(1f) }
    val stateRef by rememberUpdatedState(state)
    val hapticsRef by rememberUpdatedState(haptics)
    val previewRef by rememberUpdatedState(onPreview)
    val commitRef by rememberUpdatedState(onCommit)
    val aspectRef by rememberUpdatedState(aspect)

    fun outHeight(): Float {
        val a = aspectRef
        return if (a <= 0f) REF_W else REF_W / a
    }

    fun clampOffset(t: Transform): Transform {
        val outH = outHeight()
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

    // The PARENT (a square container, owned by EditorScreen) only gives us a box; we size
    // the *preview element* to the requested aspect ratio and centre it. That is how the
    // window follows the image ratio (ORIGINAL) or the picked preset while the container
    // stays square. `Modifier.fillMaxSize().aspectRatio()` cannot do this: with fixed
    // constraints the ratio is silently ignored and you always get a 1:1 window.
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val a = if (aspect <= 0f) 1f else aspect
        val window = when {
            maxWidth <= 0.dp || maxHeight <= 0.dp -> 1.dp to 1.dp
            maxWidth / maxHeight > a -> (maxHeight * a) to maxHeight
            else -> maxWidth to (maxWidth / a)
        }
        Box(
            modifier = Modifier
                .size(window.first, window.second)
                .onSizeChanged { if (it.width > 1) viewportWidth = it.width.toFloat() }
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0B0B0D))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val start = stateRef.transform
                        var accScale = 1f
                        var accRot = 0f
                        var accPan = Offset.Zero
                        var moved = false
                        var limitNotified = false

                        while (true) {
                            val event: PointerEvent = awaitPointerEvent()
                            if (event.changes.isEmpty()) break
                            if (event.changes.none { it.pressed }) break

                            val zoom = event.calculateZoom()
                            val rot = event.calculateRotation()
                            val pan = event.calculatePan()
                            val isGesture = zoom != 1f || rot != 0f ||
                                abs(pan.x) + abs(pan.y) > 0.01f
                            if (isGesture) {
                                moved = true
                                accScale = (accScale * zoom).coerceIn(0.05f, 40f)
                                accRot += rot
                                accPan += pan

                                val factor = REF_W / viewportWidth
                                val desiredScale = start.scale * accScale
                                val clampedScale = desiredScale.coerceIn(1f, 10f)
                                val candidate = start.copy(
                                    scale = clampedScale,
                                    rotation = start.rotation + accRot,
                                    offsetX = start.offsetX + accPan.x * factor,
                                    offsetY = start.offsetY + accPan.y * factor,
                                )
                                val clamped = clampOffset(candidate)
                                val hitLimit = abs(clamped.offsetX - candidate.offsetX) > 1f ||
                                    abs(clamped.offsetY - candidate.offsetY) > 1f ||
                                    clampedScale != desiredScale
                                if (hitLimit && !limitNotified) {
                                    limitNotified = true
                                    hapticsRef.tick()
                                } else if (!hitLimit) {
                                    limitNotified = false
                                }
                                previewRef(clamped)
                            }
                        }

                        if (moved) {
                            val latest = stateRef.transform
                            val snapped = TextPainter.snapRotation(latest.rotation)
                            if (abs(snapped - latest.rotation) > 0.01f) hapticsRef.snap()
                            commitRef(clampOffset(latest.copy(rotation = snapped)))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        // tap on the preview closes the open panel (margin taps are
                        // handled by the square container in EditorScreen).
                        onTap = { onTapOutside() },
                        onDoubleTap = {
                            val t = stateRef.transform
                            val next = if (t.scale > 1.6f) 1f else 2f
                            hapticsRef.snap()
                            commitRef(clampOffset(t.copy(scale = next)))
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
