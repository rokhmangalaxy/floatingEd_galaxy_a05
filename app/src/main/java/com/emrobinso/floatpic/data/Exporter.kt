package com.emrobinso.floatpic.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.emrobinso.floatpic.core.model.EditorState
import com.emrobinso.floatpic.core.render.EditorRenderer
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Exporter {

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** Renders the state to a NEW bitmap at real export resolution. */
    fun render(context: Context, source: Bitmap, state: EditorState, orientation: Int): Bitmap {
        val size = EditorRenderer.outputSize(state, source.width, source.height, orientation)
        val out = Bitmap.createBitmap(size.w, size.h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        EditorRenderer.drawInto(canvas, state, source, size.w, size.h)
        return out
    }

    /** Save to the public gallery (scoped storage friendly), or app files as fallback. */
    fun saveToGallery(context: Context, bitmap: Bitmap, png: Boolean): Uri {
        val name = "FloatPic_${stamp()}.${if (png) "png" else "jpg"}"
        val mime = if (png) "image/png" else "image/jpeg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/FloatPic")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore insert failed")
        resolver.openOutputStream(uri)?.use { stream ->
            compress(bitmap, stream, png)
        } ?: throw IllegalStateException("cannot open $uri")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    /** Share copy in cache (never pollutes the gallery, docs/03). */
    fun writeShareCopy(context: Context, bitmap: Bitmap, png: Boolean = false): Uri {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "FloatPic_share_${stamp()}.${if (png) "png" else "jpg"}")
        FileOutputStream(file).use { compress(bitmap, it, png) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun writeThumb(context: Context, bitmap: Bitmap): Uri {
        val dir = File(context.cacheDir, "session").apply { mkdirs() }
        val file = File(dir, "thumb_${stamp()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun compress(bitmap: Bitmap, stream: OutputStream, png: Boolean) {
        if (png) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        } else {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        }
    }

    fun copyToPrivateStorage(context: Context, uri: Uri, name: String = "source"): File {
        val dir = File(context.cacheDir, "session").apply { mkdirs() }
        val out = File(dir, "$name.bin")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("cannot read $uri")
        return out
    }
}
