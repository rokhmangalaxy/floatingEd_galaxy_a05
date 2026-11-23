package com.emrobinso.floatpic.feature.editor

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emrobinso.floatpic.R
import com.emrobinso.floatpic.core.model.AspectPreset
import com.emrobinso.floatpic.core.model.EditorState
import com.emrobinso.floatpic.core.model.Transform
import com.emrobinso.floatpic.ui.Haptics
import com.emrobinso.floatpic.ui.icons.FloatIcons
import com.emrobinso.floatpic.ui.theme.LocalExtra
import kotlinx.coroutines.delay

enum class Tool { NONE, CROP, FRAME, BACKGROUND, TEXT }

/**
 * The floating card (docs/02 L1): one screen, everything immediate, CTA at the bottom.
 * Kept intentionally standalone so the same content can be hosted by an overlay later.
 */
@Composable
fun EditorScreen(
    state: EditorState,
    bitmap: Bitmap?,
    theme: com.emrobinso.floatpic.ui.theme.AppTheme,
    loading: Boolean,
    error: String?,
    canUndo: Boolean,
    canRedo: Boolean,
    haptics: Haptics,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onTheme: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAspect: (AspectPreset) -> Unit,
    onPreview: (Transform) -> Unit,
    onCommit: (Transform) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onCropApply: () -> Unit,
    onFrameAlign: (Float, Float) -> Unit,
    onPadding: (Float) -> Unit,
    onCorner: (Float) -> Unit,
    onShadow: () -> Unit,
    onBackground: (com.emrobinso.floatpic.core.model.BackgroundSpec) -> Unit,
    onAmbient: () -> Unit,
    onScaleMode: (com.emrobinso.floatpic.core.model.ScaleMode) -> Unit,
    onRotate90: (Float) -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onText: (String) -> Unit,
    onTextPosition: (com.emrobinso.floatpic.core.model.TextPosition) -> Unit,
    onTextStyle: (com.emrobinso.floatpic.core.model.TextStylePreset) -> Unit,
    onTextSize: (Float) -> Unit,
    onTextCommit: () -> Unit,
) {
    var tool by remember { mutableStateOf(Tool.NONE) }
    var guides by remember { mutableStateOf(false) }
    val extra = LocalExtra.current
    val radius = extra.cardRadius.dp
    val scheme = MaterialTheme.colorScheme

    // bottom sheet gets out of the way when the native keyboard opens (docs/12)
    val ime = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)

    Surface(
        modifier = modifier
            .fillMaxWidth(0.96f)
            .shadow(if (extra.raised) 6.dp else 24.dp, RoundedCornerShape(radius), clip = false)
            .border(
                width = if (extra.raised) 1.dp else 0.dp,
                color = if (extra.raised) extra.softLight.copy(alpha = .8f) else Color.Transparent,
                shape = RoundedCornerShape(radius),
            ),
        shape = RoundedCornerShape(radius),
        color = scheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            TopBar(
                canUndo = canUndo,
                canRedo = canRedo,
                onClose = onClose,
                onUndo = onUndo,
                onRedo = onRedo,
                onTheme = onTheme,
                onMinimize = onMinimize,
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                when {
                    loading -> LoadingState()
                    error != null -> ErrorState(error)
                    else -> EditorViewport(
                        state = state,
                        bitmap = bitmap,
                        haptics = haptics,
                        showGuides = guides || tool == Tool.CROP,
                        onPreview = onPreview,
                        onCommit = onCommit,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }

            RatioChips(
                selected = state.aspect,
                onSelect = { haptics.tick(); onAspect(it) },
            )

            ToolRow(
                active = tool,
                onTool = { haptics.tick(); tool = if (tool == it) Tool.NONE else it },
            )

            AnimatedVisibility(
                visible = tool != Tool.NONE,
                enter = slideInVertically { it / 2 } + fadeIn(),
                exit = slideOutVertically { it / 2 } + fadeOut(),
            ) {
                Column {
                    ToolPanel(
                        tool = tool,
                        state = state,
                        hasBitmap = bitmap != null,
                        onCropApply = { guides = !guides; onCropApply() },
                        onFrameAlign = onFrameAlign,
                        onPadding = onPadding,
                        onCorner = onCorner,
                        onShadow = onShadow,
                        onBackground = onBackground,
                        onAmbient = onAmbient,
                        onScaleMode = onScaleMode,
                        onRotate90 = onRotate90,
                        onFlipH = onFlipH,
                        onFlipV = onFlipV,
                        onText = onText,
                        onTextPosition = onTextPosition,
                        onTextStyle = onTextStyle,
                        onTextSize = onTextSize,
                        onTextCommit = onTextCommit,
                        onDone = { tool = Tool.NONE },
                    )
                }
            }

            ActionBar(onSave = onSave, onShare = onShare)
            Spacer(
                Modifier.height(
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() * 0.5f,
                ),
            )
        }
    }
}

@Composable
private fun TopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTheme: () -> Unit,
    onMinimize: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(onClick = onClose) { FloatIcons.Close(tint = scheme.onSurface) }
        Spacer(Modifier.width(6.dp))
        Text(
            text = androidx.compose.ui.platform.LocalContext.current.getString(R.string.editor_title),
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = scheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        RoundIconButton(enabled = canUndo, onClick = onUndo) {
            FloatIcons.Undo(tint = if (canUndo) scheme.onSurface else scheme.onSurface.copy(.3f))
        }
        RoundIconButton(enabled = canRedo, onClick = onRedo) {
            FloatIcons.Redo(tint = if (canRedo) scheme.onSurface else scheme.onSurface.copy(.3f))
        }
        RoundIconButton(onClick = onTheme) { FloatIcons.Palette(tint = scheme.onSurface) }
        RoundIconButton(onClick = onMinimize) { FloatIcons.Minimize(tint = scheme.onSurface) }
    }
}

@Composable
fun RoundIconButton(
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun RatioChips(selected: AspectPreset, onSelect: (AspectPreset) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val presets = listOf(
        AspectPreset.ORIGINAL to R.string.ratio_original,
        AspectPreset.SQUARE to 0,
        AspectPreset.PORTRAIT_4_5 to 0,
        AspectPreset.STORY_9_16 to 0,
        AspectPreset.LANDSCAPE_16_9 to 0,
        AspectPreset.CLASSIC_3_2 to 0,
        AspectPreset.CLASSIC_4_3 to 0,
        AspectPreset.PORTRAIT_2_3 to 0,
        AspectPreset.PORTRAIT_3_4 to 0,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { (preset, labelRes) ->
            val active = preset == selected
            val label = when (preset) {
                AspectPreset.ORIGINAL -> androidx.compose.ui.platform.LocalContext.current
                    .getString(R.string.ratio_original)
                AspectPreset.SQUARE -> "1:1"
                AspectPreset.PORTRAIT_4_5 -> "4:5"
                AspectPreset.STORY_9_16 -> "9:16"
                AspectPreset.LANDSCAPE_16_9 -> "16:9"
                AspectPreset.CLASSIC_3_2 -> "3:2"
                AspectPreset.CLASSIC_4_3 -> "4:3"
                AspectPreset.PORTRAIT_2_3 -> "2:3"
                AspectPreset.PORTRAIT_3_4 -> "3:4"
                else -> "9:20"
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (active) scheme.primary else scheme.surfaceVariant.copy(alpha = .7f),
                    )
                    .border(
                        width = if (active) 0.dp else 1.dp,
                        color = scheme.outline.copy(alpha = .45f),
                        shape = RoundedCornerShape(50),
                    )
                    .clickable { onSelect(preset) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ToolRow(active: Tool, onTool: (Tool) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ToolButton(
            label = ctx.getString(R.string.tool_crop),
            active = active == Tool.CROP,
            icon = { FloatIcons.Crop(tint = if (active == Tool.CROP) scheme.onPrimary else scheme.onSurface) },
            onClick = { onTool(Tool.CROP) },
        )
        ToolButton(
            label = ctx.getString(R.string.tool_frame),
            active = active == Tool.FRAME,
            icon = { FloatIcons.Position(tint = if (active == Tool.FRAME) scheme.onPrimary else scheme.onSurface) },
            onClick = { onTool(Tool.FRAME) },
        )
        ToolButton(
            label = ctx.getString(R.string.tool_background),
            active = active == Tool.BACKGROUND,
            icon = { FloatIcons.Background(tint = if (active == Tool.BACKGROUND) scheme.onPrimary else scheme.onSurface) },
            onClick = { onTool(Tool.BACKGROUND) },
        )
        ToolButton(
            label = ctx.getString(R.string.tool_text),
            active = active == Tool.TEXT,
            icon = { FloatIcons.TextIcon(tint = if (active == Tool.TEXT) scheme.onPrimary else scheme.onSurface) },
            onClick = { onTool(Tool.TEXT) },
        )
    }
}

@Composable
private fun ToolButton(
    label: String,
    active: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = LocalExtra.current
    val bg = if (active) scheme.primary else scheme.surfaceVariant.copy(alpha = if (extra.raised) .9f else .55f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (active) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ActionBar(onSave: () -> Unit, onShare: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        Row(
            Modifier
                .weight(1f)
                .height(50.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, scheme.primary, RoundedCornerShape(16.dp))
                .clickable(onClick = onSave),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            FloatIcons.Save(tint = scheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(ctx.getString(R.string.action_save), color = scheme.primary, fontWeight = FontWeight.SemiBold)
        }
        Row(
            Modifier
                .weight(1f)
                .height(50.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.primary)
                .clickable(onClick = onShare),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            FloatIcons.Share(tint = scheme.onPrimary)
            Spacer(Modifier.width(8.dp))
            Text(ctx.getString(R.string.action_share), color = scheme.onPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            fontSize = 13.sp,
            modifier = Modifier.padding(24.dp),
        )
    }
}
