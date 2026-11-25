package com.emrobinso.floatpic.feature.editor

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emrobinso.floatpic.core.image.LoadedImage
import com.emrobinso.floatpic.core.model.AspectPreset
import com.emrobinso.floatpic.core.model.BackgroundKind
import com.emrobinso.floatpic.core.model.BackgroundSpec
import com.emrobinso.floatpic.core.model.EditorState
import com.emrobinso.floatpic.core.model.ScaleMode
import com.emrobinso.floatpic.core.model.TextPosition
import com.emrobinso.floatpic.core.model.TextStylePreset
import com.emrobinso.floatpic.core.model.Transform
import com.emrobinso.floatpic.core.render.EditorRenderer
import com.emrobinso.floatpic.core.render.TextPainter
import com.emrobinso.floatpic.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    /**
     * Starts FALSE: a cold start shows the empty preview straight away. It used to start
     * true, so the very first frame was a spinner even though there was nothing to load —
     * the "preview langsung animasi loading" the user saw on every launch.
     */
    val loading: Boolean = false,
    val error: String? = null,
    val state: EditorState = EditorState(),
    val theme: AppTheme = AppTheme.MAROON_DARK,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val savedTick: Int = 0,
    /** true once the user changed anything since the last history record */
    val dirty: Boolean = false,
    /** an AI run swapped the picture, so there is always something worth keeping */
    val sourceReplaced: Boolean = false,
)

class EditorViewModel : ViewModel() {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    var source: Bitmap? = null
        private set
    var orientation: Int = 0
        private set

    /** Local, re-editable copy of the current source (used to record history). */
    var sourceFile: java.io.File? = null
        private set

    /**
     * The picture as it was when this session opened: the original file / shared image, NOT
     * an AI result. AI always sends this one, so a second prompt does not feed the model its
     * own output (which compounded artefacts and lost the original framing).
     */
    var baseSource: Bitmap? = null
        private set

    /** File behind [baseSource], re-read at full size on demand. */
    private var baseFile: java.io.File? = null

    /**
     * The state the picture had right after the last source swap (open, AI result, history
     * reopen). "Reset editan terakhir" returns here: the picture stays, the edits go.
     */
    private var editBase: EditorState = EditorState()

    fun currentEditBase(): EditorState = editBase

    private val undo = ArrayDeque<EditorState>()
    private val redo = ArrayDeque<EditorState>()
    private var committed: EditorState = EditorState()

    fun loadImage(loader: suspend () -> LoadedImage) {
        viewModelScope.launch { loadImageNow(loader) }
    }

    /**
     * Same as [loadImage] but suspends until the bitmap is in place, so a caller can apply a
     * saved state afterwards without racing the "new image -> default state" reset.
     */
    suspend fun loadImageNow(loader: suspend () -> LoadedImage) {
        _ui.update { it.copy(loading = true, error = null) }
        runCatching { loader() }
                .onSuccess { loaded ->
                    source?.recycle()
                    source = loaded.bitmap
                    // a newly opened picture is its own base
                    if (baseSource !== loaded.bitmap) {
                        baseSource?.recycle()
                        baseSource = loaded.bitmap
                        baseFile = loaded.cacheFile
                    }
                    // The loader already bakes EXIF rotation into the bitmap, so the
                    // source is upright: using the EXIF angle again would swap w/h and
                    // make the preview ratio disagree with the exported file.
                    orientation = 0
                    sourceFile = loaded.cacheFile
                    committed = EditorState()
                    editBase = EditorState()
                    undo.clear()
                    redo.clear()
                    _ui.update {
                        it.copy(
                            loading = false,
                            state = committed,
                            canUndo = false,
                            canRedo = false,
                            dirty = false,
                            sourceReplaced = false,
                        )
                    }
                }
                .onFailure { e ->
                    _ui.update { it.copy(loading = false, error = e.message ?: "decode failed") }
                }
    }

    /**
     * Opens a history session: load the source first, then re-apply the saved edits.
     * The loader is passed in because decoding needs a Context (the ViewModel has none).
     */
    suspend fun openAndRestore(snapshot: DraftSnapshot, loader: suspend () -> LoadedImage) {
        loadImageNow(loader)
        restore(snapshot)
    }

    /**
     * A restored session is based on the file it was saved with. Because history rows now
     * contain the RENDERED picture, this file is exactly what AI should edit next.
     */
    fun useBase(bitmap: Bitmap, file: java.io.File?) {
        if (baseSource !== bitmap) {
            if (baseSource !== source) baseSource?.recycle()
            baseSource = bitmap
        }
        baseFile = file
    }

    /** File behind the session base, so a caller can decode it at full size. */
    fun baseFile(): java.io.File? = baseFile

    /**
     * The picture AI should edit: the session base (never an AI result).
     * Falls back to the live source only when no base was recorded.
     */
    fun aiSource(): Bitmap? = baseSource ?: source

    /**
     * Re-applies saved edits. The palette is NOT taken from the snapshot: opening an old
     * entry used to flip the UI back to the default theme. The theme is an app-wide
     * preference, restored once in onCreate.
     */
    fun restore(snapshot: DraftSnapshot) {
        committed = snapshot.state
        editBase = snapshot.state
        undo.clear()
        redo.clear()
        _ui.update {
            it.copy(
                state = snapshot.state,
                loading = false,
                canUndo = false,
                canRedo = false,
                dirty = false,
            )
        }
    }

    fun setTheme(theme: AppTheme) = _ui.update { it.copy(theme = theme) }

    fun isDirty(): Boolean = _ui.value.dirty

    /**
     * True only when the user actually changed the picture. The bundled sample opens with a
     * pristine [EditorState]; that preview must NOT be written to history just because the
     * editor was opened and left again.
     */
    fun hasRealEdits(): Boolean {
        val ui = _ui.value
        val s = ui.state
        if (ui.sourceReplaced) return true
        return s != EditorState()
    }

    /** Called after the session was written to [com.emrobinso.floatpic.data.HistoryStore]. */
    fun markSaved() = _ui.update { it.copy(dirty = false) }

    fun currentState(): EditorState = _ui.value.state

    fun currentTheme(): AppTheme = _ui.value.theme

    /** Live transform during a gesture — never touches history. */
    fun previewTransform(t: Transform) {
        _ui.update { it.copy(state = it.state.copy(transform = t), dirty = true) }
    }

    /** Gesture ended: one history entry, independent of frame count. */
    fun commitTransform(t: Transform) {
        pushHistory()
        _ui.update {
            it.copy(state = it.state.copy(transform = t), canUndo = true, canRedo = false, dirty = true)
        }
        committed = _ui.value.state
    }

    private fun pushHistory() {
        undo.addLast(committed)
        while (undo.size > 40) undo.removeFirst()
        redo.clear()
    }

    private fun mutate(block: (EditorState) -> EditorState) {
        pushHistory()
        _ui.update { it.copy(state = block(it.state), canUndo = true, canRedo = false, dirty = true) }
        committed = _ui.value.state
    }

    fun undo() {
        val prev = undo.removeLastOrNull() ?: return
        redo.addLast(committed)
        committed = prev
        _ui.update { it.copy(state = prev, canUndo = undo.isNotEmpty(), canRedo = true, dirty = true) }
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(committed)
        committed = next
        _ui.update { it.copy(state = next, canUndo = true, canRedo = redo.isNotEmpty(), dirty = true) }
    }

    /**
     * Changing the frame ratio re-fits the image (like Canva/IG): offsets stored in output
     * px would otherwise point outside a narrower frame.
     */
    fun setAspect(preset: AspectPreset) = mutate {
        if (it.aspect == preset) it else it.copy(aspect = preset, transform = Transform())
    }

    /** Reset pan/zoom/rotation but keep background, padding and text (docs/04). */
    fun resetTransform() = mutate { it.copy(transform = Transform()) }

    fun setScaleMode(mode: ScaleMode) = mutate { it.copy(scaleMode = mode) }

    fun flipHorizontal() = mutate {
        it.copy(transform = it.transform.copy(flipH = !it.transform.flipH))
    }

    fun flipVertical() = mutate {
        it.copy(transform = it.transform.copy(flipV = !it.transform.flipV))
    }

    /** Live rotation while the slider moves — no history entry per pixel. */
    fun previewRotation(deg: Float) {
        _ui.update {
            it.copy(
                state = it.state.copy(transform = it.state.transform.copy(rotation = deg)),
                dirty = true,
            )
        }
    }

    /** Slider released: one undo step. */
    fun commitRotation(deg: Float) {
        pushHistory()
        _ui.update {
            it.copy(
                state = it.state.copy(transform = it.state.transform.copy(rotation = deg)),
                canUndo = true,
                canRedo = false,
                dirty = true,
            )
        }
        committed = _ui.value.state
    }

    /** Reset 1 — everything: pristine state AND the session base picture (undoable). */
    fun resetAll() = mutate {
        val base = baseSource
        if (base != null && base !== source) {
            source?.recycle()
            source = base
        }
        sourceFile = baseFile
        editBase = EditorState()
        EditorState()
    }

    /** Reset 2 — edits only: keep the current picture, drop the edits (undoable). */
    fun resetEdits() = mutate { editBase }

    fun setPadding(dp: Float) = mutate { it.copy(paddingDp = dp) }

    fun setCorner(dp: Float) = mutate { it.copy(cornerDp = dp) }

    fun toggleShadow() = mutate { it.copy(shadow = !it.shadow) }

    fun setBackground(spec: BackgroundSpec) = mutate { it.copy(background = spec) }

    fun useAmbientBackground() {
        val bmp = source ?: return
        val (c1, _, c2) = EditorRenderer.ambientColors(bmp)
        mutate {
            it.copy(
                background = BackgroundSpec(
                    kind = BackgroundKind.GRADIENT,
                    color = c1.toLong(),
                    color2 = c2.toLong(),
                ),
            )
        }
    }

    fun setText(text: String) = mutate { it.copy(text = it.text.copy(text = text)) }

    fun setTextPosition(pos: TextPosition) = mutate { it.copy(text = it.text.copy(position = pos)) }

    fun setTextAlign(align: com.emrobinso.floatpic.core.model.TextAlign) = mutate {
        it.copy(text = it.text.copy(align = align))
    }

    fun setTextAnchor(anchor: com.emrobinso.floatpic.core.model.TextAnchor) = mutate {
        it.copy(text = it.text.copy(anchor = anchor))
    }

    /** Caption rotation in degrees; the slider reports continuously, so no history per px. */
    fun previewTextRotation(deg: Float) {
        _ui.update { ui ->
            ui.copy(
                state = ui.state.copy(text = ui.state.text.copy(rotation = deg)),
                dirty = true,
            )
        }
    }

    /** Slider released: single undo step. */
    fun commitTextRotation(deg: Float) {
        pushHistory()
        _ui.update { ui ->
            ui.copy(
                state = ui.state.copy(text = ui.state.text.copy(rotation = deg)),
                canUndo = true,
                canRedo = false,
                dirty = true,
            )
        }
        committed = _ui.value.state
    }

    /** Swaps the whole picture for an AI result (keeps undo + history working). */
    fun replaceSource(bitmap: android.graphics.Bitmap, file: java.io.File?) {
        pushHistory()
        source?.recycle()
        source = bitmap
        sourceFile = file
        _ui.update {
            it.copy(
                loading = false,
                error = null,
                state = EditorState(),
                canUndo = true,
                canRedo = false,
                dirty = true,
                sourceReplaced = true,
            )
        }
        committed = _ui.value.state
        // an AI result is a new starting point: undoing the edits stops here
        editBase = committed
    }

    fun setTextStyle(style: TextStylePreset) = mutate {
        it.copy(text = it.text.copy(style = style))
    }

    fun setTextSize(frac: Float) = mutate { it.copy(text = it.text.copy(sizeFraction = frac)) }

    /**
     * Relative positioning: keep the zoom, snap the image to one of the 9 anchors inside
     * its frame (docs/06). Offsets live in output px so preview == export.
     */
    fun alignTo(anchorFx: Float, anchorFy: Float) {
        val bmp = source ?: return
        val state = _ui.value.state
        val out = EditorRenderer.outputSize(state, bmp.width, bmp.height, orientation)
        val outW = out.w.toFloat()
        val outH = out.h.toFloat()
        val pad = state.paddingDp * (outW / 1080f)
        val availW = (outW - pad * 2).coerceAtLeast(1f)
        val availH = (outH - pad * 2).coerceAtLeast(1f)
        val base = EditorRenderer.baseScale(state, outW, outH, bmp.width, bmp.height)
        val s = base * state.transform.scale
        val imgW = bmp.width * s
        val imgH = bmp.height * s
        val overflowX = ((imgW - availW) / 2f).coerceAtLeast(0f)
        val overflowY = ((imgH - availH) / 2f).coerceAtLeast(0f)
        val tx = overflowX * (2f * anchorFx - 1f) / (outW / 1080f)
        val ty = overflowY * (2f * anchorFy - 1f) / (outW / 1080f)
        mutate { it.copy(transform = it.transform.copy(offsetX = tx, offsetY = ty)) }
    }

    /** Empties the editor: used by the trash shortcut on the preview. */
    fun clearSession() {
        source?.recycle()
        source = null
        sourceFile = null
        if (baseSource !== null && baseSource !== source) baseSource?.recycle()
        baseSource = null
        baseFile = null
        editBase = EditorState()
        committed = EditorState()
        undo.clear()
        redo.clear()
        _ui.update {
            it.copy(
                loading = false,
                error = null,
                state = EditorState(),
                canUndo = false,
                canRedo = false,
                dirty = false,
                sourceReplaced = false,
            )
        }
    }

    fun bubbleThumb(): Bitmap? = source?.let { EditorRenderer.bubbleThumb(it) }

    /** Snapshot of the editing base without any AI result, for the history row. */
    fun baseForHistory(): Bitmap? = baseSource ?: source

    /** Records a source file that was created outside the loader (bubble session). */
    fun attachSourceFile(file: java.io.File) {
        sourceFile = file
    }

    fun reportSaved() = _ui.update { it.copy(savedTick = it.savedTick + 1) }

    fun snapRotation(rotation: Float): Float = TextPainter.snapRotation(rotation)

    override fun onCleared() {
        super.onCleared()
        source?.recycle()
        source = null
        if (baseSource !== source) baseSource?.recycle()
        baseSource = null
    }
}
