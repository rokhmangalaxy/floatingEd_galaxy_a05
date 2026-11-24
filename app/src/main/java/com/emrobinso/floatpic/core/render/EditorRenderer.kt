package com.emrobinso.floatpic.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.emrobinso.floatpic.core.model.AspectPreset
import com.emrobinso.floatpic.core.model.BackgroundKind
import com.emrobinso.floatpic.core.model.EditorState
import com.emrobinso.floatpic.core.model.ScaleMode
import com.emrobinso.floatpic.core.model.TextAlign
import com.emrobinso.floatpic.core.model.TextOverlay
import com.emrobinso.floatpic.core.model.TextPosition
import com.emrobinso.floatpic.core.model.TextStylePreset
import com.emrobinso.floatpic.core.model.Transform
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * One renderer for preview AND export (see docs/04). Everything is drawn in output
 * coordinates; the caller only chooses the target size.
 */
object EditorRenderer {

    /** Resolved output size for a state + source bitmap (keeps `ORIGINAL` honest). */
    fun outputSize(state: EditorState, srcW: Int, srcH: Int, orientation: Int): IntPair {
        val swapped = orientation == 90 || orientation == 270
        val w = if (swapped) srcH else srcW
        val h = if (swapped) srcW else srcH
        val preset = state.aspect
        if (preset == AspectPreset.ORIGINAL) {
            val s = if (w >= h) 1920f / max(w, 1) else 1920f / max(h, 1)
            val sc = min(s, 1f)
            return IntPair(max(1, (w * sc).toInt()), max(1, (h * sc).toInt()))
        }
        return IntPair(preset.outputWidth, preset.outputHeight)
    }

    fun aspectRatio(state: EditorState, srcW: Int, srcH: Int, orientation: Int): Float {
        val size = outputSize(state, srcW, srcH, orientation)
        return size.w.toFloat() / size.h.toFloat()
    }

    /** "scale" inside Transform is relative to the natural fit-scale (see baseScale). */
    fun baseScale(
        state: EditorState,
        outW: Float,
        outH: Float,
        bmpW: Int,
        bmpH: Int,
    ): Float {
        val inset = 2f * state.paddingDp * (outW / 1080f)
        val availW = max(1f, outW - inset)
        val availH = max(1f, outH - inset)
        val fit = min(availW / bmpW, availH / bmpH)
        val cover = max(availW / bmpW, availH / bmpH)
        return if (state.scaleMode == ScaleMode.FILL) cover else fit
    }

    fun drawInto(
        canvas: Canvas,
        state: EditorState,
        bitmap: Bitmap,
        outW: Int,
        outH: Int,
        allowBlur: Boolean = true,
    ) {
        val w = outW.toFloat()
        val h = outH.toFloat()

        // background
        when (state.background.kind) {
            BackgroundKind.COLOR -> canvas.drawColor(state.background.color.toInt())
            BackgroundKind.GRADIENT -> {
                val a = state.background.color.toInt()
                val b = state.background.color2.toInt()
                val rad = Math.toRadians(state.background.angle.toDouble())
                val dx = kotlin.math.cos(rad).toFloat() * w
                val dy = kotlin.math.sin(rad).toFloat() * h
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        (w - dx) / 2f, (h - dy) / 2f, (w + dx) / 2f, (h + dy) / 2f,
                        intArrayOf(a, b), null, Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawRect(0f, 0f, w, h, paint)
            }
            BackgroundKind.BLUR -> {
                canvas.drawColor(Color.BLACK)
                val backdrop = if (allowBlur) BlurCache.get(bitmap) else null
                if (backdrop != null) {
                    drawCover(canvas, backdrop, w, h)
                    // gentle vignette so the subject still pops
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(
                            w / 2f, h / 2f, max(w, h) * 0.78f,
                            intArrayOf(Color.argb(20, 0, 0, 0), Color.argb(150, 0, 0, 0)),
                            null, Shader.TileMode.CLAMP,
                        )
                    }
                    canvas.drawRect(0f, 0f, w, h, paint)
                } else {
                    canvas.drawColor(state.background.color.toInt())
                }
            }
            BackgroundKind.PATTERN -> {
                canvas.drawColor(state.background.color.toInt())
                drawPattern(canvas, state.background.patternId, state.background.color2.toInt(), w, h)
            }
        }

        // geometry
        val pad = state.paddingDp * (w / 1080f)
        val inset = RectF(pad, pad, w - pad, h - pad)
        val bx = (inset.left + inset.right) / 2f
        val by = (inset.top + inset.bottom) / 2f
        val base = baseScale(state, w, h, bitmap.width, bitmap.height)
        val t: Transform = state.transform
        val s = base * t.scale
        val corner = state.cornerDp * (w / 1080f)

        canvas.save()
        if (corner > 0f) {
            val path = Path().apply {
                addRoundRect(inset, corner, corner, Path.Direction.CW)
            }
            canvas.clipPath(path)
        }

        canvas.translate(bx + t.offsetX * (w / 1080f), by + t.offsetY * (w / 1080f))
        canvas.rotate(t.rotation)
        canvas.scale(
            (if (t.flipH) -s else s),
            (if (t.flipV) -s else s),
        )
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        if (state.shadow) {
            paint.setShadowLayer(corner.coerceAtLeast(6f) * 0.9f, 0f, corner.coerceAtLeast(6f) * 0.4f,
                Color.argb(90, 0, 0, 0))
        }
        canvas.drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, paint)
        canvas.restore()

        // text overlay (never rotated with the image; it sticks to the frame)
        if (state.text.isVisible) {
            TextPainter.draw(canvas, state.text, w, h)
        }
    }

    /** cover-fit draw of an already sized bitmap */
    private fun drawCover(canvas: Canvas, bmp: Bitmap, w: Float, h: Float) {
        val cover = max(w / bmp.width, h / bmp.height)
        canvas.drawBitmap(
            bmp, null,
            RectF((w - bmp.width * cover) / 2f, (h - bmp.height * cover) / 2f,
                (w + bmp.width * cover) / 2f, (h + bmp.height * cover) / 2f),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
    }

    /**
     * Pre-blurred, downscaled backdrop. Rendering it once (not per frame) keeps preview
     * and export pixel-identical and avoids RenderEffect (API 31 only) — see docs/05.
     */
    private object BlurCache {
        private var key: Bitmap? = null
        private var value: Bitmap? = null

        fun get(source: Bitmap): Bitmap? = synchronized(this) {
            if (key === source && value != null) return value
            val small = Bitmap.createScaledBitmap(source, 96, 96, true)
            val blurred = stackBlur(small, 18)
            if (small !== source) small.recycle()
            key = source
            value = blurred
            blurred
        }

        /** Two-pass box blur (fast, dependency free). */
        private fun stackBlur(src: Bitmap, radius: Int): Bitmap {
            val w = src.width
            val h = src.height
            val pixels = IntArray(w * h)
            src.getPixels(pixels, 0, w, 0, 0, w, h)
            val tmp = IntArray(w * h)
            boxPass(pixels, tmp, w, h, radius, horizontal = true)
            boxPass(tmp, pixels, w, h, radius, horizontal = false)
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            out.setPixels(pixels, 0, w, 0, 0, w, h)
            return out
        }

        private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
            val len = if (horizontal) w else h
            val lines = if (horizontal) h else w
            for (line in 0 until lines) {
                var sumR = 0; var sumG = 0; var sumB = 0; var sumA = 0; var count = 0
                for (i in -r..r) {
                    val idx = i.coerceIn(0, len - 1)
                    val p = src[index(idx, line, w, horizontal)]
                    sumA += (p ushr 24) and 0xFF
                    sumR += (p shr 16) and 0xFF
                    sumG += (p shr 8) and 0xFF
                    sumB += p and 0xFF
                    count++
                }
                for (i in 0 until len) {
                    dst[index(i, line, w, horizontal)] =
                        ((sumA / count) shl 24) or
                            ((sumR / count) shl 16) or
                            ((sumG / count) shl 8) or
                            (sumB / count)
                    val outIdx = (i - r).coerceIn(0, len - 1)
                    val inIdx = (i + r + 1).coerceIn(0, len - 1)
                    val pOut = src[index(outIdx, line, w, horizontal)]
                    val pIn = src[index(inIdx, line, w, horizontal)]
                    sumA += ((pIn ushr 24) and 0xFF) - ((pOut ushr 24) and 0xFF)
                    sumR += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
                    sumG += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
                    sumB += (pIn and 0xFF) - (pOut and 0xFF)
                }
            }
        }

        private fun index(i: Int, line: Int, w: Int, horizontal: Boolean): Int =
            if (horizontal) line * w + i else i * w + line
    }

    private fun drawPattern(canvas: Canvas, id: String, color: Int, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = w * 0.004f
        }
        val step = w / 14f
        when (id) {
            "dots" -> {
                paint.style = Paint.Style.FILL
                var y = step / 2
                while (y < h) {
                    var x = step / 2
                    while (x < w) {
                        canvas.drawCircle(x, y, w * 0.004f, paint)
                        x += step
                    }
                    y += step
                }
            }
            "diagonal" -> {
                var x = -h
                while (x < w) {
                    canvas.drawLine(x, 0f, x + h, h, paint)
                    x += step
                }
            }
            "grid" -> {
                var x = 0f
                while (x <= w) {
                    canvas.drawLine(x, 0f, x, h, paint)
                    x += step
                }
                var y = 0f
                while (y <= h) {
                    canvas.drawLine(0f, y, w, y, paint)
                    y += step
                }
            }
        }
    }

    /** Cheap, deterministic "ambient" background: blurred gradient sampled from the image. */
    fun ambientColors(bitmap: Bitmap): Triple<Int, Int, Int> {
        val small = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        var darkR = 0; var darkG = 0; var darkB = 0; var darkest = Int.MAX_VALUE
        for (y in 0 until 32) for (x in 0 until 32) {
            val c = small.getPixel(x, y)
            val pr = (c shr 16) and 0xFF
            val pg = (c shr 8) and 0xFF
            val pb = c and 0xFF
            r += pr; g += pg; b += pb; n++
            val lum = (pr * 299 + pg * 587 + pb * 114) / 1000
            if (lum < darkest) { darkest = lum; darkR = pr; darkG = pg; darkB = pb }
        }
        if (small !== bitmap) small.recycle()
        val mr = (r / n).toInt(); val mg = (g / n).toInt(); val mb = (b / n).toInt()
        fun darken(v: Int) = (v * 0.35f + 12).toInt().coerceIn(0, 255)
        fun lighten(v: Int) = (v + (255 - v) * 0.25f).toInt().coerceIn(0, 255)
        val c1 = Color.rgb(lighten(mr), lighten(mg), lighten(mb))
        val c2 = Color.rgb(darken(darkR), darken(darkG), darken(darkB))
        val mid = Color.rgb((mr + darkR) / 2, (mg + darkG) / 2, (mb + darkB) / 2)
        return Triple(c1, mid, c2)
    }

    /** Thumbnail used by the bubble. */
    fun bubbleThumb(bitmap: Bitmap, sizePx: Int = 160): Bitmap =
        Bitmap.createScaledBitmap(bitmap, sizePx, sizePx, true)
}

data class IntPair(val w: Int, val h: Int)

/** Text with outline: stroke first, fill on top (docs/12). */
object TextPainter {
    fun draw(canvas: Canvas, overlay: TextOverlay, w: Float, h: Float) {
        if (!overlay.isVisible) return
        val margin = w * 0.06f
        val maxWidth = max(1, (w - margin * 2).toInt())
        var sizePx = w * overlay.sizeFraction
        val strokeFrac = if (overlay.style == TextStylePreset.WHITE_ON_BLACK) 0.10f else 0.12f
        val fillColor = if (overlay.style == TextStylePreset.WHITE_ON_BLACK) Color.WHITE else Color.BLACK
        val strokeColor = if (overlay.style == TextStylePreset.WHITE_ON_BLACK) Color.BLACK else Color.WHITE

        var result: Layouts? = null
        var guard = 0
        while (guard++ < 24) {
            val l = build(overlay.text, maxWidth, sizePx, fillColor, strokeColor, strokeFrac, overlay.align)
            result = l
            if (l.height <= h * 0.3f || sizePx < w * 0.028f) break
            sizePx *= 0.94f
        }
        val layouts = result ?: return
        val x = when (overlay.align) {
            TextAlign.LEFT -> margin
            TextAlign.CENTER -> (w - layouts.width) / 2f
            TextAlign.RIGHT -> w - layouts.width - margin
        }
        val y = when (overlay.position) {
            TextPosition.TOP_CENTER -> h * 0.05f
            TextPosition.BOTTOM_CENTER -> h - layouts.height - h * 0.06f
        }
        canvas.save()
        canvas.translate(x, y)
        layouts.stroke.draw(canvas)
        layouts.fill.draw(canvas)
        canvas.restore()
    }

    private class Layouts(val stroke: android.text.StaticLayout, val fill: android.text.StaticLayout) {
        val width: Float get() = max(stroke.width, fill.width).toFloat()
        val height: Float get() = max(stroke.height, fill.height).toFloat()
    }

    private fun build(
        text: String,
        maxWidth: Int,
        size: Float,
        fill: Int,
        stroke: Int,
        strokeFrac: Float,
        align: TextAlign,
    ): Layouts {
        val typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        fun paint(color: Int, width: Float, style: Paint.Style) = android.text.TextPaint(
            Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG,
        ).apply {
            this.color = color
            textSize = size
            this.style = style
            strokeWidth = width
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            this.typeface = typeface
        }
        val textAlign = when (align) {
            TextAlign.LEFT -> android.text.Layout.Alignment.ALIGN_NORMAL
            TextAlign.CENTER -> android.text.Layout.Alignment.ALIGN_CENTER
            TextAlign.RIGHT -> android.text.Layout.Alignment.ALIGN_OPPOSITE
        }
        fun layout(p: android.text.TextPaint) = android.text.StaticLayout.Builder
            .obtain(text, 0, text.length, p, maxWidth)
            .setAlignment(textAlign)
            .setLineSpacing(0f, 1.05f)
            .setMaxLines(3)
            .setIncludePad(true)
            .build()

        val strokeLayout = layout(paint(stroke, size * strokeFrac, Paint.Style.STROKE))
        val fillLayout = layout(paint(fill, 0f, Paint.Style.FILL))
        return Layouts(strokeLayout, fillLayout)
    }

    fun snapRotation(deg: Float): Float {
        val norm = ((deg % 360f) + 360f) % 360f
        val nearest = (norm / 90f).toInt() * 90f
        return if (abs(norm - nearest) <= 3.5f) nearest else deg
    }
}
