package com.emrobinso.floatpic.feature.editor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emrobinso.floatpic.R
import com.emrobinso.floatpic.core.image.ImageLoader
import com.emrobinso.floatpic.data.AiEditClient
import com.emrobinso.floatpic.feature.ai.AiSessionState
import kotlin.math.roundToInt
import com.emrobinso.floatpic.data.DraftStore
import com.emrobinso.floatpic.data.Exporter
import com.emrobinso.floatpic.data.HistoryEntry
import com.emrobinso.floatpic.data.HistoryStore
import com.emrobinso.floatpic.feature.bubble.BubbleService
import com.emrobinso.floatpic.ui.rememberHaptics
import com.emrobinso.floatpic.ui.theme.AppTheme
import com.emrobinso.floatpic.ui.theme.FloatPicTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class EditorActivity : ComponentActivity() {

    private val vm: EditorViewModel by viewModels()

    private var pendingMinimize = false

    /**
     * History row this editor writes to. Autosave re-uses the same id, so one picture never
     * fills the list with duplicates; it is reset when a NEW source is opened.
     */
    private var currentSessionId: String? = null

    /** Most recent theme choice; recorded with the session and kept app-wide. */
    private var sessionTheme: AppTheme = AppTheme.MAROON_DARK

    /** set from setContent so suspend code can publish a new history list */
    private var onHistoryChanged: (List<com.emrobinso.floatpic.data.HistoryEntry>) -> Unit = {}
    private var onHistoryIndexReset: () -> Unit = {}

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val legacyWritePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) saveToGallery(lifecycleScope) else {
                Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
            }
        }

    /** photo picker (API 19+ via backport, no permission needed) */
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) openUri(uri) }

    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            openUri(uri)
        }
    }

    /**
     * Overlay permission. Never lets an OEM activity exception escape (that was the
     * force close): we always fall back to the global settings page, then to a toast.
     */
    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (!pendingMinimize) return@registerForActivityResult
        pendingMinimize = false
        if (BubbleService.canDrawOverlays(this)) minimize(lifecycleScope)
        else Toast.makeText(this, R.string.bubble_permission_denied, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        // never leave the bubble on top of the editor it just opened
        if (BubbleService.isRunning) runCatching { BubbleService.editorShown(this) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (intent?.action == BubbleService.ACTION_FINISH_APP) {
            finishAndRemoveTask()
            return
        }

        if (Build.VERSION.SDK_INT >= 33) {
            runCatching { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }

        val savedTheme = runCatching { DraftStore.theme(this) }.getOrNull()
        if (savedTheme != null) {
            sessionTheme = savedTheme
            vm.setTheme(savedTheme)
        }

        // Any failure while preparing the first picture is shown, never fatal: a missing
        // or undecodable sample used to kill the activity before the first frame.
        try {
            handleIntent(intent)
        } catch (t: Throwable) {
            com.emrobinso.floatpic.data.CrashLog.write(this, t)
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show()
        }

        val screenMaxHeight = resources.displayMetrics.heightPixels /
            resources.displayMetrics.density

        // tell the user (and us) what killed the previous run, then clear it
        com.emrobinso.floatpic.data.CrashLog.take(this)?.let { raw ->
            android.util.Log.e("FloatPicCrash", raw)
            runCatching {
                Toast.makeText(
                    this,
                    "crash sebelumnya: " + com.emrobinso.floatpic.data.CrashLog.summary(raw),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

        runCatching {
            setContent {
                val ui by vm.ui.collectAsState()
            val scope = rememberCoroutineScope()
            var showMinimizePrompt by remember { mutableStateOf(false) }

            // NOTE: no setup sheet here any more. The launcher is HomeActivity now, and it
            // owns the permission + bubble switch; this activity is only the editor.
            // floating card position: dragging the grabber moves the whole card.
            // IMPORTANT: kept as two plain Floats. androidx.compose.ui.geometry.Offset is
            // NOT Bundle-saveable, and putting it in rememberSaveable made the activity
            // throw while composing the very first frame ("aplikasi memiliki bug" on open).
            var cardX by rememberSaveable { mutableFloatStateOf(0f) }
            var cardY by rememberSaveable { mutableFloatStateOf(0f) }
            val cardOffset = Offset(cardX, cardY)
            val uiDensity = LocalDensity.current
            val screenWpx = resources.displayMetrics.widthPixels.toFloat()
            val screenHpx = resources.displayMetrics.heightPixels.toFloat()
            // The card is centred, so the room it has is (screen - card) / 2 on each axis.
            // It used to be a fixed 8 % of the screen, which is why the card could never be
            // pushed to the very top or bottom: it stopped well short of the edge and the
            // whole thing read as "an app with a transparent background". The real measured
            // size now decides the limit, so the card can touch the edge and no further.
            var cardW by remember { mutableFloatStateOf(0f) }
            var cardH by remember { mutableFloatStateOf(0f) }
            val maxShiftX = ((screenWpx - cardW) / 2f).coerceAtLeast(0f)
            val maxShiftY = ((screenHpx - cardH) / 2f).coerceAtLeast(0f)
            var history by remember { mutableStateOf<List<com.emrobinso.floatpic.data.HistoryEntry>>(emptyList()) }
            // which row of `history` is currently open (-1 = a fresh picture)
            var historyIndex by remember { mutableStateOf(-1) }
            val enter by animateFloatAsState(1f, tween(220), label = "enter")
            val ctx = LocalContext.current
            val haptics = rememberHaptics()
            // AI state lives here (not inside the panel) so leaving the AI tool never cancels
            // a running request nor drops a finished result.
            val aiSession = remember { AiSessionState(this@EditorActivity, scope) }

            onHistoryChanged = { list ->
                history = list
                historyIndex = 0
            }
            onHistoryIndexReset = { historyIndex = -1 }
            suspend fun refreshHistory() {
                history = HistoryStore.list(ctx)
                val id = currentSessionId
                historyIndex = if (id == null) -1 else history.indexOfFirst { it.id == id }
            }

            // A newly opened picture (shared from another app or picked) writes its own
            // history row straight away. The bundled sample shown on a cold start is NOT a
            // picture the user chose, so it stays out of history until something is edited.
            LaunchedEffect(ui.loading, vm.source) {
                if (ui.loading || vm.source == null) return@LaunchedEffect
                if (currentSessionId != null) return@LaunchedEffect
                if (isFreshOpen()) return@LaunchedEffect
                delay(400)
                val entry = autosave(force = true)
                if (entry != null) refreshHistory()
            }
            LaunchedEffect(ui.savedTick) { refreshHistory() }
            LaunchedEffect(Unit) { refreshHistory() }

            // ---- AUTOSAVE ------------------------------------------------------------
            // Fires ~1.2 s after the last real edit (not on every gesture frame), and once
            // more when the editor leaves the foreground. The row is updated in place, so
            // a single picture never fills the history with duplicates.
            LaunchedEffect(ui.state, ui.dirty, ui.loading) {
                // the untouched preview must never enter history: no edits = nothing to store
                if (!ui.dirty || ui.loading || vm.source == null || !vm.hasRealEdits()) return@LaunchedEffect
                delay(1200)
                val entry = autosave()
                if (entry != null) refreshHistory()
            }

            FloatPicTheme(theme = ui.theme, dark = false) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .offset { IntOffset(cardOffset.x.roundToInt(), cardOffset.y.roundToInt()) }
                            .graphicsLayer {
                                scaleX = 0.94f + 0.06f * enter
                                scaleY = 0.94f + 0.06f * enter
                                alpha = enter
                            }
                            .fillMaxWidth(0.80f)
                            // hug the content instead of a fixed 70% block: the tall empty
                            // band above the preview was exactly this fixed height.
                            .wrapContentHeight(Alignment.CenterVertically)
                            .heightIn(max = (screenMaxHeight * 0.82f).dp)
                            .onSizeChanged { cardW = it.width.toFloat(); cardH = it.height.toFloat() }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                    ) {
                        Column {
                        EditorScreen(
                            state = ui.state,
                            bitmap = vm.source,
                            theme = ui.theme,
                            loading = ui.loading,
                            error = ui.error,
                            canUndo = ui.canUndo,
                            canRedo = ui.canRedo,
                            history = history,
                            thumbPath = { entry -> HistoryStore.filesOf(ctx, entry).thumb.absolutePath },
                            haptics = haptics,
                            modifier = Modifier.fillMaxSize(),
                            // closing the card is also a save point: write the session
                            // first (non-cancellable), then leave — otherwise the row you
                            // just edited disappears from history
                            onClose = {
                                if (vm.isDirty() && vm.hasRealEdits()) {
                                    lifecycleScope.launch {
                                        withContext(NonCancellable) { autosave(force = true) }
                                        finish()
                                    }
                                } else {
                                    finish()
                                }
                            },
                            onMinimize = { showMinimizePrompt = true },
                            onDeleteHistory = { entry ->
                                scope.launch {
                                    HistoryStore.delete(ctx, entry.id)
                                    refreshHistory()
                                }
                            },
                            historyCurrent = historyIndex,
                            onStepHistory = { entry ->
                                val snapshot = HistoryStore.snapshotOf(ctx, entry)
                                if (snapshot == null) {
                                    Toast.makeText(this@EditorActivity, R.string.open_failed, Toast.LENGTH_SHORT).show()
                                } else {
                                    lifecycleScope.launch {
                                        val file = HistoryStore.filesOf(ctx, entry).source
                                        vm.openAndRestore(snapshot) {
                                            ImageLoader.loadFile(this@EditorActivity, file)
                                        }
                                        vm.source?.let { vm.useBase(it, file) }
                                        currentSessionId = entry.id
                                        historyIndex = history.indexOfFirst { it.id == entry.id }
                                    }
                                }
                            },
                            onOpenHistory = { entry ->
                                val snapshot = HistoryStore.snapshotOf(ctx, entry)
                                if (snapshot == null) {
                                    Toast.makeText(this@EditorActivity, R.string.open_failed, Toast.LENGTH_SHORT).show()
                                } else {
                                    lifecycleScope.launch {
                                        val file = HistoryStore.filesOf(ctx, entry).source
                                        // suspend: finishes only after the new source is in,
                                        // otherwise loadImage() resets the editor to defaults
                                        // right after we restored it.
                                        vm.openAndRestore(snapshot) {
                                            ImageLoader.loadFile(this@EditorActivity, file)
                                        }
                                        // the reopened picture becomes the base AI edits from
                                        vm.source?.let { vm.useBase(it, file) }
                                        // reopening an entry keeps writing to the same row
                                        currentSessionId = entry.id
                                        historyIndex = history.indexOfFirst { it.id == entry.id }
                                    }
                                }
                            },
                            onThemeToggle = {
                                val next = ui.theme.toggled()
                                vm.setTheme(next)
                                sessionTheme = next
                                scope.launch { DraftStore.setTheme(this@EditorActivity, next) }
                            },
                            onUndo = vm::undo,
                            onRedo = vm::redo,
                            onAspect = vm::setAspect,
                            onPreview = vm::previewTransform,
                            onCommit = vm::commitTransform,
                            onDrag = { dragX, dragY ->
                                // clamp against the measured card, so the edges of the card
                                // land exactly on the edges of the screen
                                cardX = (cardX + dragX).coerceIn(-maxShiftX, maxShiftX)
                                cardY = (cardY + dragY).coerceIn(-maxShiftY, maxShiftY)
                            },
                            onSave = { saveToGallery(scope) },
                            onCommitEdit = {
                                scope.launch {
                                    // one row per deliberate save-as-result, so the edited
                                    // picture becomes its own thing to reopen
                                    currentSessionId = null
                                    val entry = autosave(force = true)
                                    refreshHistory()
                                    Toast.makeText(
                                        this@EditorActivity,
                                        if (entry != null) R.string.commit_edit_done else R.string.commit_edit_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            onShare = { shareNow() },
                            onReset = vm::resetTransform,
                            onAlign = vm::alignTo,
                            onPadding = vm::setPadding,
                            onCorner = vm::setCorner,
                            onShadow = vm::toggleShadow,
                            onBackground = vm::setBackground,
                            onAmbient = vm::useAmbientBackground,
                            onScaleMode = vm::setScaleMode,
                            onRotatePreview = vm::previewRotation,
                            onRotateCommit = vm::commitRotation,
                            onResetEdits = {
                                vm.resetEdits()
                                haptics.tick()
                                Toast.makeText(this@EditorActivity, R.string.reset_edits_done, Toast.LENGTH_SHORT).show()
                            },
                            onResetAll = {
                                vm.resetAll()
                                haptics.tick()
                                Toast.makeText(this@EditorActivity, R.string.reset_done, Toast.LENGTH_SHORT).show()
                            },
                            onFlipH = vm::flipHorizontal,
                            onFlipV = vm::flipVertical,
                            onText = vm::setText,
                            onTextStyle = vm::setTextStyle,
                            onTextSize = vm::setTextSize,
                            onTextAnchor = vm::setTextAnchor,
                            onTextRotationPreview = vm::previewTextRotation,
                            onTextRotationCommit = vm::commitTextRotation,
                            aiSession = aiSession,
                            aiBase = { vm.aiSource() },
                            aiBaseFile = { vm.baseFile() },
                            onAiApply = { bmp ->
                                // last AI result wins: it becomes the working picture, and
                                // undo takes you straight back to the previous one.
                                val file = AiEditClient.persist(this@EditorActivity, bmp)
                                vm.replaceSource(bmp, file)
                                currentSessionId = null
                                // EVERY AI result gets its own history row. The renderer draws
                                // the remaining edits (text, frame, ratio…) on top, so what is
                                // stored is the finished picture, not a separate ingredient.
                                scope.launch {
                                    val entry = autosave(force = true)
                                    refreshHistory()
                                    if (entry == null) {
                                        Toast.makeText(
                                            this@EditorActivity,
                                            R.string.commit_edit_failed,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            },
                            onInfo = { msg ->
                                Toast.makeText(this@EditorActivity, msg, Toast.LENGTH_SHORT).show()
                            },
                            onClearImage = {
                                vm.clearSession()
                                currentSessionId = null
                                onHistoryIndexReset()
                            },
                            onPickImage = {
                                runCatching {
                                    pickImage.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                }.onFailure { openDocument.launch(arrayOf("image/*")) }
                            },
                        )
                        }
                    }

                    if (showMinimizePrompt) {
                        MinimizeDialog(
                            title = ctx.getString(R.string.bubble_permission_title),
                            body = ctx.getString(R.string.bubble_permission_body),
                            confirm = ctx.getString(R.string.bubble_permission_grant),
                            later = ctx.getString(R.string.bubble_permission_later),
                            onDismiss = { showMinimizePrompt = false },
                            onGrant = {
                                showMinimizePrompt = false
                                requestOverlayThenMinimize(scope)
                            },
                        )
                    }
                }

                    LaunchedEffect(ui.savedTick) {
                        if (ui.savedTick > 0) {
                            Toast.makeText(this@EditorActivity, R.string.saved_toast, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }.onFailure { t ->
            // last resort: show the reason instead of a blank translucent window
            com.emrobinso.floatpic.data.CrashLog.write(this, t)
            android.util.Log.e("FloatPicCrash", "setContent failed", t)
            Toast.makeText(
                this,
                "UI gagal dimuat: " + (t.message ?: t::class.java.simpleName),
                Toast.LENGTH_LONG,
            ).show()
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        // last-chance autosave: covers "user just closed the card / swiped away"
        // Only real edits are worth a history row; the untouched preview is not.
        if (vm.isDirty() && vm.hasRealEdits()) {
            lifecycleScope.launch { withContext(NonCancellable) { autosave(force = true) } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == BubbleService.ACTION_FINISH_APP) {
            finishAndRemoveTask()
            return
        }
        // Any failure while preparing the first picture is shown, never fatal: a missing
        // or undecodable sample used to kill the activity before the first frame.
        try {
            handleIntent(intent)
        } catch (t: Throwable) {
            com.emrobinso.floatpic.data.CrashLog.write(this, t)
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val sessionId = intent.getStringExtra(EXTRA_RESTORE_SESSION)
        if (sessionId != null) {
            // the bubble opened the editor: hide the bubble view but KEEP the service, so it
            // returns immediately on minimize (this used to stop the service for good, which
            // is why "floating" died after one round-trip)
            runCatching { BubbleService.editorShown(this) }
            restoreSession(sessionId)
            return
        }

        val action = intent.action
        val uri = when (action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_VIEW, Intent.ACTION_EDIT ->
                ShareIntents.singleUri(intent) ?: ShareIntents.multiUris(intent).firstOrNull()
            else -> null
        }

        if (uri != null) {
            openUri(uri)
        } else if (action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE) {
            Toast.makeText(this, R.string.no_image, Toast.LENGTH_SHORT).show()
        } else {
            // Cold start from the launcher: an EMPTY preview. The user adds a picture with
            // the "+" shortcut (or by sharing one in). The bundled sample used to appear here
            // and then sneak into history, which the user explicitly did not want.
        }
    }

    private fun openUri(uri: Uri) {
        // a new picture starts its own history row (autosave then updates that row in place)
        currentSessionId = null
        vm.loadImage { ImageLoader.load(this, uri) }
        onHistoryIndexReset()
    }

    private fun restoreSession(sessionId: String) {
        lifecycleScope.launch {
            val snapshot = DraftStore.load(this@EditorActivity, sessionId) ?: return@launch
            currentSessionId = snapshot.historyId
            val file = snapshot.sourceUri?.let { File(it) }
            if (file != null && file.exists()) {
                vm.openAndRestore(snapshot) { ImageLoader.loadFile(this@EditorActivity, file) }
            } else {
                vm.restore(snapshot)
            }
        }
    }

    private fun requestOverlayThenMinimize(scope: CoroutineScope) {
        if (BubbleService.canDrawOverlays(this)) {
            minimize(scope)
            return
        }
        pendingMinimize = true
        val launched = runCatching {
            overlayLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            )
        }.isSuccess

        if (!launched) {
            val globalLaunched = runCatching {
                overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }.isSuccess
            if (!globalLaunched) {
                pendingMinimize = false
                Toast.makeText(this, R.string.bubble_permission_denied, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun minimize(scope: CoroutineScope) {
        val bmp = vm.source
        if (bmp == null) {
            // no picture yet: the bubble still makes sense as a show/hide toggle
            BubbleService.startEmpty(this)
            finishAndRemoveTask()
            return
        }
        scope.launch {
            // record first, so the bubble session and the history row share one id
            runCatching { autosave(force = true) }
            val sessionId = "session_${System.currentTimeMillis()}"
            val thumb = vm.bubbleThumb()
            val thumbUri = thumb?.let { runCatching { Exporter.writeThumb(this@EditorActivity, it) }.getOrNull() }
            // The launcher sample never had a cache copy, so minimizing used to fail with
            // "gagal menyimpan gambar" and no bubble ever appeared. Materialize one.
            val file = Exporter.ensureSourceFile(this@EditorActivity, bmp, vm.sourceFile)
            if (file != null) {
                vm.attachSourceFile(file)
                val source = file.absolutePath
                DraftStore.save(
                    this@EditorActivity,
                    sessionId,
                    DraftSnapshot(
                        sourceUri = source,
                        state = vm.currentState(),
                        theme = sessionTheme,
                        historyId = currentSessionId,
                    ),
                )
                BubbleService.start(this@EditorActivity, sessionId, thumbUri)
                finishAndRemoveTask()
            } else {
                Toast.makeText(this@EditorActivity, R.string.save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Writes the current session into the history. Re-uses [currentSessionId] so the list
     * shows one row per picture, not one row per save.
     */
    private suspend fun autosave(force: Boolean = false): HistoryEntry? = withContext(NonCancellable) {
        val bmp = vm.source ?: return@withContext null
        // A brand new SHARED picture gets one row even before any edit. The launcher sample
        // (default preview) does not: it only appears once it has been edited.
        val firstRow = currentSessionId == null && !isFreshOpen()
        if (!firstRow && !vm.hasRealEdits()) return@withContext null
        // What the row shows must be what the user sees: the edited picture. The state is
        // stored alongside it, so reopening a row restores the same look (and AI still gets
        // the untouched base from vm.aiSource()).
        val rowBitmap = renderCurrent() ?: vm.baseForHistory() ?: bmp
        if (!force && !vm.isDirty()) return@withContext null
        val entry = HistoryStore.record(
            context = this@EditorActivity,
            bitmap = rowBitmap,
            state = vm.currentState(),
            theme = sessionTheme,
            sourceFile = vm.sourceFile,
            sessionId = currentSessionId,
        )
        if (entry != null) {
            currentSessionId = entry.id
            vm.markSaved()
        }
        entry
    }

    private suspend fun recordHistory() {
        autosave(force = true)
    }

    /**
     * Records the picture that was just shared/saved as its own history row. It must be the
     * SAME bytes the user sent out (no second render, no drift), so the bitmap is written
     * straight to the row instead of being re-rendered from the state.
     */
    private suspend fun recordRendered(rendered: android.graphics.Bitmap): HistoryEntry? =
        withContext(NonCancellable) {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = java.io.File(filesDir, "rendered").apply { mkdirs() }
                    val out = java.io.File(dir, "out_${System.currentTimeMillis()}.jpg")
                    out.outputStream().use {
                        rendered.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, it)
                    }
                    out
                }.getOrNull()
            }
            val entry = HistoryStore.record(
                context = this@EditorActivity,
                bitmap = rendered,
                state = vm.currentState(),
                theme = sessionTheme,
                sourceFile = file,
                sessionId = null,
            )
            if (entry != null) {
                currentSessionId = entry.id
                vm.markSaved()
                onHistoryChanged(HistoryStore.list(this@EditorActivity))
            }
            entry
        }

    /** True when this Activity was opened by "+" in the app (no shared picture). */
    private fun isFreshOpen(): Boolean {
        val i = intent ?: return true
        if (i.getStringExtra(EXTRA_RESTORE_SESSION) != null) return false
        return when (i.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_VIEW, Intent.ACTION_EDIT -> false
            else -> ShareIntents.singleUri(i) == null
        }
    }

    private fun renderCurrent(): Bitmap? {
        val bmp = vm.source ?: return null
        return runCatching { Exporter.render(this, bmp, vm.currentState(), vm.orientation) }.getOrNull()
    }

    private fun saveToGallery(scope: CoroutineScope) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            legacyWritePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        scope.launch {
            val out = renderCurrent()
            if (out == null) {
                Toast.makeText(this@EditorActivity, R.string.save_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val saved = runCatching { Exporter.saveToGallery(this@EditorActivity, out, png = false) }
                .onSuccess { vm.reportSaved() }
                .onFailure {
                    Toast.makeText(this@EditorActivity, R.string.save_failed, Toast.LENGTH_SHORT).show()
                }
                .isSuccess
            if (saved) {
                // the picture the user just saved becomes a history row of its own, and that
                // row stores exactly this rendered file (not a second render)
                recordRendered(out)
            }
            out.recycle()
        }
    }

    private fun shareNow() {
        val out = renderCurrent()
        if (out == null) {
            Toast.makeText(this, R.string.share_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = runCatching { Exporter.writeShareCopy(this, out) }.getOrNull()
        // shared picture = a history row holding the rendered file. The bitmap is recycled
        // inside the coroutine, after it was written.
        lifecycleScope.launch {
            recordRendered(out)
            runCatching { out.recycle() }
        }
        if (uri == null) {
            Toast.makeText(this, R.string.share_failed, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            startActivity(
                Intent.createChooser(ShareIntents.shareImage(this, uri), getString(R.string.action_share)),
            )
        }.onFailure {
            Toast.makeText(this, R.string.share_failed, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun MinimizeDialog(
    title: String,
    body: String,
    confirm: String,
    later: String,
    onDismiss: () -> Unit,
    onGrant: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(Modifier.padding(22.dp)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = body,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(later) }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = onGrant,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) { Text(confirm) }
                }
            }
        }
    }
}
