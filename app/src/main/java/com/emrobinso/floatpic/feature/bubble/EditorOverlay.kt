package com.emrobinso.floatpic.feature.bubble

import android.content.Context
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.emrobinso.floatpic.data.HistoryStore
import com.emrobinso.floatpic.feature.editor.EditorScreen
import com.emrobinso.floatpic.ui.rememberHaptics
import com.emrobinso.floatpic.ui.theme.FloatPicTheme
import kotlinx.coroutines.launch

/**
 * A ComposeView that brings its own lifecycle + saved-state owners.
 *
 * This is the piece the old bubble was missing: a Compose composition needs a
 * SavedStateRegistryOwner and a ViewModelStoreOwner, which an overlay window does not
 * provide. Without them the composition throws and the process dies — exactly the force
 * close reported when the bubble was switched on. Handing Compose these three owners makes
 * an overlay window as safe as an Activity.
 */
class OverlayComposeView(context: Context) :
    AbstractComposeView(context),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    /** set before the view is added to a window */
    var content: (@Composable () -> Unit)? = null

    @Composable
    override fun Content() {
        content?.invoke()
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: LifecycleRegistry get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    init {
        // The composition is attached to the window, not an Activity. DisposeOnDetached is
        // the only strategy whose mechanism exists here (the view-tree one needs an owner
        // further up, which an overlay window has not got) — and we keep the session OUTSIDE
        // the composition, so disposing it on detach never loses the picture.
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        savedState.performAttach()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED
        // Compose reads the owners from the ViewTree. A window created by WindowManager has
        // no Activity to supply them, which is what killed the old Compose bubble.
        setViewTreeLifecycleOwner(this)
        setViewTreeViewModelStoreOwner(this)
        setViewTreeSavedStateRegistryOwner(this)
    }

    fun dispose() {
        if (lifecycleRegistry.currentState != androidx.lifecycle.Lifecycle.State.DESTROYED) {
            lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED
        }
        store.clear()
    }
}

/**
 * The editor as a REAL system overlay, but the window is only as big as the CARD.
 *
 * A screen-sized window was the reason the app behind could be seen but not touched: an
 * overlay window swallows every touch inside its bounds, transparent pixels included. Sizing
 * the window to the card means the app behind keeps all the space around it — you can scroll
 * and tap it while the editor floats on top, and the card reaches the very edges of the
 * screen because the window travels with it instead of being pinned to the middle.
 */
private const val KEY_X = "card_x"
private const val KEY_Y = "card_y"

class EditorOverlay(
    private val context: Context,
    private val session: EditorSessionState,
    private val onMinimize: () -> Unit,
    private val onClose: () -> Unit,
    private val onOpenGallery: () -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: OverlayComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var cardWidthPx = 0
    private var cardHeightPx = 0
    private var cardX = 0f
    private var cardY = 0f

    val isShowing: Boolean get() = view != null

    fun show() {
        if (view != null) return
        val dm = context.resources.displayMetrics
        // 80 % of the width, capped by 82 % of the height so a tall card still fits
        cardWidthPx = (dm.widthPixels * 0.80f).toInt()
        cardHeightPx = (dm.heightPixels * 0.80f).toInt()
        val p = WindowManager.LayoutParams(
            cardWidthPx,
            cardHeightPx,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            // focusable: text fields and the keyboard need it. NOT_FOCUSABLE would make the
            // prompt box deaf and the back key unhandled.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // centred in what displayMetrics calls the app area; the card can travel from
            // here to any edge of the screen
            x = (dm.widthPixels - cardWidthPx) / 2
            y = (dm.heightPixels - cardHeightPx) / 2
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        params = p
        // the card stays where it was last dragged; the window IS the card, so restoring the
        // offset also places the window
        val prefs = context.getSharedPreferences("floatpic_editor", Context.MODE_PRIVATE)
        cardX = prefs.getFloat(KEY_X, 0f)
        cardY = prefs.getFloat(KEY_Y, 0f)
        clampOffsets()
        p.x = ((dm.widthPixels - cardWidthPx) / 2 + cardX).toInt()
        p.y = ((dm.heightPixels - cardHeightPx) / 2 + cardY).toInt()
        val compose = OverlayComposeView(context)
        compose.content = { EditorOverlayContent() }
        view = compose
        windowManager.addView(compose, p)
    }

    fun session(): EditorSessionState = session

    fun hide() {
        val v = view ?: return
        view = null
        runCatching { windowManager.removeView(v) }
        v.dispose()
    }

    private fun clampOffsets() {
        val dm = context.resources.displayMetrics
        val maxX = ((dm.widthPixels - cardWidthPx) / 2f).coerceAtLeast(0f)
        val maxY = ((dm.heightPixels - cardHeightPx) / 2f).coerceAtLeast(0f)
        cardX = cardX.coerceIn(-maxX, maxX)
        cardY = cardY.coerceIn(-maxY, maxY)
    }

    private fun rememberOffset() {
        context.getSharedPreferences("floatpic_editor", Context.MODE_PRIVATE)
            .edit().putFloat(KEY_X, cardX).putFloat(KEY_Y, cardY).apply()
    }

    /**
     * Moves the window by the card offset. The window travels instead of the card inside a
     * big window: that is what keeps the rest of the screen usable.
     */
    private fun applyLayout() {
        val p = params ?: return
        val v = view ?: return
        val dm = context.resources.displayMetrics
        val baseX = (dm.widthPixels - cardWidthPx) / 2
        val baseY = (dm.heightPixels - cardHeightPx) / 2
        p.x = (baseX + cardX).toInt().coerceIn(0, (dm.widthPixels - cardWidthPx).coerceAtLeast(0))
        p.y = (baseY + cardY).toInt().coerceIn(0, (dm.heightPixels - cardHeightPx).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(v, p) }
    }

    /** The card IS the window, so it simply fills it. */
    @Composable
    private fun EditorOverlayContent() {
        val ui by session.vm.ui.collectAsState()
        val scope = rememberCoroutineScope()
        val haptics = rememberHaptics()

        // autosave: 1.2 s after the last real edit
        androidx.compose.runtime.LaunchedEffect(ui.state, ui.dirty) {
            if (!ui.dirty || !session.vm.hasRealEdits()) return@LaunchedEffect
            kotlinx.coroutines.delay(1200)
            session.autosave()
        }

        FloatPicTheme(theme = ui.theme, dark = false) {
            Box(Modifier.fillMaxSize()) {
                EditorScreen(
                    state = ui.state,
                    bitmap = session.vm.source,
                    theme = ui.theme,
                    loading = ui.loading,
                    error = ui.error,
                    canUndo = ui.canUndo,
                    canRedo = ui.canRedo,
                    history = session.history,
                    thumbPath = { entry -> HistoryStore.filesOf(context, entry).thumb.absolutePath },
                    haptics = haptics,
                    modifier = Modifier.fillMaxSize(),
                    onClose = onClose,
                    onMinimize = onMinimize,
                    onDeleteHistory = { entry ->
                        scope.launch {
                            HistoryStore.delete(context, entry.id)
                            session.refreshHistory()
                        }
                    },
                    historyCurrent = session.historyIndex,
                    onStepHistory = { entry -> session.openHistoryEntry(entry) },
                    onOpenHistory = { entry -> session.openHistoryEntry(entry) },
                    onThemeToggle = {
                        val next = ui.theme.toggled()
                        session.vm.setTheme(next)
                        session.theme = next
                    },
                    onUndo = session.vm::undo,
                    onRedo = session.vm::redo,
                    onAspect = session.vm::setAspect,
                    onPreview = session.vm::previewTransform,
                    onCommit = session.vm::commitTransform,
                    onDrag = { dx, dy ->
                        cardX += dx
                        cardY += dy
                        clampOffsets()
                        rememberOffset()
                        applyLayout()
                    },
                    onResetPosition = {
                        cardX = 0f
                        cardY = 0f
                        rememberOffset()
                        applyLayout()
                    },
                    onSave = { session.saveToGallery() },
                    onCommitEdit = {
                        scope.launch {
                            session.currentRow = null
                            session.autosave(force = true)
                            session.refreshHistory()
                        }
                    },
                    onShare = {
                        // sharing ends the round: the card steps aside so the share sheet
                        // is not covered, and the bubble comes back
                        session.shareNow()
                        onMinimize()
                    },
                    onReset = session.vm::resetTransform,
                    onAlign = session.vm::alignTo,
                    onPadding = session.vm::setPadding,
                    onCorner = session.vm::setCorner,
                    onShadow = session.vm::toggleShadow,
                    onBackground = session.vm::setBackground,
                    onAmbient = session.vm::useAmbientBackground,
                    onScaleMode = session.vm::setScaleMode,
                    onRotatePreview = session.vm::previewRotation,
                    onRotateCommit = session.vm::commitRotation,
                    onResetEdits = {
                        session.vm.resetEdits()
                        haptics.tick()
                    },
                    onResetAll = {
                        session.vm.resetAll()
                        haptics.tick()
                    },
                    onFlipH = session.vm::flipHorizontal,
                    onFlipV = session.vm::flipVertical,
                    onText = session.vm::setText,
                    onTextClear = { session.vm.setText("") },
                    onTextStyle = session.vm::setTextStyle,
                    onTextSize = session.vm::setTextSize,
                    onTextAnchor = session.vm::setTextAnchor,
                    onTextRotationPreview = session.vm::previewTextRotation,
                    onTextRotationCommit = session.vm::commitTextRotation,
                    aiSession = session.ai,
                    aiBase = { session.vm.aiSource() },
                    aiBaseFile = { session.vm.baseFile() },
                    onAiApply = { bmp -> session.applyAi(bmp) },
                    onInfo = { msg ->
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onClearImage = { session.clearCurrent() },
                    onPickImage = onOpenGallery,
                )
            }
        }
    }
}
