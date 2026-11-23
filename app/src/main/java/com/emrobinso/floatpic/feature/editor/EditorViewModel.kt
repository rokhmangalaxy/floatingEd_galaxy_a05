package com.emrobinso.floatpic.feature.editor

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emrobinso.floatpic.core.image.ImageLoader
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
import com.emrobinso.floatpic.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class UiState(
    val loading: Boolean = true,
    val error: String? = null,
    val state: EditorState = EditorState(),
    val theme: AppTheme = AppTheme.PASTEL_MAROON,
    val pendingSessionId: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val saveResult: String? = null,
    val imageReady: Boolean = false,
)

class EditorViewModel : ViewModel() {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    var source: Bitmap? = null
        private set
    var orientation: Int = 0
        private set

    private val undo = ArrayDeque<EditorState>()
    private val redo = ArrayDeque<EditorState>()
    private val maxHistory = 40
    private var committed: EditorState = EditorState()

    fun loadImage(loader: suspend () -> LoadedImage) {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching { loader() }
                .onSuccess { loaded ->
                    source = loaded.bitmap
                    orientation = loaded.orientation
                    val fresh = EditorState().copy(
                        background = EditorState().background,
                    )
                    committed = fresh
                    undo.clear(); redo.clear()
                    _ui.update {
                        it.copy(
                            loading = false,
                            imageReady = true,
                            state = fresh,
                            canUndo = false,
                            canRedo = false,
                        )
                    }
                }
                .onFailure { e ->
                    _ui.update { it.copy(loading = false, error = e.message ?: "decode failed") }
                }
        }
    }

    fun restore(snapshot: DraftSnapshot) {
        committed = snapshot.state
        undo.clear(); redo.clear()
        _ui.update {
            it.copy(
                state = snapshot.state,
                theme = snapshot.theme,
                loading = false,
                imageReady = source != null,
                canUndo = false,
                canRedo = false,
            )
        }
    }

    fun theme(): AppTheme = _ui.value.theme

    fun setTheme(theme: AppTheme) = _ui.update { it.copy(theme = theme) }

    fun currentState(): EditorState = _ui.value.state

    fun sourceSize(): Pair<Int, Int>? = source?.let { it.width to it.height }

    fun outputSize(): Pair<Int, Int> {
        val bmp = source ?: return 1080 to 1080
        val size = EditorRenderer.outputSize(committed, bmp.width, bmp.height, orientation)
        return size.w to size.h
    }

    // ---- mutation helpers -------------------------------------------------

    /** Live transform changes during a gesture: no history entry per frame. */
    fun previewTransform(t: Transform) {
        _ui.update { it.copy(state = it.state.copy(transform = t)) }
    }

    /** Called when a gesture ends — pushes a single undo entry. */
    fun commitTransform(t: Transform) {
        pushHistory()
        _ui.update {
            it.copy(state = it.state.copy(transform = t), canUndo = true, canRedo = false)
        }
        committed = _ui.value.state
    }

    private fun pushHistory() {
        undo.addLast(committed)
        while (undo.size > maxHistory) undo.removeFirst()
        redo.clear()
    }

    fun mutate(block: (EditorState) -> EditorState) {
        pushHistory()
        _ui.update {
            it.copy(state = block(it.state), canUndo = true, canRedo = false)
        }
        committed = _ui.value.state
    }

    fun undo() {
        val prev = undo.removeLastOrNull() ?: return
        redo.addLast(committed)
        committed = prev
        _ui.update { it.copy(state = prev, canUndo = undo.isNotEmpty(), canRedo = true) }
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(committed)
        committed = next
        _ui.update { it.copy(state = next, canUndo = true, canRedo = redo.isNotEmpty()) }
    }

    fun reset() {
        mutate { it.copy(transform = Transform()) }
    }

    fun setAspect(preset: AspectPreset) = mutate { it.copy(aspect = preset) }

    fun setScaleMode(mode: ScaleMode) = mutate { it.copy(scaleMode = mode) }

    fun rotateBy(deg: Float) = mutate {
        it.copy(transform = it.transform.copy(rotation = it.transform.rotation + deg))
    }

    fun flipHorizontal() = mutate {
        it.copy(transform = it.transform.copy(flipH = !it.transform.flipH))
    }

    fun flipVertical() = mutate {
        it.copy(transform = it.transform.copy(flipV = !it.transform.flipV))
    }

    fun setPadding(dp: Float) = mutate { it.copy(paddingDp = dp) }

    fun setCorner(dp: Float) = mutate { it.copy(cornerDp = dp) }

    fun toggleShadow() = mutate { it.copy(shadow = !it.shadow) }

    fun setBackground(spec: BackgroundSpec) = mutate { it.copy(background = spec) }

    fun useAmbientBackground() {
        val bmp = source ?: return
        val (c1, c2, _) = EditorRenderer.ambientColors(bmp)
        mutate {
            it.copy(
                background = BackgroundSpec(
                    kind = BackgroundKind.GRADIENT,
                    color = c1,
                    color2 = c2,
                ),
            )
        }
    }

    fun setText(text: String) = mutate { it.copy(text = it.text.copy(text = text)) }

    fun setTextPosition(pos: TextPosition) = mutate { it.copy(text = it.text.copy(position = pos)) }

    fun setTextStyle(style: TextStylePreset) = mutate { it.copy(text = it.text.copy(style = style)) }

    fun setTextSize(frac: Float) = mutate { it.copy(text = it.text.copy(sizeFraction = frac)) }

    fun clearText() = mutate { it.copy(text = it.text.copy(text = "")) }

    /**
     * Tap-to-align: keeps the current zoom but re-centres the image on the frame per the
     * 3x3 anchor. Offset is expressed in output px so the preview matches the export.
     */
    fun alignTo(anchorFx: Float, anchorFy: Float, outW: Float, outH: Float, srcW: Int, srcH: Int) {
        val state = _ui.value.state
        val pad = state.paddingDp * (outW / 1080f)
        val availW = (outW - pad * 2).coerceAtLeast(1f)
        val availH = (outH - pad * 2).coerceAtLeast(1f)
        val base = EditorRenderer.baseScale(state, outW, outH, srcW, srcH) * state.transform.scale
        val imgW = srcW * base
        val imgH = srcH * base
        val overflowX = ((imgW - availW) / 2f).coerceAtLeast(0f)
        val overflowY = ((imgH - availH) / 2f).coerceAtLeast(0f)
        val tx = overflowX * (2f * anchorFx - 1f) / (outW / 1080f)
        val ty = overflowY * (2f * anchorFy - 1f) / (outH / 1080f)
        mutate { it.copy(transform = it.transform.copy(offsetX = tx, offsetY = ty)) }
    }

    fun bubbleThumb(): Bitmap? = source?.let { EditorRenderer.bubbleThumb(it) }

    fun reportSaved() = _ui.update { it.copy(saveResult = "saved") }

    fun clearSaveResult() = _ui.update { it.copy(saveResult = null) }

    fun snapRotation(rotation: Float): Float {
        val snapped = com.emrobinso.floatpic.core.render.TextPainter.snapRotation(rotation)
        return if (abs(snapped - rotation) > 0.001f) snapped else rotation
    }

    /** Push one history entry for a text edit that is already reflected in the UI state. */
    fun commitTextEdit() {
        if (committed.text != _ui.value.state.text) {
            pushHistory()
            committed = _ui.value.state
            _ui.update { it.copy(canUndo = undo.isNotEmpty(), canRedo = false) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        source?.recycle()
        source = null
    }

    companion object {
        fun round(value: Float): Int = value.roundToInt()
    }
}
