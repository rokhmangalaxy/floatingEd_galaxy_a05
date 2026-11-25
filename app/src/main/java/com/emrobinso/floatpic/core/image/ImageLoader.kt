package com.emrobinso.floatpic.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * [bitmap] is already upright (EXIF applied) and downsampled.
 * [cacheFile] is the local copy used as the re-editable source.
 */
data class LoadedImage(
    val bitmap: Bitmap,
    val orientation: Int = 0,
    val cacheFile: File? = null,
)

object ImageLoader {

    private const val IMPORT_DIR = "import"

    /**
     * Share URIs from chat apps are often one-shot: reading the stream more than once
     * (bounds + decode + EXIF) throws `cannot open content://...`. So we COPY ONCE into
     * the cache and decode only from that local file.
     */
    suspend fun load(context: Context, uri: Uri, targetMax: Int = 2048): LoadedImage =
        withContext(Dispatchers.IO) {
            val copy = copyOnce(context, uri)
            try {
                decodeFile(copy, targetMax)
            } catch (t: Throwable) {
                copy.delete()
                throw if (t is IOException) t else IOException("cannot open $uri", t)
            }
        }

    suspend fun loadFile(context: Context, file: File, targetMax: Int = 2048): LoadedImage =
        withContext(Dispatchers.IO) { decodeFile(file, targetMax) }

    /** Small thumbnail for the bubble / history list. */
    suspend fun loadThumb(file: File, targetMax: Int = 256): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            FileInputStream(file).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetMax &&
                bounds.outHeight / (sample * 2) >= targetMax
            ) {
                sample *= 2
            }
            FileInputStream(file).use {
                BitmapFactory.decodeStream(
                    it, null,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                )
            }
        }.getOrNull()
    }

    // -- internals ---------------------------------------------------------------------

    private fun copyOnce(context: Context, uri: Uri): File {
        val dir = File(context.cacheDir, IMPORT_DIR).apply { mkdirs() }
        pruneOldFiles(dir)
        val target = File.createTempFile("src_", ".bin", dir)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("cannot open $uri")
        input.use { source ->
            target.outputStream().use { sink -> source.copyTo(sink) }
        }
        if (target.length() <= 0L) {
            target.delete()
            throw IOException("empty stream $uri")
        }
        return target
    }

    private fun pruneOldFiles(dir: File) {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        runCatching {
            dir.listFiles()?.forEach { f ->
                if (f.lastModified() < cutoff) f.delete()
            }
        }
    }

    private fun decodeFile(file: File, targetMax: Int): LoadedImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        FileInputStream(file).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("unsupported image")
        }

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetMax &&
            bounds.outHeight / (sample * 2) >= targetMax
        ) {
            sample *= 2
        }
        var bitmap = FileInputStream(file).use {
            BitmapFactory.decodeStream(
                it, null,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        } ?: throw IOException("decode failed")

        val exif = runCatching {
            FileInputStream(file).use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

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
            val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (upright !== bitmap) bitmap.recycle()
            bitmap = upright
        }

        return LoadedImage(bitmap, rotation, file)
    }
}
