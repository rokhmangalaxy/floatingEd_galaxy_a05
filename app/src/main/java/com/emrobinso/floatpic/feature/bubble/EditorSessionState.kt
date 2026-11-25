package com.emrobinso.floatpic.feature.bubble

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.emrobinso.floatpic.R
import com.emrobinso.floatpic.core.image.ImageLoader
import com.emrobinso.floatpic.data.AiEditClient
import com.emrobinso.floatpic.data.DraftSnapshot
import com.emrobinso.floatpic.data.DraftStore
import com.emrobinso.floatpic.data.Exporter
import com.emrobinso.floatpic.data.HistoryEntry
import com.emrobinso.floatpic.data.HistoryStore
import com.emrobinso.floatpic.feature.ai.AiSessionState
import com.emrobinso.floatpic.feature.editor.EditorViewModel
import com.emrobinso.floatpic.feature.editor.ShareIntents
import com.emrobinso.floatpic.ui.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One editing session, owned by [BubbleService] instead of an Activity.
 *
 * This is the whole reason the editor can live in an overlay window: the picture, the undo
 * stack, the history index and the AI state sit in a plain object that outlives every window
 * we attach and detach. Before, all of it hung off an Activity, so the editor could only
 * exist while that Activity was on screen.
 */
class EditorSessionState(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    val vm = EditorViewModel()
    val ai = AiSessionState(context, scope)

    /** history row this session writes to; null = a picture that has no row yet */
    var currentRow by mutableStateOf<String?>(null)
    var theme by mutableStateOf(AppTheme.MAROON_DARK)
    var history by mutableStateOf<List<HistoryEntry>>(emptyList())
    var historyIndex by mutableStateOf(-1)

    /**
     * True once a picture arrived from outside the app (share, gallery, history). The empty
     * preview is not such a picture, so it must not write a history row of its own.
     */
    var sourceWasChosen by mutableStateOf(false)

    fun refreshHistory() {
        history = HistoryStore.list(context)
        val id = currentRow
        historyIndex = if (id == null) -1 else history.indexOfFirst { it.id == id }
    }

    /** A shared / picked picture: its own row, and the base every later edit starts from. */
    fun openUri(uri: Uri) {
        currentRow = null
        sourceWasChosen = true
        vm.loadImage { ImageLoader.load(context, uri) }
        scope.launch {
            kotlinx.coroutines.delay(300)
            historyIndex = -1
            refreshHistory()
        }
    }

    fun openFile(file: File, entryId: String? = null) {
        currentRow = entryId
        sourceWasChosen = true
        vm.loadImage { ImageLoader.loadFile(context, file) }
        scope.launch {
            kotlinx.coroutines.delay(300)
            refreshHistory()
        }
    }

    fun restoreDraft(sessionId: String) {
        scope.launch {
            val snapshot = DraftStore.load(context, sessionId) ?: return@launch
            currentRow = snapshot.historyId
            sourceWasChosen = true
            val file = snapshot.sourceUri?.let { File(it) }
            if (file != null && file.exists()) {
                vm.openAndRestore(snapshot) { ImageLoader.loadFile(context, file) }
            } else {
                vm.restore(snapshot)
            }
            refreshHistory()
        }
    }

    /**
     * Writes the session into the history, re-using [currentRow] so one picture keeps one
     * row. Returns the row it wrote, or null when there was nothing worth storing.
     */
    suspend fun autosave(force: Boolean = false): HistoryEntry? = withContext(NonCancellable) {
        val bmp = vm.source ?: return@withContext null
        val firstRow = currentRow == null && sourceWasChosen
        if (!firstRow && !vm.hasRealEdits()) return@withContext null
        val rowBitmap = renderCurrent() ?: vm.baseForHistory() ?: bmp
        if (!force && !vm.isDirty()) return@withContext null
        val entry = HistoryStore.record(
            context = context,
            bitmap = rowBitmap,
            state = vm.currentState(),
            theme = theme,
            sourceFile = vm.sourceFile,
            sessionId = currentRow,
        )
        if (entry != null) {
            currentRow = entry.id
            vm.markSaved()
            refreshHistory()
        }
        entry
    }

    /**
     * Stores exactly the bytes that were just shared/saved as its own row, so the row and the
     * file the user sent out can never drift apart.
     */
    suspend fun recordRendered(rendered: android.graphics.Bitmap): HistoryEntry? =
        withContext(NonCancellable) {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "rendered").apply { mkdirs() }
                    val out = File(dir, "out_${System.currentTimeMillis()}.jpg")
                    out.outputStream().use {
                        rendered.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, it)
                    }
                    out
                }.getOrNull()
            }
            val entry = HistoryStore.record(
                context = context,
                bitmap = rendered,
                state = vm.currentState(),
                theme = theme,
                sourceFile = file,
                sessionId = null,
            )
            if (entry != null) {
                currentRow = entry.id
                vm.markSaved()
                refreshHistory()
            }
            entry
        }

    fun openHistoryEntry(entry: HistoryEntry) {
        val snapshot = HistoryStore.snapshotOf(context, entry) ?: run {
            Toast.makeText(context, R.string.open_failed, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val file = HistoryStore.filesOf(context, entry).source
            vm.openAndRestore(snapshot) { ImageLoader.loadFile(context, file) }
            vm.source?.let { vm.useBase(it, file) }
            currentRow = entry.id
            sourceWasChosen = true
            refreshHistory()
        }
    }

    fun renderCurrent(): android.graphics.Bitmap? {
        val bmp = vm.source ?: return null
        return runCatching { Exporter.render(context, bmp, vm.currentState(), vm.orientation) }.getOrNull()
    }

    /** Save to the gallery; the rendered file also becomes a history row. */
    fun saveToGallery(onDone: () -> Unit = {}) {
        scope.launch {
            val out = renderCurrent()
            if (out == null) {
                Toast.makeText(context, R.string.save_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val saved = runCatching { Exporter.saveToGallery(context, out, png = false) }
                .onSuccess { vm.reportSaved() }
                .onFailure {
                    Toast.makeText(context, R.string.save_failed, Toast.LENGTH_SHORT).show()
                }
                .isSuccess
            if (saved) recordRendered(out)
            out.recycle()
            onDone()
        }
    }

    /** Share: one render, the same file goes out and into the history. */
    fun shareNow() {
        val out = renderCurrent()
        if (out == null) {
            Toast.makeText(context, R.string.share_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = runCatching { Exporter.writeShareCopy(context, out) }.getOrNull()
        scope.launch {
            recordRendered(out)
            runCatching { out.recycle() }
        }
        if (uri == null) {
            Toast.makeText(context, R.string.share_failed, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(ShareIntents.shareImage(context, uri), context.getString(R.string.action_share))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            Toast.makeText(context, R.string.share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** Swaps in an AI result, then records it as its own row. */
    fun applyAi(bitmap: android.graphics.Bitmap) {
        val file = AiEditClient.persist(context, bitmap)
        vm.replaceSource(bitmap, file)
        currentRow = null
        scope.launch {
            val entry = autosave(force = true)
            if (entry == null) {
                Toast.makeText(context, R.string.commit_edit_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Keeps a draft on disk so a killed process can still be restored. */
    suspend fun saveDraft(sessionId: String) {
        val bmp = vm.source ?: return
        val file = Exporter.ensureSourceFile(context, bmp, vm.sourceFile) ?: return
        vm.attachSourceFile(file)
        DraftStore.save(
            context,
            sessionId,
            DraftSnapshot(
                sourceUri = file.absolutePath,
                state = vm.currentState(),
                theme = theme,
                historyId = currentRow,
            ),
        )
    }

    fun clearCurrent() {
        vm.clearSession()
        currentRow = null
        historyIndex = -1
        sourceWasChosen = false
    }
}
