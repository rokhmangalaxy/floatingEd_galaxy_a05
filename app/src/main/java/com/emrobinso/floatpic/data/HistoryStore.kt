package com.emrobinso.floatpic.data

import android.content.Context
import android.graphics.Bitmap
import com.emrobinso.floatpic.core.image.LoadedImage
import com.emrobinso.floatpic.core.model.EditorState
import com.emrobinso.floatpic.feature.editor.DraftSnapshot
import com.emrobinso.floatpic.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * One re-editable session.
 *
 * Only file *names* are stored (the index lives next to the session folders), so the index
 * stays small and survives the app's data dir being moved/copied. Absolute paths are built
 * at read time.
 */
@Serializable
data class HistoryEntry(
    val id: String,
    val at: Long,
    /** file name inside the session folder, e.g. `source.jpg` */
    val source: String,
    /** file name inside the session folder, e.g. `thumb.jpg` */
    val thumb: String,
    /** JSON of [EditorState] */
    val state: String,
    val theme: String = AppTheme.MAROON_DARK.name,
)

/** All resolved files for one history session (kept out of the serialized model). */
data class HistoryFiles(val entry: HistoryEntry, val source: File, val thumb: File)

/**
 * Last N (50) edits, kept on disk so they can be re-edited later.
 *
 * Recording is explicit — save, share, minimize or leaving the editor with real edits —
 * never silent uploads, so the list matches "what I actually made".
 */
object HistoryStore {

    const val MAX = 50
    private const val SOURCE_NAME = "source.jpg"
    private const val THUMB_NAME = "thumb.jpg"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun root(context: Context) = File(context.filesDir, "history").apply { mkdirs() }
    private fun indexFile(context: Context) = File(root(context), "index.json")
    private fun dirOf(context: Context, id: String) = File(root(context), id)

    /**
     * Resolves the files of an entry. Older builds stored absolute paths, newer ones store
     * the bare file name — both are accepted so an app update never eats existing history.
     */
    fun filesOf(context: Context, entry: HistoryEntry): HistoryFiles {
        val dir = dirOf(context, entry.id)
        fun resolve(raw: String) = if (raw.contains('/')) File(raw) else File(dir, raw)
        return HistoryFiles(entry = entry, source = resolve(entry.source), thumb = resolve(entry.thumb))
    }

    /** Newest first; entries whose files vanished are dropped and not re-written. */
    fun list(context: Context): List<HistoryEntry> = synchronized(this) {
        val raw = runCatching {
            val f = indexFile(context)
            if (f.exists()) json.decodeFromString(ListSerializer(HistoryEntry.serializer()), f.readText())
            else emptyList()
        }.getOrDefault(emptyList())
        raw.filter {
            val files = filesOf(context, it)
            files.source.exists() && files.source.length() > 0L
        }
    }

    /**
     * Saves the session. The *edited* look is reproduced from the ORIGINAL picture plus the
     * editor state, so the source stays full resolution — no double generation loss and the
     * same image can be re-opened and edited again later.
     *
     * @param bitmap source bitmap currently open in the editor
     * @param sourceFile local copy on disk (preferred: avoids re-compressing the original)
     */
    suspend fun record(
        context: Context,
        bitmap: Bitmap,
        state: EditorState,
        theme: AppTheme,
        sourceFile: File? = null,
        // same session -> the existing row is updated instead of adding a duplicate
        sessionId: String? = null,
    ): HistoryEntry? = withContext(Dispatchers.IO) {
        synchronized(this@HistoryStore) {
            runCatching {
                val id = sessionId ?: "h_${System.currentTimeMillis()}"
                val dir = dirOf(context, id).apply { mkdirs() }

                val sourceOut = File(dir, SOURCE_NAME)
                val copy = sourceFile?.takeIf { it.exists() && it.length() > 0L }
                // copy the source at most once per session: re-recording only refreshes the
                // state/thumb, otherwise every autosave would copy megabytes again
                val sameFile = copy != null && copy.absolutePath == sourceOut.absolutePath
                val alreadyStored = sessionId != null && sourceOut.exists() && sourceOut.length() > 0L
                when {
                    alreadyStored || sameFile -> Unit
                    copy != null -> copy.copyTo(sourceOut, overwrite = true)
                    else -> writeScaled(bitmap, sourceOut, maxSide = 1600, quality = 90)
                }

                writeScaled(bitmap, File(dir, THUMB_NAME), maxSide = 220, quality = 80)

                val entry = HistoryEntry(
                    id = id,
                    at = System.currentTimeMillis(),
                    source = SOURCE_NAME,
                    thumb = THUMB_NAME,
                    state = json.encodeToString(EditorState.serializer(), state),
                    theme = theme.name,
                )
                val kept = (listOf(entry) + list(context).filter { it.id != id }).take(MAX)
                writeIndex(context, kept)
                prune(context, kept)
                entry
            }.getOrNull()
        }
    }

    fun snapshotOf(context: Context, entry: HistoryEntry): DraftSnapshot? {
        val state = runCatching {
            json.decodeFromString(EditorState.serializer(), entry.state)
        }.getOrNull() ?: return null
        val theme = runCatching { AppTheme.valueOf(entry.theme) }.getOrDefault(AppTheme.MAROON_DARK)
        val source = filesOf(context, entry).source
        if (!source.exists()) return null
        return DraftSnapshot(sourceUri = source.absolutePath, state = state, theme = theme)
    }

    suspend fun delete(context: Context, id: String) = withContext(Dispatchers.IO) {
        synchronized(this@HistoryStore) {
            runCatching { dirOf(context, id).deleteRecursively() }
            val kept = list(context).filter { it.id != id }
            writeIndex(context, kept)
        }
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        synchronized(this@HistoryStore) {
            runCatching { root(context).deleteRecursively() }
            root(context)
        }
    }

    // -- helpers -----------------------------------------------------------------------

    private fun writeIndex(context: Context, entries: List<HistoryEntry>) {
        runCatching {
            indexFile(context).writeText(
                json.encodeToString(ListSerializer(HistoryEntry.serializer()), entries),
            )
        }
    }

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
