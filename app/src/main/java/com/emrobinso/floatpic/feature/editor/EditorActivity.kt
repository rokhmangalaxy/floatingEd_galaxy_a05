package com.emrobinso.floatpic.feature.editor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emrobinso.floatpic.R
import com.emrobinso.floatpic.core.image.ImageLoader
import com.emrobinso.floatpic.core.image.LoadedImage
import com.emrobinso.floatpic.data.DraftStore
import com.emrobinso.floatpic.data.Exporter
import com.emrobinso.floatpic.feature.bubble.BubbleService
import com.emrobinso.floatpic.ui.rememberHaptics
import com.emrobinso.floatpic.ui.theme.AppTheme
import com.emrobinso.floatpic.ui.theme.FloatPicTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class EditorActivity : ComponentActivity() {

    private val vm: EditorViewModel by viewModels()

    private var pendingMinimize = false

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val legacyWritePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) saveToGallery(lifecycleScope) else {
                Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
            }
        }

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (!pendingMinimize) return@registerForActivityResult
        pendingMinimize = false
        if (BubbleService.canDrawOverlays(this)) {
            minimize(lifecycleScope)
        } else {
            Toast.makeText(this, R.string.bubble_permission_denied, Toast.LENGTH_SHORT).show()
        }
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

        // restore the last used theme synchronously: avoids a flash of the wrong theme
        val savedTheme = runBlocking { runCatching { DraftStore.theme(this@EditorActivity) }.getOrNull() }
        if (savedTheme != null) vm.setTheme(savedTheme)

        handleIntent(intent)

        setContent {
            val ui by vm.ui.collectAsState()
            val scope = rememberCoroutineScope()
            var showMinimizePrompt by remember { mutableStateOf(false) }
            val enter by animateFloatAsState(1f, tween(220), label = "enter")
            val ctx = LocalContext.current

            FloatPicTheme(theme = ui.theme, dark = false) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.42f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { if (!ui.loading) finish() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val maxH = (resources.displayMetrics.heightPixels /
                        resources.displayMetrics.density * 0.9f).dp
                    Box(
                        Modifier
                            .graphicsLayer {
                                scaleX = 0.94f + 0.06f * enter
                                scaleY = 0.94f + 0.06f * enter
                                alpha = enter
                            }
                            .navigationBarsPadding()
                            .padding(vertical = 10.dp)
                            .fillMaxWidth(0.97f)
                            .height(maxH)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                    ) {
                        EditorScreen(
                            state = ui.state,
                            bitmap = vm.source,
                            theme = ui.theme,
                            loading = ui.loading,
                            error = ui.error,
                            canUndo = ui.canUndo,
                            canRedo = ui.canRedo,
                            haptics = rememberHaptics(),
                            modifier = Modifier.fillMaxSize(),
                            onClose = { finish() },
                            onMinimize = { showMinimizePrompt = true },
                            onThemeToggle = {
                                val next = if (ui.theme == AppTheme.PASTEL_MAROON)
                                    AppTheme.NEUMORPHISM else AppTheme.PASTEL_MAROON
                                vm.setTheme(next)
                                scope.launch { DraftStore.setTheme(this@EditorActivity, next) }
                            },
                            onUndo = vm::undo,
                            onRedo = vm::redo,
                            onAspect = vm::setAspect,
                            onPreview = vm::previewTransform,
                            onCommit = vm::commitTransform,
                            onSave = { saveToGallery(scope) },
                            onShare = { shareNow() },
                            onReset = vm::resetTransform,
                            onAlign = vm::alignTo,
                            onPadding = vm::setPadding,
                            onCorner = vm::setCorner,
                            onShadow = vm::toggleShadow,
                            onBackground = vm::setBackground,
                            onAmbient = vm::useAmbientBackground,
                            onScaleMode = vm::setScaleMode,
                            onRotate = vm::rotateBy,
                            onFlipH = vm::flipHorizontal,
                            onFlipV = vm::flipVertical,
                            onText = vm::setText,
                            onTextPosition = vm::setTextPosition,
                            onTextStyle = vm::setTextStyle,
                            onTextSize = vm::setTextSize,
                        )
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == BubbleService.ACTION_FINISH_APP) {
            finishAndRemoveTask()
            return
        }
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val sessionId = intent.getStringExtra(EXTRA_RESTORE_SESSION)
        if (sessionId != null) {
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
            vm.loadImage { ImageLoader.load(this, uri) }
        } else if (action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE) {
            Toast.makeText(this, R.string.no_image, Toast.LENGTH_SHORT).show()
        } else {
            // cold start from the launcher: bundled sample keeps the editor explorable
            vm.loadImage {
                val bmp = android.graphics.BitmapFactory.decodeStream(assets.open("sample_photo.jpg"))
                LoadedImage(bmp, 0)
            }
        }
    }

    private fun restoreSession(sessionId: String) {
        lifecycleScope.launch {
            val snapshot = DraftStore.load(this@EditorActivity, sessionId) ?: return@launch
            val uri = snapshot.sourceUri?.let { Uri.parse(it) }
            if (uri != null) {
                vm.loadImage {
                    runCatching { ImageLoader.load(this@EditorActivity, uri) }.getOrElse {
                        val bmp = android.graphics.BitmapFactory.decodeStream(
                            assets.open("sample_photo.jpg"),
                        )
                        LoadedImage(bmp, 0)
                    }
                }
            }
            vm.restore(snapshot)
        }
    }

    private fun requestOverlayThenMinimize(scope: CoroutineScope) {
        if (BubbleService.canDrawOverlays(this)) {
            minimize(scope)
            return
        }
        pendingMinimize = true
        overlayLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun minimize(scope: CoroutineScope) {
        val bmp = vm.source
        if (bmp == null) {
            finish()
            return
        }
        scope.launch {
            val sessionId = "session_${System.currentTimeMillis()}"
            val shareCopy = runCatching { Exporter.writeShareCopy(this@EditorActivity, bmp) }.getOrNull()
            val thumb = vm.bubbleThumb()
            val thumbUri = thumb?.let { runCatching { Exporter.writeThumb(this@EditorActivity, it) }.getOrNull() }
            DraftStore.save(
                this@EditorActivity,
                sessionId,
                DraftSnapshot(
                    sourceUri = shareCopy?.toString(),
                    state = vm.currentState(),
                    theme = vm.currentTheme(),
                ),
            )
            BubbleService.start(this@EditorActivity, sessionId, thumbUri)
            finishAndRemoveTask()
        }
    }

    private fun renderCurrent(): android.graphics.Bitmap? {
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
            runCatching { Exporter.saveToGallery(this@EditorActivity, out, png = false) }
                .onSuccess { vm.reportSaved() }
                .onFailure {
                    Toast.makeText(this@EditorActivity, R.string.save_failed, Toast.LENGTH_SHORT).show()
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
        out.recycle()
        if (uri == null) {
            Toast.makeText(this, R.string.share_failed, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent.createChooser(ShareIntents.shareImage(this, uri), getString(R.string.action_share)),
        )
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
                .fillMaxWidth(0.86f)
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
