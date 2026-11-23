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
    val loading: Boolean = true,
    val error: String? = null,
    val state: EditorState = EditorState(),
    val theme: AppTheme = AppTheme.PASTEL_MAROON,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val savedTick: Int = 0,
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
    private var committed: EditorState = EditorState()

    fun loadImage(loader: suspend () -> LoadedImage) {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching { loader() }
                .onSuccess { loaded ->
                    source?.recycle()
                    source = loaded.bitmap
                    orientation = loaded.orientation
                    committed = EditorState()
                    undo.clear()
                    redo.clear()
                    _ui.update {
                        it.copy(
                            loading = false,
                            state = committed,
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
        undo.clear()
        redo.clear()
        _ui.update {
            it.copy(
                state = snapshot.state,
                theme = snapshot.theme,
                loading = false,
                canUndo = false,
                canRedo = false,
            )
        }
    }

    fun setTheme(theme: AppTheme) = _ui.update { it.copy(theme = theme) }

    fun currentState(): EditorState = _ui.value.state

    fun currentTheme(): AppTheme = _ui.value.theme

    /** Live transform during a gesture — never touches history. */
    fun previewTransform(t: Transform) {
        _ui.update { it.copy(state = it.state.copy(transform = t)) }
    }

    /** Gesture ended: one history entry, independent of frame count. */
    fun commitTransform(t: Transform) {
        pushHistory()
        _ui.update { it.copy(state = it.state.copy(transform = t), canUndo = true, canRedo = false) }
        committed = _ui.value.state
    }

    private fun pushHistory() {
        undo.addLast(committed)
        while (undo.size > 40) undo.removeFirst()
        redo.clear()
    }

    private fun mutate(block: (EditorState) -> EditorState) {
        pushHistory()
        _ui.update { it.copy(state = block(it.state), canUndo = true, canRedo = false) }
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
        val (c1, _, c2) = EditorRenderer.ambientColors(bmp)
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

    fun bubbleThumb(): Bitmap? = source?.let { EditorRenderer.bubbleThumb(it) }

    fun reportSaved() = _ui.update { it.copy(savedTick = it.savedTick + 1) }

    fun snapRotation(rotation: Float): Float = TextPainter.snapRotation(rotation)

    override fun onCleared() {
        super.onCleared()
        source?.recycle()
        source = null
    }
}
