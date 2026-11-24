package com.emrobinso.floatpic.feature.ai

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emrobinso.floatpic.R
import com.emrobinso.floatpic.data.AiBackend
import com.emrobinso.floatpic.data.AiEditClient
import com.emrobinso.floatpic.data.AiPromptStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One finished run: the bitmap plus the prompt that produced it. */
data class AiCandidate(val bitmap: Bitmap, val prompt: String, val lora: String?)

/**
 * Everything the AI panel shows, held ABOVE the panel (in the Activity).
 *
 * The panel composable is disposed the moment the user leaves the AI tool, so any state kept
 * inside it was lost — that is why a running request used to disappear and a finished result
 * vanished when switching menus. All of it lives here now, and the request runs on the
 * Activity scope, so switching tools never cancels it.
 */
class AiSessionState(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var prompt by mutableStateOf("")
    var lora by mutableStateOf<String?>(null)
    var backend by mutableStateOf(AiBackend.of(AiPromptStore.backend(context)))
        private set
    var busy by mutableStateOf(false)
        private set
    var stage by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var elapsed by mutableStateOf(0)
        private set
    var showLog by mutableStateOf(false)
        private set

    /** newest first; the first one is what the editor is currently showing */
    val results = mutableStateListOf<AiCandidate>()

    /** readable trace of the last run, copyable from the UI */
    val log = mutableStateListOf<String>()

    var prompts by mutableStateOf(AiPromptStore.load(context))
        private set

    private var job: Job? = null
    private var resultCallback: ((Bitmap) -> Unit)? = null

    private fun note(line: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        log.add("[$t] $line")
        // keep the box small; the newest lines are what matter
        while (log.size > 120) log.removeAt(0)
    }

    fun logText(): String = log.joinToString("\n")

    fun toggleLog() {
        showLog = !showLog
    }

    fun cancel(info: (String) -> Unit) {
        job?.cancel()
        job = null
        busy = false
        stage = ""
        note("dibatalkan oleh pengguna")
        info(context.getString(R.string.ai_cancelled))
    }

    fun pushPrompt(text: String) {
        prompts = AiPromptStore.push(context, text)
    }

    /** Switching server invalidates the LoRA choice: ids differ per Space. */
    fun setBackend(next: AiBackend) {
        if (next == backend) return
        backend = next
        lora = null
        AiPromptStore.setBackend(context, next.name)
        note("server diganti ke ${next.label}")
    }

    /**
     * Starts a request. [onApply] is remembered so a result that arrives while the user is in
     * another tool is still applied instead of being dropped.
     */
    fun run(
        source: Bitmap?,
        correction: String?,
        onApply: (Bitmap) -> Unit,
        onInfo: (String) -> Unit,
    ) {
        val bmp = source
        if (bmp == null) {
            onInfo(context.getString(R.string.open_failed))
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
        stage = context.getString(R.string.ai_uploading)
        elapsed = 0
        log.clear()
        val sentPrompt = prompt
        pushPrompt(sentPrompt)
        note("app ${android.os.Build.VERSION.RELEASE} / ${android.os.Build.MODEL}")
        note("mulai: prompt=\"${sentPrompt.take(80)}\" lora=${lora ?: backend.noStyle ?: "-"}")
        note("sumber ${bmp.width}x${bmp.height}")

        // running on the ACTIVITY scope: leaving the AI menu must not cancel the request
        job = scope.launch {
            val res = AiEditClient.edit(
                backend = backend,
                source = bmp,
                prompt = sentPrompt,
                lora = lora,
                correction = correction,
                onStage = { s -> stage = s.replaceFirstChar { it.uppercase() } },
                onLog = { line -> note(line) },
            )
            busy = false
            job = null
            when (res) {
                is AiEditClient.Result.Ok -> {
                    results.add(0, AiCandidate(res.bitmap, sentPrompt, lora))
                    note("sukses: ${res.bitmap.width}x${res.bitmap.height}, ${results.size} hasil")
                    resultCallback?.invoke(res.bitmap)
                    prompt = ""
                    onInfo(context.getString(R.string.ai_done))
                }
                is AiEditClient.Result.Failed -> {
                    error = res.message
                    stage = ""
                    note("gagal: ${res.message}")
                    onInfo(context.getString(R.string.ai_failed_detail, res.message))
                }
            }
        }

        // ticking timer: separate job so it stops with the request
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
 * AI panel. All state lives in [session] (see [AiSessionState]), so opening another tool and
 * coming back keeps the prompt, the running timer, the log and every generated picture.
 */
@Composable
fun AiPanel(
    session: AiSessionState,
    source: Bitmap?,
    onApply: (Bitmap) -> Unit,
    onInfo: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                ctx.getString(R.string.panel_ai_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = scheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            // log toggle: the only way to see why a request did nothing
            Text(
                ctx.getString(if (session.showLog) R.string.ai_log_hide else R.string.ai_log_show),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(scheme.primary.copy(alpha = .12f))
                    .clickable { session.toggleLog() }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(5.dp))

        // which server: both run Qwen-Image-Edit-2511, but one may be asleep / queued
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                ctx.getString(R.string.ai_server),
                fontSize = 9.sp,
                color = scheme.onSurfaceVariant,
            )
            AiBackend.entries.forEach { b ->
                ServerChip(
                    label = b.label,
                    active = session.backend == b,
                    onClick = { session.setBackend(b) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        // prompt box: TWO lines, no auto-focus (opening the panel must not pop the keyboard)
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
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
                    color = scheme.onSurfaceVariant.copy(.55f),
                )
            }
            BasicTextField(
                value = session.prompt,
                onValueChange = { if (it.length <= 150) session.prompt = it },
                singleLine = false,
                maxLines = 2,
                textStyle = TextStyle(fontSize = 12.sp, color = scheme.onSurface, lineHeight = 15.sp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (session.prompts.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                session.prompts.forEach { item ->
                    PromptChip(text = item) { session.prompt = item }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LoraChip(ctx.getString(R.string.ai_lora_none), session.lora == null) {
                session.lora = null
            }
            session.backend.loras.forEach { id ->
                LoraChip(label = id, active = session.lora == id) { session.lora = id }
            }
        }

        Spacer(Modifier.height(7.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RunChip(
                label = ctx.getString(R.string.ai_run),
                enabled = !session.busy,
                onClick = { session.run(source, null, onApply, onInfo) },
            )
            if (session.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = scheme.primary,
                )
                Text(
                    "${session.stage} ${session.elapsed}s",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    ctx.getString(R.string.ai_cancel),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.error,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(scheme.error.copy(alpha = .12f))
                        .clickable { session.cancel(onInfo) }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                )
            } else if (session.results.isNotEmpty()) {
                RunChip(
                    label = ctx.getString(R.string.ai_retry),
                    enabled = true,
                    onClick = {
                        session.run(
                            source,
                            "keep the composition, improve on the previous attempt",
                            onApply,
                            onInfo,
                        )
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            session.error?.let {
                Text(
                    ctx.getString(R.string.ai_failed_detail, it),
                    fontSize = 9.sp,
                    maxLines = 2,
                    color = scheme.error,
                )
            }
        }

        if (session.showLog) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ctx.getString(R.string.ai_log_title),
                    fontSize = 10.sp,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    ctx.getString(R.string.ai_log_copy),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(scheme.primary.copy(alpha = .12f))
                        .clickable {
                            clipboard.setText(AnnotatedString(session.logText()))
                            onInfo(ctx.getString(R.string.ai_log_copied))
                        }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 132.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(scheme.surfaceVariant.copy(alpha = .55f))
                    .border(1.dp, scheme.outline.copy(.35f), RoundedCornerShape(9.dp))
                    .padding(7.dp),
            ) {
                val scroll = rememberScrollState()
                LaunchedEffect(session.log.size) { scroll.scrollTo(scroll.maxValue) }
                Text(
                    text = if (session.log.isEmpty()) "—" else session.logText(),
                    fontSize = 8.5.sp,
                    lineHeight = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scroll),
                )
            }
        }

        if (session.results.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                ctx.getString(R.string.ai_results) + " (${session.results.size})",
                fontSize = 10.sp,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                session.results.forEachIndexed { index, candidate ->
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(scheme.surfaceVariant)
                            .border(
                                width = if (index == 0) 2.dp else 1.dp,
                                color = if (index == 0) scheme.primary else scheme.outline.copy(.35f),
                                shape = RoundedCornerShape(9.dp),
                            )
                            .clickable { onApply(candidate.bitmap) },
                    ) {
                        Image(
                            bitmap = candidate.bitmap.asImageBitmap(),
                            contentDescription = ctx.getString(R.string.ai_apply),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(46.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            ctx.getString(R.string.panel_ai_hint_short),
            fontSize = 9.sp,
            lineHeight = 12.sp,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** Server switch. A compact pill so both options always fit on one line. */
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

/** One saved prompt. Tap = put it back into the box. */
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
private fun LoraChip(label: String, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = label,
        fontSize = 10.sp,
        maxLines = 1,
        color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) scheme.primary else Color.Transparent)
            .border(
                1.dp,
                if (active) Color.Transparent else scheme.outline.copy(.5f),
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
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
