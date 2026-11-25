package com.emrobinso.floatpic.feature.ai

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emrobinso.floatpic.R
import com.emrobinso.floatpic.data.AiBackend
import com.emrobinso.floatpic.data.AiEditClient
import com.emrobinso.floatpic.data.AiPromptStore
import com.emrobinso.floatpic.data.AiQuota
import com.emrobinso.floatpic.data.IpInfo
import com.emrobinso.floatpic.ui.icons.FloatIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One finished run: the bitmap plus the prompt that produced it. */
data class AiCandidate(val bitmap: Bitmap, val prompt: String, val lora: String?)

/**
 * Everything the AI panel shows, held ABOVE the panel (in the Activity) so leaving the tool
 * never cancels a request and never loses a result.
 */
class AiSessionState(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var prompt by mutableStateOf("")
    private val lora: String? = null
    var backend by mutableStateOf(AiBackend.of(AiPromptStore.backend(context)))
        private set

    var ip by mutableStateOf<String?>(null)
        private set
    var quota by mutableStateOf(AiQuota.Usage(0, 0L, false))
        private set

    var busy by mutableStateOf(false)
        private set
    var stage by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var elapsed by mutableStateOf(0)
        private set


    /** readable trace of the last run */
    val log = mutableStateListOf<String>()

    var prompts by mutableStateOf(AiPromptStore.load(context))
        private set
    var managerOpen by mutableStateOf(false)

    private var job: Job? = null
    private var resultCallback: ((Bitmap) -> Unit)? = null

    private fun note(line: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        log.add("[$t] $line")
        while (log.size > 120) log.removeAt(0)
    }

    fun logText(): String = log.joinToString("\n")

    fun refreshQuota() {
        scope.launch {
            val found = withContext(Dispatchers.IO) { IpInfo.lookup(refresh = true) }
            ip = found
            quota = AiQuota.usage(context, found)
        }
    }

    fun pushPrompt(text: String) {
        prompts = AiPromptStore.push(context, text)
    }

    fun deletePrompt(text: String) {
        prompts = AiPromptStore.remove(context, text)
    }

    fun renamePrompt(from: String, to: String) {
        prompts = AiPromptStore.rename(context, from, to)
        if (prompt == from) prompt = to.trim()
    }

    fun pinPrompt(text: String, on: Boolean) {
        AiPromptStore.setPinned(context, text, on)
        // recompose the manager: the star lives in the store, not in the list
        prompts = AiPromptStore.load(context)
    }

    fun bumpPrompt(text: String) {
        AiPromptStore.bump(context, text)
    }

    fun clearPrompts() {
        prompts = AiPromptStore.clear(context)
    }

    fun toggleManager() {
        managerOpen = !managerOpen
    }

    fun chooseBackend(next: AiBackend) {
        if (next == backend) return
        backend = next
        AiPromptStore.setBackend(context, next.name)
        note("server diganti ke ${next.label}")
    }

    fun cancel(info: (String) -> Unit) {
        job?.cancel()
        job = null
        busy = false
        stage = ""
        note("dibatalkan oleh pengguna")
        info(context.getString(R.string.ai_cancelled))
    }

    /**
     * Starts a request. The picture sent is the editor PREVIEW (what the user sees), never a
     * re-decoded original: re-reading the file at 3072 px was the routine cause of the
     * force-close on large photos (OOM while the editor bitmap was still alive).
     */
    fun run(
        source: Bitmap?,
        correction: String?,
        onApply: (Bitmap) -> Unit,
        onInfo: (String) -> Unit,
    ) {
        val bmp = source
        if (bmp == null) {
            onInfo(context.getString(R.string.empty_title))
            return
        }
        if (prompt.isBlank()) {
            onInfo(context.getString(R.string.ai_no_prompt))
            return
        }
        if (busy) return

        resultCallback = onApply
        busy = true
        error = null
        stage = context.getString(R.string.ai_stage_queued)
        elapsed = 0
        log.clear()
        val sentPrompt = prompt
        pushPrompt(sentPrompt)
        AiPromptStore.bump(context, sentPrompt)
        note("app ${android.os.Build.VERSION.RELEASE} / ${android.os.Build.MODEL}")
        note("mulai: prompt=\"${sentPrompt.take(80)}\" lora=${lora ?: backend.noStyle ?: "-"}")
        note("sumber preview ${bmp.width}x${bmp.height}")

        job = scope.launch {
            val res = AiEditClient.edit(
                backend = backend,
                source = bmp,
                prompt = sentPrompt,
                lora = lora,
                correction = correction,
                onStage = { s -> stage = s },
                onLog = { line -> note(line) },
            )
            busy = false
            job = null
            when (res) {
                is AiEditClient.Result.Ok -> {
                    withContext(Dispatchers.IO) {
                        val here = ip ?: IpInfo.lookup()
                        AiQuota.recordSuccess(context, here)
                        note("kuota IP $here: ${AiQuota.usage(context, here).used}/${AiQuota.LIMIT}")
                    }
                    refreshQuota()
                    note("sukses: ${res.bitmap.width}x${res.bitmap.height}")
                    resultCallback?.invoke(res.bitmap)
                    // the prompt is NOT cleared: it can be tweaked and run again
                    onInfo(context.getString(R.string.ai_done))
                }
                is AiEditClient.Result.Failed -> {
                    error = res.message
                    stage = ""
                    note("gagal: ${res.message}")
                    if (AiQuota.looksLikeLimit(res.message)) {
                        withContext(Dispatchers.IO) {
                            AiQuota.recordExhausted(context, ip ?: IpInfo.lookup())
                        }
                        note("server menolak: IP $ip ditandai habis")
                        refreshQuota()
                    }
                    onInfo(context.getString(R.string.ai_failed_detail, res.message))
                }
            }
        }

        scope.launch {
            val start = System.currentTimeMillis()
            while (busy) {
                elapsed = ((System.currentTimeMillis() - start) / 1000L).toInt()
                delay(200)
            }
        }
    }
}

/**
 * Kinds of waiting the panel can show. Each gets its own animated glyph so a long queue
 * never looks like a frozen spinner.
 */
enum class AiPhase { UPLOAD, QUEUE, GENERATE, DOWNLOAD, DONE, FAIL }

private fun phaseOf(stage: String): AiPhase {
    val s = stage.lowercase()
    return when {
        s.contains("unggah") || s.contains("upload") -> AiPhase.UPLOAD
        s.contains("antre") || s.contains("antrean") || s.contains("queue") -> AiPhase.QUEUE
        s.contains("unduh") || s.contains("download") -> AiPhase.DOWNLOAD
        s.contains("membuat") || s.contains("generat") -> AiPhase.GENERATE
        s.contains("selesai") -> AiPhase.DONE
        s.contains("gagal") || s.contains("error") -> AiPhase.FAIL
        else -> AiPhase.QUEUE
    }
}

@Composable
fun AiPanel(
    session: AiSessionState,
    base: Bitmap?,
    baseFile: java.io.File?,
    onApply: (Bitmap) -> Unit,
    onInfo: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) { session.refreshQuota() }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                ctx.getString(R.string.panel_ai_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = scheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            // prompt manager: the saved prompts, with delete + clear
            Text(
                ctx.getString(R.string.ai_manager),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (session.managerOpen) scheme.onPrimary else scheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (session.managerOpen) scheme.primary else scheme.primary.copy(alpha = .12f),
                    )
                    .clickable { session.toggleManager() }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(5.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(ctx.getString(R.string.ai_server), fontSize = 9.sp, color = scheme.onSurfaceVariant)
            AiBackend.entries.forEach { b ->
                ServerChip(b.label, session.backend == b) { session.chooseBackend(b) }
            }
        }
        Spacer(Modifier.height(6.dp))

        if (session.managerOpen) {
            PromptManager(session)
            Spacer(Modifier.height(6.dp))
        }

        // prompt box: fixed height, scrolls inside, no character limit
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .height(60.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.outline.copy(.5f), RoundedCornerShape(11.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                if (session.prompt.isEmpty()) {
                    Text(
                        ctx.getString(R.string.panel_ai_hint),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = scheme.onSurfaceVariant.copy(.55f),
                    )
                }
                BasicTextField(
                    value = session.prompt,
                    onValueChange = { session.prompt = it },
                    singleLine = false,
                    textStyle = TextStyle(fontSize = 12.sp, color = scheme.onSurface, lineHeight = 15.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                )
            }
            Spacer(Modifier.width(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // paste from clipboard
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(scheme.primary.copy(alpha = .14f))
                        .border(1.dp, scheme.primary.copy(alpha = .5f), RoundedCornerShape(9.dp))
                        .semantics { contentDescription = ctx.getString(R.string.action_paste) }
                        .clickable {
                            val pasted = clipboard.getText()?.text?.trim()
                            if (!pasted.isNullOrBlank()) session.prompt = pasted
                        },
                    contentAlignment = Alignment.Center,
                ) { FloatIcons.Paste(tint = scheme.primary) }
                // clear the field
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(scheme.surfaceVariant.copy(alpha = .7f))
                        .border(1.dp, scheme.outline.copy(alpha = .45f), RoundedCornerShape(9.dp))
                        .semantics { contentDescription = ctx.getString(R.string.ai_clear) }
                        .clickable { session.prompt = "" },
                    contentAlignment = Alignment.Center,
                ) { FloatIcons.Trash(tint = scheme.onSurfaceVariant) }
            }
        }

        if (session.prompts.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                session.prompts.take(4).forEach { item ->
                    PromptChip(text = item) { session.prompt = item }
                }
            }
        }

        // per-IP allowance
        Spacer(Modifier.height(5.dp))
        run {
            val q = session.quota
            val here = session.ip ?: "-"
            val left = q.remaining
            val windowH = (q.windowLeftMs / (60 * 60 * 1000L)).toInt().coerceAtLeast(1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (left > 0) ctx.getString(R.string.ai_quota, q.used, AiQuota.LIMIT, here)
                    else ctx.getString(R.string.ai_quota_full, here, windowH),
                    fontSize = 9.sp,
                    color = if (left > 0) scheme.onSurfaceVariant else scheme.error,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    ctx.getString(R.string.ai_quota_recheck),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { session.refreshQuota() }
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }

        Spacer(Modifier.height(7.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RunChip(
                label = ctx.getString(if (session.busy) R.string.ai_running else R.string.ai_run),
                enabled = !session.busy,
                onClick = { session.run(base, null, onApply, onInfo) },
            )
            if (session.busy) {
                Box(Modifier.weight(1f)) {
                AiStatusCard(
                    phase = phaseOf(session.stage),
                    stage = session.stage,
                    elapsed = session.elapsed,
                    onCancel = { session.cancel(onInfo) },
                )
                }
            } else {
                session.error?.let { err ->
                    Text(
                        ctx.getString(R.string.ai_failed_detail, err),
                        fontSize = 9.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = scheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Saved prompts, with per-item delete and a bulk clear. */
@Composable
private fun PromptManager(session: AiSessionState) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 116.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceVariant.copy(alpha = .45f))
            .border(1.dp, scheme.outline.copy(alpha = .3f), RoundedCornerShape(10.dp))
            .padding(6.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                ctx.getString(R.string.ai_manager_title, session.prompts.size),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (session.prompts.isNotEmpty()) {
                Text(
                    ctx.getString(R.string.ai_manager_clear),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.error,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { session.clearPrompts() }
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
        if (session.prompts.isEmpty()) {
            Text(
                ctx.getString(R.string.ai_manager_empty),
                fontSize = 9.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        session.prompts.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .clickable { session.prompt = item }
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { session.deletePrompt(item) },
                    contentAlignment = Alignment.Center,
                ) { FloatIcons.Trash(tint = scheme.error, modifier = Modifier.size(13.dp)) }
            }
        }
    }
}

/**
 * The waiting state: an animated glyph per phase (hourglass while queued, magic wand while
 * generating, down arrow while downloading) plus the stage text, the timer and cancel.
 */
@Composable
private fun AiStatusCard(
    phase: AiPhase,
    stage: String,
    elapsed: Int,
    onCancel: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val transition = rememberInfiniteTransition(label = "ai-phase")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    val pulse by transition.animateFloat(
        initialValue = .35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceVariant.copy(alpha = .55f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhaseGlyph(phase = phase, spin = spin, pulse = pulse)
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stage.ifBlank { ctx.getString(R.string.ai_working) },
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = scheme.onSurface,
            )
            Text("${elapsed}s", fontSize = 8.sp, color = scheme.onSurfaceVariant)
        }
        Text(
            ctx.getString(R.string.ai_cancel),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.error,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(scheme.error.copy(alpha = .12f))
                .clickable(onClick = onCancel)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * The waiting state drawn ON the picture: the user asked for the preview itself to carry
 * the animation (hourglass while queued, wand while generating, arrow while downloading).
 */
@Composable
fun AiPreviewOverlay(stage: String, elapsed: Int) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "ai-preview")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    val pulse by transition.animateFloat(
        initialValue = .35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .38f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surface.copy(alpha = .94f)),
                contentAlignment = Alignment.Center,
            ) {
                PhaseGlyph(phase = phaseOf(stage), spin = spin, pulse = pulse)
            }
            Spacer(Modifier.height(7.dp))
            Text(
                stage.ifBlank { "…" },
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = .55f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "${elapsed}s",
                fontSize = 8.sp,
                color = Color.White.copy(alpha = .8f),
            )
        }
    }
}

/** Vector glyphs: no assets, drawn with the theme colour. */
@Composable
private fun PhaseGlyph(phase: AiPhase, spin: Float, pulse: Float) {
    val scheme = MaterialTheme.colorScheme
    val tint = scheme.primary
    Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
        when (phase) {
            // hourglass: two triangles, top one pulses
            AiPhase.QUEUE -> Canvas(Modifier.size(16.dp)) {
                val w = size.width
                val h = size.height
                val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                drawLine(tint, Offset(0f, 0f), Offset(w, 0f), 1.6.dp.toPx(), StrokeCap.Round)
                drawLine(tint, Offset(0f, h), Offset(w, h), 1.6.dp.toPx(), StrokeCap.Round)
                drawLine(tint.copy(alpha = pulse), Offset(w * .1f, h * .1f), Offset(w * .9f, h * .1f), 1.4.dp.toPx(), StrokeCap.Round)
                drawLine(tint, Offset(w * .12f, h * .08f), Offset(w * .88f, h * .08f), 1.4.dp.toPx(), StrokeCap.Round)
                drawLine(tint, Offset(w * .12f, h * .08f), Offset(w * .88f, h * .92f), 1.4.dp.toPx(), StrokeCap.Round)
                drawLine(tint, Offset(w * .88f, h * .08f), Offset(w * .12f, h * .92f), 1.4.dp.toPx(), StrokeCap.Round)
                drawCircle(tint.copy(alpha = pulse), radius = w * .12f, center = Offset(w * .5f, h * .78f))
                drawRoundRect(tint.copy(alpha = .25f), Offset(w * .3f, h * .42f), Size(w * .4f, h * .4f), style = stroke)
            }
            // magic wand: stick + three sparkles, rotating slowly
            AiPhase.GENERATE -> Canvas(Modifier.size(18.dp).rotate(spin)) {
                val w = size.width
                val sw = 1.7.dp.toPx()
                drawLine(tint, Offset(w * .18f, w * .82f), Offset(w * .68f, w * .32f), sw, StrokeCap.Round)
                listOf(
                    Offset(w * .82f, w * .18f) to w * .13f,
                    Offset(w * .86f, w * .52f) to w * .09f,
                    Offset(w * .5f, w * .12f) to w * .08f,
                ).forEach { (c, r) ->
                    drawLine(tint.copy(alpha = pulse), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.3.dp.toPx(), StrokeCap.Round)
                    drawLine(tint.copy(alpha = pulse), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.3.dp.toPx(), StrokeCap.Round)
                }
            }
            // down arrow + tray line: downloading
            AiPhase.DOWNLOAD -> Canvas(Modifier.size(16.dp)) {
                val w = size.width
                val h = size.height
                val sw = 1.8.dp.toPx()
                val bob = (pulse - .35f) / .65f
                val tipY = h * (.25f + .12f * (1f - bob))
                drawLine(tint, Offset(w * .5f, h * .12f), Offset(w * .5f, tipY), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * .28f, tipY - h * .14f), Offset(w * .5f, tipY), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * .72f, tipY - h * .14f), Offset(w * .5f, tipY), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * .18f, h * .88f), Offset(w * .82f, h * .88f), sw, StrokeCap.Round)
            }
            // up arrow: uploading
            AiPhase.UPLOAD -> Canvas(Modifier.size(16.dp)) {
                val w = size.width
                val h = size.height
                val sw = 1.8.dp.toPx()
                drawLine(tint, Offset(w * .5f, h * .18f), Offset(w * .5f, h * .62f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * .3f, h * .38f), Offset(w * .5f, h * .18f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * .7f, h * .38f), Offset(w * .5f, h * .18f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * .18f, h * .88f), Offset(w * .82f, h * .88f), sw, StrokeCap.Round)
            }
            AiPhase.DONE -> FloatIcons.Check(tint = tint)
            AiPhase.FAIL -> FloatIcons.Close(tint = scheme.error)
        }
    }
}

/** Server switch. */
@Composable
private fun ServerChip(label: String, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = label,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) scheme.primary else scheme.surfaceVariant.copy(.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun PromptChip(text: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = if (text.length > 26) text.take(25) + "…" else text,
        fontSize = 9.sp,
        maxLines = 1,
        color = scheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(scheme.surfaceVariant.copy(.7f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun RunChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = scheme.onPrimary,
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) scheme.primary else scheme.primary.copy(alpha = .35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
