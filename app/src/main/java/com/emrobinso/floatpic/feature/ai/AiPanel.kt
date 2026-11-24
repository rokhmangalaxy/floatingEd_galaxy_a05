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
import kotlinx.coroutines.launch

/** One finished run: the bitmap plus the prompt that produced it. */
data class AiCandidate(val bitmap: Bitmap, val prompt: String, val lora: String?)

/**
 * AI panel. Deliberately a "tray": every run is kept in [results], the newest one is the
 * applied picture (last wins), and older ones stay tappable so the user can go back to a
 * better result without spending another request.
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

    var prompt by remember { mutableStateOf("") }
    var lora by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun run(correction: String?) {
        val bmp = source ?: return
        if (prompt.isBlank()) {
            onInfo(ctx.getString(R.string.ai_no_prompt))
            return
        }
        if (busy) return
        busy = true
        error = null
        stage = ctx.getString(R.string.ai_uploading)
        scope.launch {
            val res = AiEditClient.edit(
                source = bmp,
                prompt = prompt,
                lora = lora,
                correction = correction,
                onStage = { s ->
                    stage = if (s == "upload") ctx.getString(R.string.ai_uploading)
                    else ctx.getString(R.string.ai_generating)
                },
            )
            busy = false
            when (res) {
                is AiEditClient.Result.Ok -> {
                    results.add(0, AiCandidate(res.bitmap, prompt, lora))
                    // last result wins: it immediately replaces the working picture
                    onApply(res.bitmap)
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

        // prompt box (no auto-focus: opening the panel must not pop the keyboard)
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.outline.copy(.5f), RoundedCornerShape(11.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (prompt.isEmpty()) {
                Text(
                    "contoh: buat latar jadi pantai saat senja",
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant.copy(.55f),
                )
            }
            BasicTextField(
                value = prompt,
                onValueChange = { if (it.length <= 300) prompt = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.sp, color = scheme.onSurface),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LoraChip(ctx.getString(R.string.ai_lora_none), lora == null) { lora = null }
            AiEditClient.LORAS.forEach { id ->
                LoraChip(id, lora == id) { lora = id }
            }
        }

        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Text(stage, fontSize = 10.sp, color = scheme.onSurfaceVariant)
            } else if (results.isNotEmpty()) {
                // cheap re-roll with feedback: "make it brighter, keep the face"
                RunChip(
                    label = "Perbaiki",
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
            // THE TRAY: every generated picture, newest first. Tap = make it the working one.
            Row(
                Modifier
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
