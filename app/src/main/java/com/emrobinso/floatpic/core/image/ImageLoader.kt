package com.emrobinso.floatpic.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

data class LoadedImage(
    val bitmap: Bitmap,
    /** 0, 90, 180, 270 — rotation already applied to [bitmap]. */
    val orientation: Int,
)

object ImageLoader {

    /** Decode downsampled + upright. Never keep the raw stream around. */
    suspend fun load(context: Context, uri: Uri, targetMax: Int = 2048): LoadedImage =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: throw IOException("cannot open $uri")
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IOException("unsupported image $uri")
            }

            var sample = 1
            while (bounds.outWidth / sample > targetMax * 2 && bounds.outHeight / sample > targetMax * 2) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: throw IOException("decode failed $uri")

            val exifRotation = resolver.openInputStream(uri)?.use { stream ->
                runCatching {
                    when (ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
                    )) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }.getOrDefault(0)
            } ?: 0

            val extra = runCatching {
                resolver.openInputStream(uri)?.use { stream ->
                    when (ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
                    )) {
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> intArrayOf(-1, 0)
                        ExifInterface.ORIENTATION_FLIP_VERTICAL -> intArrayOf(0, -1)
                        else -> null
                    }
                }
            }.getOrNull()

            if (exifRotation != 0 || extra != null) {
                val m = Matrix().apply {
                    postRotate(exifRotation.toFloat())
                    when (extra) {
                        intArrayOf(-1, 0) -> postScale(-1f, 1f)
                        intArrayOf(0, -1) -> postScale(1f, -1f)
                    }
                }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            }

            LoadedImage(bitmap, exifRotation)
        }
}
