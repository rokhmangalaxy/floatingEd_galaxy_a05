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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import com.emrobinso.floatpic.R
import com.emrobinso.floatpic.core.image.ImageLoader
import com.emrobinso.floatpic.data.DraftStore
import com.emrobinso.floatpic.feature.editor.ShareIntents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Bubble launcher (docs/13): the editor stays an Activity; this service keeps a small
 * draggable handle so the session can be resumed. Hold 1.6s -> haptics + close app.
 */
class BubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var view: ImageView? = null
    private var params: WindowManager.LayoutParams? = null
    private var thumb: android.graphics.Bitmap? = null
    private var sessionId: String? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var idleJob: Job? = null
    private var holdJob: Job? = null
    private var holdProgress = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var sessionRef: EditorSessionState? = null
    private var editor: EditorOverlay? = null

    private val prefs by lazy { getSharedPreferences("floatpic_bubble", Context.MODE_PRIVATE) }

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
        // A rejected FGS notification (Android 13+ blocked notifications, OEM policy) used
        // to throw here and the service died before the bubble could ever be added.
        BubbleState.markRunning(true)
        runCatching { startForegroundWithNotification() }
            .onFailure { stopSelfSafely() }
    }

    /** Stops the service without letting a second foreground/FGS exception escape. */
    private fun stopSelfSafely() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { stopSelf() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CLOSE -> {
                closeApp()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_EDITOR -> {
                toggleEditor()
                return START_STICKY
            }
            ACTION_SHOW_EDITOR -> {
                showEditor(intent.getStringExtra(EXTRA_SESSION))
                return START_STICKY
            }
            ACTION_OPEN_URI -> {
                val uri = intent.data
                if (uri != null) {
                    showEditor(null)
                    sessionRef?.openUri(uri)
                }
                return START_STICKY
            }
            ACTION_EDITOR_SHOWN -> {
                // the editor is on screen: drop the bubble but KEEP the service, so the next
                // minimize (or a tap after the user closes the editor) is instant
                dropView()
                return START_STICKY
            }
            ACTION_HIDE -> {
                if (sessionId == null) {
                    hideBubble()
                    return START_NOT_STICKY
                }
                dropView()
                return START_STICKY
            }
            ACTION_UPDATE_NOTIF -> {
                runCatching { startForegroundWithNotification() }
                return START_STICKY
            }
        }

        intent?.getStringExtra(EXTRA_SESSION)?.let { sessionId = it }
        intent?.getStringExtra(EXTRA_THUMB_URI)?.let { uri -> loadThumb(Uri.parse(uri)) }
        // never let a missing FGS notification kill the bubble: it is the whole point of
        // the app, and the service is allowed to run without the badge on most ROMs
        runCatching { startForegroundWithNotification() }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.bubble_permission_denied, Toast.LENGTH_LONG).show()
            stopSelfSafely()
            return START_NOT_STICKY
        }
        val added = runCatching { showBubble() }.isSuccess
        if (!added) {
            Toast.makeText(this, R.string.bubble_failed, Toast.LENGTH_LONG).show()
            stopSelfSafely()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    /** The session outlives every window: created once, kept while the service lives. */
    private fun session(): EditorSessionState =
        sessionRef ?: EditorSessionState(this, scope).also { state ->
            state.theme = runCatching { DraftStore.theme(this) }.getOrNull() ?: state.theme
            state.vm.setTheme(state.theme)
            state.refreshHistory()
            // a bubble already up: the overlay may sit on top of it, so drop the bubble view
            sessionRef = state
        }

    private fun showEditor(draftId: String?) {
        val state = session()
        if (editor == null) {
            editor = EditorOverlay(
                context = this,
                session = state,
                onMinimize = { hideEditor() },
                onClose = { scope.launch { closeEditor() } },
                onOpenGallery = { GalleryRequest.pending = true; openGallery() },
            )
        }
        // the overlay IS the editor now, so the bubble must not sit on top of it
        if (editor?.isShowing != true) dropView()
        editor?.show()
        if (draftId != null) state.restoreDraft(draftId)
    }

    private fun hideEditor() {
        editor?.hide()
        // back to the bubble, so the session is never unreachable
        if (view == null && Settings.canDrawOverlays(this)) runCatching { showBubble() }
    }

    private fun toggleEditor() {
        if (editor?.isShowing == true) hideEditor() else showEditor(null)
    }

    private fun closeEditor() {
        val state = sessionRef
        editor?.hide()
        editor = null
        sessionRef = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        if (state != null) {
            // suspend calls belong in the service scope, not in this plain function
            scope.launch {
                runCatching { state.autosave(force = true) }
                runCatching { state.saveDraft("session_${System.currentTimeMillis()}") }
            }
        }
    }

    private fun openGallery() {
        runCatching {
            val intent = Intent(this, com.emrobinso.floatpic.feature.editor.EditorHostActivity::class.java)
                .setAction(com.emrobinso.floatpic.feature.editor.EditorHostActivity.ACTION_PICK)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }.onFailure {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** Removes the bubble view without touching the service or the session. */
    private fun dropView() {
        runCatching { if (view != null) windowManager.removeView(view) }
        view = null
    }

    private fun loadThumb(uri: Uri) {
        scope.launch {
            runCatching {
                ImageLoader.load(this@BubbleService, uri, targetMax = 256)
            }.getOrNull()?.let { loaded ->
                thumb?.recycle()
                thumb = loaded.bitmap
                view?.setImageDrawable(BubbleDrawable(loaded.bitmap))
                view?.invalidate()
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
        // Platform builder on purpose: Notification.Builder(context, channel) hides a
        // `new Notification()` under the hood, and that constructor is exactly what throws
        // on some OEM builds ("Failure saving state"). A broken notification must never be
        // able to kill the service — the bubble is the whole feature.
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification: Notification = builder
            .setSmallIcon(R.mipmap.ic_launcher)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Notification tap = show the editor overlay. A PendingIntent needs a target Activity,
     * so the invisible host does the forwarding without ever showing a screen.
     */
    private fun restoreIntent(): Intent =
        Intent(this, com.emrobinso.floatpic.feature.editor.EditorHostActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /**
     * The bubble is a plain [android.widget.ImageView], NOT a ComposeView.
     *
     * A ComposeUIView inside a TYPE_APPLICATION_OVERLAY window needs a saved-state owner and
     * a lifecycle owner that the window does not provide; the composition threw while the
     * window was being attached and the process died the moment the bubble was switched on.
     * A View has none of those requirements, so this path cannot crash for that reason.
     */
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
            val savedX = prefs.getInt(KEY_X, Int.MIN_VALUE)
            val savedY = prefs.getInt(KEY_Y, Int.MIN_VALUE)
            if (savedX != Int.MIN_VALUE && savedY != Int.MIN_VALUE) {
                // the bubble belongs where the user last left it, not back at mid-screen
                x = savedX.coerceIn(0, (dm.widthPixels - sizePx).coerceAtLeast(0))
                y = savedY.coerceIn(0, (dm.heightPixels - sizePx).coerceAtLeast(0))
            } else {
                x = dm.widthPixels - sizePx + dp(8)
                y = dm.heightPixels / 2
            }
        }
        params = p
        val image = ImageView(this).apply {
            contentDescription = getString(R.string.bubble_notif_title)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageDrawable(BubbleDrawable(thumb))
            setOnTouchListener { _, event -> handleTouch(event) }
        }
        view = image
        windowManager.addView(image, p)
        scheduleIdle()
    }

    /** Drag to move, tap to bring the editor back, hold 1.6 s to close. */
    private fun handleTouch(event: MotionEvent): Boolean {
        val p = params ?: return false
        val v = view ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                lastX = event.rawX
                lastY = event.rawY
                dragging = false
                scheduleIdle()
                startHold()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && (dx * dx + dy * dy) > dp(12) * dp(12)) {
                    dragging = true
                    cancelHold()
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                }
                if (dragging) {
                    val moveX = (event.rawX - lastX).toInt()
                    val moveY = (event.rawY - lastY).toInt()
                    // let the bubble travel the full screen: nowhere it can be parked becomes
                    // unreachable, and the clamp matches the window, not a guessed margin
                    p.x += moveX
                    p.y += moveY
                    clampToScreen(p)
                    lastX = event.rawX
                    lastY = event.rawY
                    runCatching { windowManager.updateViewLayout(v, p) }
                    scheduleIdle()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelHold()
                if (dragging) {
                    snapToEdge()
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    openEditor()
                }
                rememberPosition(p)
                dragging = false
                scheduleIdle()
            }
        }
        return true
    }

    private fun startHold() {
        holdJob?.cancel()
        holdJob = scope.launch {
            val started = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - started
                holdProgress = (elapsed.toFloat() / HOLD_MS).coerceIn(0f, 1f)
                view?.invalidate()
                if (elapsed >= HOLD_MS) break
                delay(32)
            }
            if (!dragging) {
                view?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                closeApp()
            }
        }
    }

    private fun cancelHoldProgress() {
        holdProgress = 0f
        view?.invalidate()
    }

    private fun cancelHold() {
        holdJob?.cancel()
        holdJob = null
        cancelHoldProgress()
    }

    /** Brings the editor forward WITHOUT killing the bubble: it returns on minimize. */
    private fun openEditor() = toggleEditor()

    /**
     * Draws the bubble: cream disc, shadow, optional thumbnail or the brand spark, and the
     * hold progress arc. No text is drawn — a 64 dp circle has no room for a sentence.
     */
    private inner class BubbleDrawable(private val thumb: android.graphics.Bitmap?) : Drawable() {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF9F7.toInt() }
        private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x3C000000 }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF7A0C2E.toInt()
            style = Paint.Style.STROKE
            strokeWidth = dp(3).toFloat()
            strokeCap = Paint.Cap.ROUND
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFF9F7.toInt()
            textSize = dp(26).toFloat()
            textAlign = Paint.Align.CENTER
        }

        override fun draw(canvas: Canvas) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            val c = min(w, h) / 2f
            val inset = dp(4).toFloat()
            canvas.drawCircle(w / 2f, h / 2f + dp(2).toFloat(), c - inset, shadow)
            canvas.drawCircle(w / 2f, h / 2f, c - inset, fill)
            val picture = thumb
            if (picture != null && !picture.isRecycled) {
                val scale = (c - inset - dp(5)) / (min(picture.width, picture.height) / 2f)
                val dx = w / 2f - picture.width * scale
                val dy = h / 2f - picture.height * scale
                canvas.save()
                canvas.clipPath(android.graphics.Path().apply {
                    addCircle(w / 2f, h / 2f, c - inset - dp(5), android.graphics.Path.Direction.CW)
                })
                canvas.drawBitmap(
                    picture, null,
                    android.graphics.RectF(dx, dy, dx + picture.width * scale, dy + picture.height * scale),
                    fill,
                )
                canvas.restore()
            } else {
                canvas.drawCircle(w / 2f, h / 2f, c - inset - dp(5), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF7A0C2E.toInt()
                })
                canvas.drawText("✦", w / 2f, h / 2f + textPaint.textSize / 3f, textPaint)
            }
            if (holdProgress > 0f) {
                canvas.drawArc(
                    inset, inset, w - inset, h - inset,
                    -90f, 360f * holdProgress, false, ring,
                )
            }
        }

        override fun setAlpha(alpha: Int) {
            fill.alpha = alpha
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun clampToScreen(p: WindowManager.LayoutParams) {
        val dm = resources.displayMetrics
        p.x = p.x.coerceIn(0, (dm.widthPixels - p.width).coerceAtLeast(0))
        p.y = p.y.coerceIn(0, (dm.heightPixels - p.height).coerceAtLeast(0))
    }

    private fun snapToEdge() {
        val p = params ?: return
        val v = view ?: return
        val dm = resources.displayMetrics
        // horizontal snapping only: the vertical spot is a deliberate choice and is kept
        p.x = if (p.x + p.width / 2 < dm.widthPixels / 2) 0 else dm.widthPixels - p.width
        clampToScreen(p)
        rememberPosition(p)
        runCatching { windowManager.updateViewLayout(v, p) }
    }

    /** Position survives a minimize / new bubble: stored in prefs, read when it is re-added. */
    private fun rememberPosition(p: WindowManager.LayoutParams) {
        prefs.edit().putInt(KEY_X, p.x).putInt(KEY_Y, p.y).apply()
    }

    private fun scheduleIdle() {
        // the bubble used to shrink to 78 % after a while (Compose animateFloatAsState);
        // with a plain View the idle timer only matters for the service lifecycle
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(3000)
        }
    }

    /** Stops the bubble completely: the off switch on the home screen. */
    private fun hideBubble() {
        idleJob?.cancel()
        cancelHold()
        dropView()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Hold 1.6 s: clear the draft, close the editor if it is alive, stop the service. */
    private fun closeApp() {
        cancelHold()
        dropView()
        editor?.hide()
        editor = null
        sessionRef = null
        val id = sessionId
        if (id != null) scope.launch { DraftStore.clear(this@BubbleService, id) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        BubbleState.markRunning(false)
        idleJob?.cancel()
        cancelHold()
        runCatching { unregisterReceiver(screenReceiver) }
        editor?.hide()
        editor = null
        sessionRef = null
        dropView()
        thumb?.recycle()
        thumb = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_CLOSE = "com.emrobinso.floatpic.bubble.CLOSE"
        const val ACTION_FINISH_APP = "com.emrobinso.floatpic.FINISH_APP"
        const val ACTION_UPDATE_NOTIF = "com.emrobinso.floatpic.bubble.UPDATE_NOTIF"
        const val ACTION_RESTORE = "com.emrobinso.floatpic.bubble.RESTORE"
        const val ACTION_HIDE = "com.emrobinso.floatpic.bubble.HIDE"
        const val ACTION_EDITOR_SHOWN = "com.emrobinso.floatpic.bubble.EDITOR_SHOWN"
        const val ACTION_TOGGLE_EDITOR = "com.emrobinso.floatpic.bubble.TOGGLE_EDITOR"
        const val ACTION_SHOW_EDITOR = "com.emrobinso.floatpic.bubble.SHOW_EDITOR"
        const val ACTION_OPEN_URI = "com.emrobinso.floatpic.bubble.OPEN_URI"
        const val EXTRA_SESSION = "bubble.session"
        const val EXTRA_THUMB_URI = "bubble.thumb"
        private const val CHANNEL_ID = "floatpic_bubble"
        private const val KEY_X = "bubble_x"
        private const val KEY_Y = "bubble_y"
        private const val NOTIFICATION_ID = 42

        /** Bubble with no picture behind it: tapping only brings the editor back. */
        fun startEmpty(context: Context) {
            val intent = Intent(context, BubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Tap on the bubble: show the editor window, or hide it when it is already open. */
        fun toggleEditor(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, BubbleService::class.java).setAction(ACTION_TOGGLE_EDITOR),
                )
            }
        }

        /** Open a picture in a new overlay session (share, gallery pick, history). */
        fun openInEditor(context: Context, uri: Uri) {
            runCatching {
                val intent = Intent(context, BubbleService::class.java).apply {
                    action = ACTION_OPEN_URI
                    data = uri
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /** Bring up an empty editor window (the "open editor" button on the home screen). */
        fun showEditor(context: Context, draftId: String? = null) {
            runCatching {
                val intent = Intent(context, BubbleService::class.java)
                    .setAction(ACTION_SHOW_EDITOR)
                    .putExtra(EXTRA_SESSION, draftId)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /** The editor is visible: hide the bubble but keep the service for the next minimize. */
        fun editorShown(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, BubbleService::class.java).setAction(ACTION_EDITOR_SHOWN),
                )
            }
        }

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

        /** Stops the bubble for good (the off switch on the home screen). */
        fun hide(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, BubbleService::class.java).setAction(ACTION_CLOSE),
                )
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

        val isRunning: Boolean get() = BubbleState.isRunning
    }
}

/**
 * The overlay window cannot use ActivityResult launchers, so a gallery pick is started as a
 * plain Intent and the outcome comes back through [EditorHostActivity], which forwards the
 * uri here. [pending] marks that the picker was opened by the editor.
 */
object GalleryRequest {
    var pending: Boolean = false
}

/** What the rest of the app needs to know about the service. */
object BubbleState {
    /** true while the service (and so the bubble) is alive */
    var isRunning: Boolean = false
        private set

    fun markRunning(on: Boolean) {
        isRunning = on
    }
}

private const val HOLD_MS = 1600L