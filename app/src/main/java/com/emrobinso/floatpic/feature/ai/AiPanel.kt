package com.emrobinso.floatpic.feature.ai

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emrobinso.floatpic.R
import com.emrobinso.floatpic.data.AiEditClient
import com.emrobinso.floatpic.data.AiPromptStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One finished run: the bitmap plus the prompt that produced it. */
data class AiCandidate(val bitmap: Bitmap, val prompt: String, val lora: String?)

/**
 * AI panel.
 *
 * Session rules the user asked for:
 *  - the panel starts EMPTY every time it is opened (input, button and spinner are reset),
 *    which is why nothing here is remembered across a [Tool.AI] open/close;
 *  - while a request runs a timer ticks, so a slow Space never looks frozen;
 *  - the prompt box is a two line textarea;
 *  - the last 10 prompts are kept as one-tap history chips.
 *
 * Results are a tray: the newest run immediately replaces the working picture (last wins)
 * and older runs stay tappable.
 */
@Composable
fun AiPanel(
    source: Bitmap?,
    onApply: (Bitmap) -> Unit,
    onInfo: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val results = remember { mutableStateListOf<AiCandidate>() }

    // saveable-only where it must survive a rotation, plain state where it must reset
    var prompt by remember { mutableStateOf("") }
    var lora by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var elapsed by remember { mutableStateOf(0) }

    // ---- prompt history (10, newest first, transparently persisted) -------------------
    var promptHistory by remember { mutableStateOf(AiPromptStore.load(ctx)) }

    fun rememberPrompt(text: String) {
        promptHistory = AiPromptStore.push(ctx, text)
    }

    // ---- timer: seconds since the request started ------------------------------------
    LaunchedEffect(busy) {
        if (!busy) {
            elapsed = 0
            return@LaunchedEffect
        }
        val start = System.currentTimeMillis()
        while (busy) {
            elapsed = ((System.currentTimeMillis() - start) / 1000L).toInt()
            delay(200)
        }
    }

    fun run(correction: String?) {
        val bmp = source ?: return
        if (prompt.isBlank()) {
            onInfo(ctx.getString(R.string.ai_no_prompt))
            return
        }
        if (busy) return
        busy = true
        error = null
        results.clear()
        rememberPrompt(prompt)
        stage = ctx.getString(R.string.ai_uploading)
        val sentPrompt = prompt
        scope.launch {
            val res = AiEditClient.edit(
                source = bmp,
                prompt = sentPrompt,
                lora = lora,
                correction = correction,
                onStage = { phase ->
                    stage = if (phase == "upload") ctx.getString(R.string.ai_uploading)
                    else ctx.getString(R.string.ai_generating)
                },
            )
            busy = false
            when (res) {
                is AiEditClient.Result.Ok -> {
                    results.add(0, AiCandidate(res.bitmap, sentPrompt, lora))
                    // last result wins: it immediately replaces the working picture
                    onApply(res.bitmap)
                    // the request is done: the panel goes back to its empty, ready state
                    prompt = ""
                    onInfo(ctx.getString(R.string.ai_done))
                }
                is AiEditClient.Result.Failed -> {
                    error = res.message
                    onInfo(ctx.getString(R.string.ai_failed_detail, res.message))
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            ctx.getString(R.string.panel_ai_title),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = scheme.onSurface,
        )
        Spacer(Modifier.height(5.dp))

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
            if (prompt.isEmpty()) {
                Text(
                    ctx.getString(R.string.panel_ai_hint),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = scheme.onSurfaceVariant.copy(.55f),
                )
            }
            BasicTextField(
                value = prompt,
                onValueChange = { if (it.length <= 150) prompt = it },
                singleLine = false,
                maxLines = 2,
                textStyle = TextStyle(fontSize = 12.sp, color = scheme.onSurface, lineHeight = 15.sp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (promptHistory.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            // 10 last prompts: tap to reuse, long press would be nicer but a tap is enough
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                promptHistory.forEach { item ->
                    PromptChip(text = item) { prompt = item }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LoraChip(label = ctx.getString(R.string.ai_lora_none), active = lora == null) { lora = null }
            AiEditClient.LORAS.forEach { id ->
                LoraChip(label = id, active = lora == id) { lora = id }
            }
        }

        Spacer(Modifier.height(7.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RunChip(
                label = ctx.getString(R.string.ai_run),
                enabled = !busy,
                onClick = { run(null) },
            )
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = scheme.primary,
                )
                Text(
                    "${stage} ${elapsed}s",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurfaceVariant,
                )
            } else if (results.isNotEmpty()) {
                RunChip(
                    label = ctx.getString(R.string.ai_retry),
                    enabled = true,
                    onClick = { run("keep the composition, improve on the previous attempt") },
                )
            }
            Spacer(Modifier.weight(1f))
            error?.let {
                Text(
                    ctx.getString(R.string.ai_failed_detail, it),
                    fontSize = 9.sp,
                    maxLines = 2,
                    color = scheme.error,
                )
            }
        }

        if (results.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                ctx.getString(R.string.ai_results) + " (${results.size})",
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
                results.forEachIndexed { index, candidate ->
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
            ctx.getString(R.string.panel_ai_hint),
            fontSize = 9.sp,
            lineHeight = 12.sp,
            color = scheme.onSurfaceVariant,
        )
    }
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
