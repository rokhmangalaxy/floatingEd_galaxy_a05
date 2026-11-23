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
    /** EXIF rotation that was already applied to [bitmap] (0/90/180/270). */
    val orientation: Int,
)

object ImageLoader {

    /** Decode downsampled + upright, so the editor never touches a 50 MP bitmap. */
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
            while (bounds.outWidth / (sample * 2) >= targetMax && bounds.outHeight / (sample * 2) >= targetMax) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: throw IOException("decode failed $uri")

            val exif = resolver.openInputStream(uri)?.use { stream ->
                runCatching { ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
                ) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            val rotation = when (exif) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            val flipX = exif == ExifInterface.ORIENTATION_FLIP_HORIZONTAL
            val flipY = exif == ExifInterface.ORIENTATION_FLIP_VERTICAL

            if (rotation != 0 || flipX || flipY) {
                val matrix = Matrix().apply {
                    postRotate(rotation.toFloat())
                    postScale(if (flipX) -1f else 1f, if (flipY) -1f else 1f)
                }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            }

            LoadedImage(bitmap, rotation)
        }
}
