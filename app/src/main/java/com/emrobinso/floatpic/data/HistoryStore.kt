package com.emrobinso.floatpic.data

import android.content.Context
import android.graphics.Bitmap
import com.emrobinso.floatpic.core.model.EditorState
import com.emrobinso.floatpic.feature.editor.DraftSnapshot
import com.emrobinso.floatpic.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** One re-editable session: the source copy + the full editor state. */
@Serializable
data class HistoryEntry(
    val id: String,
    val at: Long,
    val source: String,
    val thumb: String,
    val state: String,
    val theme: String = AppTheme.PASTEL_MAROON.name,
)

/**
 * Last N (default 10) edits, kept on disk so they can be re-edited later.
 * Recording is explicit (save / share / minimize / close-ish), never silent uploads.
 */
object HistoryStore {

    const val MAX = 10
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun root(context: Context) = File(context.filesDir, "history").apply { mkdirs() }
    private fun indexFile(context: Context) = File(root(context), "index.json")

    fun list(context: Context): List<HistoryEntry> = synchronized(this) {
        val raw = runCatching {
            val f = indexFile(context)
            if (f.exists()) json.decodeFromString<List<HistoryEntry>>(f.readText()) else emptyList()
        }.getOrDefault(emptyList())
        raw.filter { File(it.source).exists() }
    }

    suspend fun record(
        context: Context,
        bitmap: Bitmap,
        state: EditorState,
        theme: AppTheme,
    ): HistoryEntry? = withContext(Dispatchers.IO) {
        synchronized(this@HistoryStore) {
            runCatching {
                val id = "h_${System.currentTimeMillis()}"
                val dir = File(root(context), id).apply { mkdirs() }

                val sourceFile = File(dir, "source.jpg")
                writeScaled(bitmap, sourceFile, maxSide = 1600, quality = 88)

                val thumbFile = File(dir, "thumb.jpg")
                writeScaled(bitmap, thumbFile, maxSide = 220, quality = 80)

                val entry = HistoryEntry(
                    id = id,
                    at = System.currentTimeMillis(),
                    source = sourceFile.absolutePath,
                    thumb = thumbFile.absolutePath,
                    state = json.encodeToString(EditorState.serializer(), state),
                    theme = theme.name,
                )
                val kept = (listOf(entry) + list(context)).take(MAX)
                indexFile(context).writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(HistoryEntry.serializer()), kept))
                prune(context, kept)
                entry
            }.getOrNull()
        }
    }

    fun snapshotOf(context: Context, entry: HistoryEntry): DraftSnapshot? {
        val state = runCatching {
            json.decodeFromString(EditorState.serializer(), entry.state)
        }.getOrNull() ?: return null
        val theme = runCatching { AppTheme.valueOf(entry.theme) }.getOrDefault(AppTheme.PASTEL_MAROON)
        return DraftSnapshot(sourceUri = entry.source, state = state, theme = theme)
    }

    suspend fun delete(context: Context, id: String) = withContext(Dispatchers.IO) {
        synchronized(this@HistoryStore) {
            runCatching { File(root(context), id).deleteRecursively() }
            val kept = list(context).filter { it.id != id }
            indexFile(context).writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(HistoryEntry.serializer()), kept))
        }
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        synchronized(this@HistoryStore) {
            runCatching { root(context).deleteRecursively() }
            root(context)
        }
    }

    // -- helpers -----------------------------------------------------------------------

    private fun writeScaled(bitmap: Bitmap, target: File, maxSide: Int, quality: Int) {
        val scale = maxSide.toFloat() / max(bitmap.width, bitmap.height)
        val out = if (scale >= 1f) bitmap else Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        FileOutputStream(target).use { out.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        if (out !== bitmap) out.recycle()
    }

    private fun prune(context: Context, kept: List<HistoryEntry>) {
        val ids = kept.map { it.id }.toSet()
        root(context).listFiles()?.forEach { f ->
            if (f.isDirectory && f.name !in ids) runCatching { f.deleteRecursively() }
        }
    }
}
