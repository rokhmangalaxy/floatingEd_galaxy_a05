package com.emrobinso.floatpic.feature.bubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emrobinso.floatpic.R
import com.emrobinso.floatpic.core.image.ImageLoader
import com.emrobinso.floatpic.data.DraftStore
import com.emrobinso.floatpic.feature.editor.EditorActivity
import com.emrobinso.floatpic.feature.editor.ShareIntents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Bubble launcher (docs/13): the editor stays an Activity; this service keeps a small
 * draggable handle so the session can be resumed. Hold 1.6s -> haptics + close app.
 */
class BubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var thumb: android.graphics.Bitmap? = null
    private var sessionId: String? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var idleJob: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> view?.visibility = View.GONE
                Intent.ACTION_SCREEN_ON -> view?.visibility = View.VISIBLE
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
        )
        startForegroundWithNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CLOSE -> {
                closeApp()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_NOTIF -> startForegroundWithNotification()
        }
        intent?.getStringExtra(EXTRA_SESSION)?.let { sessionId = it }
        intent?.getStringExtra(EXTRA_THUMB_URI)?.let { loadThumb(Uri.parse(it)) }
        showBubble()
        return START_STICKY
    }

    private fun loadThumb(uri: Uri) {
        scope.launch {
            runCatching {
                ImageLoader.load(this@BubbleService, uri, targetMax = 256)
            }.getOrNull()?.let { loaded ->
                thumb?.recycle()
                thumb = loaded.bitmap
                view?.setContent { BubbleContentRoot() }
            }
        }
    }

    private fun startForegroundWithNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.bubble_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = getString(R.string.bubble_channel_desc) },
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, restoreIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val close = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, BubbleCloseReceiver::class.java).setAction(ACTION_CLOSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle(getString(R.string.bubble_notif_title))
            .setContentText(getString(R.string.bubble_notif_text))
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    null, getString(R.string.bubble_notif_close), close,
                ).build(),
            )
            .setOngoing(true)
            .setShowWhen(false)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun restoreIntent(): Intent {
        val id = sessionId ?: return Intent(this, EditorActivity::class.java)
        return ShareIntents.editorIntentForDraft(this, id)
    }

    private fun showBubble() {
        if (view != null) return
        val dm = resources.displayMetrics
        val sizePx = dp(64)
        val p = WindowManager.LayoutParams(
            sizePx, sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels - sizePx + dp(8)
            y = dm.heightPixels / 2
        }
        params = p
        val compose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { BubbleContentRoot() }
        }
        view = compose
        runCatching { windowManager.addView(compose, p) }.onFailure { stopSelf() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @Composable
    private fun BubbleContentRoot() {
        val localView = LocalView.current
        fun haptic(kind: Int) = localView.performHapticFeedback(kind)

        BubbleContent(
            thumb = thumb,
            onDragStart = { scheduleIdle() },
            onDrag = { dx, dy -> dragBy(dx, dy) },
            onDragEnd = { snapToEdge() },
            onTap = {
                runCatching {
                    startActivity(restoreIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            onHoldTick = { p ->
                BubbleState.progress.floatValue = p
                if (p >= 0.30f && p < 0.36f) haptic(android.view.HapticFeedbackConstants.CLOCK_TICK)
                if (p >= 0.72f && p < 0.78f) haptic(android.view.HapticFeedbackConstants.CLOCK_TICK)
            },
            onClose = {
                haptic(android.view.HapticFeedbackConstants.LONG_PRESS)
                closeApp()
            },
        )
    }

    private fun dragBy(dx: Float, dy: Float) {
        val p = params ?: return
        val v = view ?: return
        val dm = resources.displayMetrics
        p.x = (p.x + dx.toInt()).coerceIn(-dp(10), dm.widthPixels - dp(20))
        p.y = (p.y + dy.toInt()).coerceIn(dp(24), dm.heightPixels - dp(120))
        runCatching { windowManager.updateViewLayout(v, p) }
        scheduleIdle()
    }

    private fun snapToEdge() {
        val p = params ?: return
        val v = view ?: return
        val dm = resources.displayMetrics
        p.x = if (p.x + p.width / 2 < dm.widthPixels / 2) -dp(20) else dm.widthPixels - p.width + dp(20)
        runCatching { windowManager.updateViewLayout(v, p) }
        scheduleIdle()
    }

    private fun scheduleIdle() {
        BubbleState.reduce.value = false
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(3000)
            BubbleState.reduce.value = true
        }
    }

    private fun closeApp() {
        BubbleState.progress.floatValue = 0f
        runCatching { if (view != null) windowManager.removeView(view) }
        view = null
        val id = sessionId
        scope.launch { DraftStore.clear(this@BubbleService, id) }
        // Ask the editor (if still alive) to finish, then stop the service.
        runCatching {
            startActivity(
                Intent(this, EditorActivity::class.java).apply {
                    action = ACTION_FINISH_APP
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                },
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        idleJob?.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { if (view != null) windowManager.removeView(view) }
        view = null
        thumb?.recycle()
        thumb = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_CLOSE = "com.emrobinso.floatpic.bubble.CLOSE"
        const val ACTION_FINISH_APP = "com.emrobinso.floatpic.FINISH_APP"
        const val ACTION_UPDATE_NOTIF = "com.emrobinso.floatpic.bubble.UPDATE_NOTIF"
        const val ACTION_RESTORE = "com.emrobinso.floatpic.bubble.RESTORE"
        const val EXTRA_SESSION = "bubble.session"
        const val EXTRA_THUMB_URI = "bubble.thumb"
        private const val CHANNEL_ID = "floatpic_bubble"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context, sessionId: String, thumbUri: Uri?) {
            val intent = Intent(context, BubbleService::class.java).apply {
                putExtra(EXTRA_SESSION, sessionId)
                putExtra(EXTRA_THUMB_URI, thumbUri?.toString())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateNotification(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, BubbleService::class.java).setAction(ACTION_UPDATE_NOTIF),
                )
            }
        }

        fun canDrawOverlays(context: Context): Boolean =
            android.provider.Settings.canDrawOverlays(context)
    }
}

/** UI state shared between the service and the bubble composable. */
object BubbleState {
    val reduce = mutableStateOf(false)
    val progress = androidx.compose.runtime.mutableFloatStateOf(0f)
}

@Composable
private fun BubbleContent(
    thumb: android.graphics.Bitmap?,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit,
    onHoldTick: (Float) -> Unit,
    onClose: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val progress = BubbleState.progress.floatValue
    val reduce = BubbleState.reduce.value
    val scale by animateFloatAsState(
        targetValue = if (reduce && !pressed) 0.78f else 1f,
        label = "bubbleScale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .scale(scale)
            .bubbleGestures(
                onPressChange = { pressed = it },
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onTap = onTap,
                onHoldTick = onHoldTick,
                onClose = onClose,
            ),
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val inset = 3.dp.toPx()
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                val r = min(size.width, size.height) / 2f - inset
                // drop shadow
                paint.color = android.graphics.Color.argb(60, 0, 0, 0)
                canvas.nativeCanvas.drawCircle(
                    size.width / 2f, size.height / 2f + 2.dp.toPx() * 2f, r, paint,
                )
                paint.color = android.graphics.Color.parseColor("#FFF9F7")
                canvas.nativeCanvas.drawCircle(size.width / 2f, size.height / 2f, r, paint)
            }
            if (progress > 0f) {
                drawArc(
                    color = Color(0xFF7A0C2E),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
        if (thumb != null) {
            val img = remember(thumb) { thumb.asImageBitmap() }
            Image(
                bitmap = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(9.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(9.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF7A0C2E)),
                contentAlignment = Alignment.Center,
            ) {
                Text("✦", color = Color(0xFFFFF9F7), fontSize = 20.sp)
            }
        }
        if (progress > 0f) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Text(
                    text = getStringStatic(R.string.bubble_hold_hint),
                    color = Color(0xFFFFF9F7),
                    fontSize = 6.sp,
                    lineHeight = 6.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .background(Color(0xE67A0C2E), RoundedCornerShape(3.dp))
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun getStringStatic(id: Int): String =
    androidx.compose.ui.platform.LocalContext.current.getString(id)

/**
 * Gesture contract:
 *  - move > slop  -> drag (hold cancelled)
 *  - hold 1.6 s   -> tick haptics at 30% / 72% -> close
 *  - release      -> tap (open editor), unless close already fired
 */
private fun Modifier.bubbleGestures(
    onPressChange: (Boolean) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit,
    onHoldTick: (Float) -> Unit,
    onClose: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onPressChange(true)
        var dragging = false
        var closed = false
        val slop = 12.dp.toPx()
        coroutineScope {
            val holdJob = launch {
                var elapsed = 0L
                while (elapsed < HOLD_MS) {
                    delay(40)
                    elapsed += 40
                    onHoldTick((elapsed.toFloat() / HOLD_MS).coerceIn(0f, 1f))
                }
                closed = true
                onHoldTick(0f)
                onClose()
            }
            while (!closed) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) break
                val travel = (change.position - down.position).getDistance()
                if (!dragging && travel > slop) {
                    dragging = true
                    holdJob.cancel()
                    onHoldTick(0f)
                    onDragStart()
                }
                if (dragging) {
                    val delta = change.positionChange()
                    if (delta.x != 0f || delta.y != 0f) onDrag(delta.x, delta.y)
                    change.consume()
                }
            }
            holdJob.cancelAndJoin()
            onPressChange(false)
            if (!closed) {
                if (dragging) {
                    onDragEnd()
                } else {
                    onHoldTick(0f)
                    onTap()
                }
            }
        }
    }
}

private const val HOLD_MS = 1600L
