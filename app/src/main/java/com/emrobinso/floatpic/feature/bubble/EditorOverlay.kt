package com.emrobinso.floatpic.feature.bubble

import android.content.Context
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.emrobinso.floatpic.data.HistoryStore
import com.emrobinso.floatpic.feature.editor.EditorScreen
import com.emrobinso.floatpic.ui.rememberHaptics
import com.emrobinso.floatpic.ui.theme.FloatPicTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
 * The editor as a REAL system overlay: a full-screen window of type APPLICATION_OVERLAY that
 * carries the same [EditorScreen] the Activity used to show, with the floating card centred
 * inside it and the rest of the window transparent so the app behind stays visible.
 */
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

    val isShowing: Boolean get() = view != null

    fun show() {
        if (view != null) return
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
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
            x = 0
            y = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        params = p
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

    fun updateLayout() {
        val p = params ?: return
        val v = view ?: return
        runCatching { windowManager.updateViewLayout(v, p) }
    }

    /** The overlay fills the screen; the card is centred with a small dim around it. */
    @Composable
    private fun EditorOverlayContent() {
        val ui by session.vm.ui.collectAsState()
        val scope = rememberCoroutineScope()
        val haptics = rememberHaptics()
        var cardX by remember { mutableFloatStateOf(0f) }
        var cardY by remember { mutableFloatStateOf(0f) }
        var cardW by remember { mutableFloatStateOf(0f) }
        var cardH by remember { mutableFloatStateOf(0f) }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val metrics = remember { context.resources.displayMetrics }
        val screenWpx = metrics.widthPixels.toFloat()
        val screenHpx = metrics.heightPixels.toFloat()
        val maxShiftX = ((screenWpx - cardW) / 2f).coerceAtLeast(0f)
        val maxShiftY = ((screenHpx - cardH) / 2f).coerceAtLeast(0f)

        // autosave: 1.2 s after the last real edit
        androidx.compose.runtime.LaunchedEffect(ui.state, ui.dirty) {
            if (!ui.dirty || !session.vm.hasRealEdits()) return@LaunchedEffect
            kotlinx.coroutines.delay(1200)
            session.autosave()
        }

        FloatPicTheme(theme = ui.theme, dark = false) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .22f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .offset { IntOffset(cardX.roundToInt(), cardY.roundToInt()) }
                        .fillMaxWidth(0.80f)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .heightIn(max = (screenHpx / density.density * 0.82f).dp)
                        .onSizeChanged { cardW = it.width.toFloat(); cardH = it.height.toFloat() }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
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
                            cardX = (cardX + dx).coerceIn(-maxShiftX, maxShiftX)
                            cardY = (cardY + dy).coerceIn(-maxShiftY, maxShiftY)
                        },
                        onSave = { session.saveToGallery() },
                        onCommitEdit = {
                            scope.launch {
                                session.currentRow = null
                                session.autosave(force = true)
                                session.refreshHistory()
                            }
                        },
                        onShare = { session.shareNow() },
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
}
