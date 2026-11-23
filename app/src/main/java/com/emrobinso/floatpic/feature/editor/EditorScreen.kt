package com.emrobinso.floatpic.feature.editor

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emrobinso.floatpic.R
import com.emrobinso.floatpic.core.model.AspectPreset
import com.emrobinso.floatpic.core.model.BackgroundKind
import com.emrobinso.floatpic.core.model.BackgroundSpec
import com.emrobinso.floatpic.core.model.EditorState
import com.emrobinso.floatpic.core.model.ScaleMode
import com.emrobinso.floatpic.core.model.TextPosition
import com.emrobinso.floatpic.core.model.TextStylePreset
import com.emrobinso.floatpic.core.model.Transform
import com.emrobinso.floatpic.ui.Haptics
import com.emrobinso.floatpic.ui.icons.FloatIcons
import com.emrobinso.floatpic.ui.theme.AppTheme
import com.emrobinso.floatpic.ui.theme.LocalExtra
import kotlinx.coroutines.delay

enum class Tool { NONE, CROP, FRAME, BACKGROUND, TEXT }

/** Label shown on the ratio chip. */
fun AspectPreset.chipLabel(): String = when (this) {
    AspectPreset.ORIGINAL -> "Asli"
    AspectPreset.SQUARE -> "1:1"
    AspectPreset.PORTRAIT_4_5 -> "4:5"
    AspectPreset.STORY_9_16 -> "9:16"
    AspectPreset.LANDSCAPE_16_9 -> "16:9"
    AspectPreset.CLASSIC_3_2 -> "3:2"
    AspectPreset.CLASSIC_4_3 -> "4:3"
    AspectPreset.PORTRAIT_2_3 -> "2:3"
    AspectPreset.PORTRAIT_3_4 -> "3:4"
    AspectPreset.FIVE_4 -> "5:4"
    AspectPreset.PHONE_9_20 -> "9:20"
}

private val RatioOrder = listOf(
    AspectPreset.ORIGINAL,
    AspectPreset.SQUARE,
    AspectPreset.PORTRAIT_4_5,
    AspectPreset.STORY_9_16,
    AspectPreset.LANDSCAPE_16_9,
    AspectPreset.CLASSIC_3_2,
    AspectPreset.CLASSIC_4_3,
    AspectPreset.PORTRAIT_2_3,
    AspectPreset.PORTRAIT_3_4,
    AspectPreset.FIVE_4,
    AspectPreset.PHONE_9_20,
)

/**
 * The floating editor card (docs/02 L1). One screen: preview, ratio chips, tools, CTA.
 * Kept standalone so an overlay host can reuse the same content later.
 */
@Composable
fun EditorScreen(
    state: EditorState,
    bitmap: Bitmap?,
    theme: AppTheme,
    loading: Boolean,
    error: String?,
    canUndo: Boolean,
    canRedo: Boolean,
    haptics: Haptics,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onThemeToggle: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAspect: (AspectPreset) -> Unit,
    onPreview: (Transform) -> Unit,
    onCommit: (Transform) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onReset: () -> Unit,
    onAlign: (Float, Float) -> Unit,
    onPadding: (Float) -> Unit,
    onCorner: (Float) -> Unit,
    onShadow: () -> Unit,
    onBackground: (BackgroundSpec) -> Unit,
    onAmbient: () -> Unit,
    onScaleMode: (ScaleMode) -> Unit,
    onRotate: (Float) -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onText: (String) -> Unit,
    onTextPosition: (TextPosition) -> Unit,
    onTextStyle: (TextStylePreset) -> Unit,
    onTextSize: (Float) -> Unit,
) {
    var tool by remember { mutableStateOf(Tool.NONE) }
    var guides by remember { mutableStateOf(false) }
    val extra = LocalExtra.current
    val scheme = MaterialTheme.colorScheme
    val radius = extra.cardRadius.dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(if (extra.raised) 4.dp else 22.dp, RoundedCornerShape(radius), clip = false)
            .border(
                width = if (extra.raised) 1.dp else 0.dp,
                color = if (extra.raised) extra.softLight.copy(alpha = .9f) else Color.Transparent,
                shape = RoundedCornerShape(radius),
            ),
        shape = RoundedCornerShape(radius),
        color = scheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            TopBar(
                title = state.aspect.chipLabel(),
                canUndo = canUndo,
                canRedo = canRedo,
                onClose = onClose,
                onUndo = onUndo,
                onRedo = onRedo,
                onThemeToggle = onThemeToggle,
                onMinimize = onMinimize,
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                when {
                    loading -> LoadingState()
                    error != null -> ErrorState(error)
                    bitmap == null -> EmptyState()
                    else -> EditorViewport(
                        state = state,
                        bitmap = bitmap,
                        haptics = haptics,
                        showGuides = guides || tool == Tool.CROP || tool == Tool.FRAME,
                        onPreview = onPreview,
                        onCommit = onCommit,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }

            RatioChips(selected = state.aspect) { haptics.tick(); onAspect(it) }

            ToolRow(active = tool) { haptics.tick(); tool = if (tool == it) Tool.NONE else it }

            AnimatedVisibility(
                visible = tool != Tool.NONE,
                enter = slideInVertically { it / 3 } + fadeIn(),
                exit = slideOutVertically { it / 3 } + fadeOut(),
            ) {
                ToolPanel(
                    tool = tool,
                    state = state,
                    onReset = onReset,
                    onAlign = onAlign,
                    onPadding = onPadding,
                    onCorner = onCorner,
                    onShadow = onShadow,
                    onBackground = onBackground,
                    onAmbient = onAmbient,
                    onScaleMode = onScaleMode,
                    onRotate = onRotate,
                    onFlipH = onFlipH,
                    onFlipV = onFlipV,
                    onText = onText,
                    onTextPosition = onTextPosition,
                    onTextStyle = onTextStyle,
                    onTextSize = onTextSize,
                    onGuides = { guides = !guides },
                    guidesOn = guides,
                    onDone = { tool = Tool.NONE },
                )
            }

            ActionBar(onSave = onSave, onShare = onShare)
            Spacer(
                Modifier.height(
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() * 0.6f,
                ),
            )
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onThemeToggle: () -> Unit,
    onMinimize: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(onClick = onClose) { FloatIcons.Close(tint = scheme.onSurface) }
        Text(
            text = "FloatPic",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            color = scheme.onSurface,
            modifier = Modifier.padding(start = 4.dp),
        )
        Text(
            text = "· $title",
            fontSize = 12.sp,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        RoundIconButton(enabled = canUndo, onClick = onUndo) {
            FloatIcons.Undo(tint = if (canUndo) scheme.onSurface else scheme.onSurface.copy(.28f))
        }
        RoundIconButton(enabled = canRedo, onClick = onRedo) {
            FloatIcons.Redo(tint = if (canRedo) scheme.onSurface else scheme.onSurface.copy(.28f))
        }
        RoundIconButton(onClick = onThemeToggle) { FloatIcons.Palette(tint = scheme.onSurface) }
        RoundIconButton(onClick = onMinimize) { FloatIcons.Minimize(tint = scheme.onSurface) }
    }
}

@Composable
fun RoundIconButton(enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun RatioChips(selected: AspectPreset, onSelect: (AspectPreset) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        RatioOrder.forEach { preset ->
            val active = preset == selected
            Text(
                text = preset.chipLabel(),
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (active) scheme.primary else scheme.surfaceVariant.copy(.65f))
                    .border(
                        1.dp,
                        if (active) Color.Transparent else scheme.outline.copy(.4f),
                        RoundedCornerShape(50),
                    )
                    .clickable { onSelect(preset) }
                    .padding(horizontal = 13.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun ToolRow(active: Tool, onTool: (Tool) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ToolButton(ctx.getString(R.string.tool_crop), active == Tool.CROP,
            { FloatIcons.Crop(tint = if (active == Tool.CROP) scheme.onPrimary else scheme.onSurface) },
        ) { onTool(Tool.CROP) }
        ToolButton(ctx.getString(R.string.tool_frame), active == Tool.FRAME,
            { FloatIcons.Position(tint = if (active == Tool.FRAME) scheme.onPrimary else scheme.onSurface) },
        ) { onTool(Tool.FRAME) }
        ToolButton(ctx.getString(R.string.tool_background), active == Tool.BACKGROUND,
            { FloatIcons.Background(tint = if (active == Tool.BACKGROUND) scheme.onPrimary else scheme.onSurface) },
        ) { onTool(Tool.BACKGROUND) }
        ToolButton(ctx.getString(R.string.tool_text), active == Tool.TEXT,
            { FloatIcons.TextIcon(tint = if (active == Tool.TEXT) scheme.onPrimary else scheme.onSurface) },
        ) { onTool(Tool.TEXT) }
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
    val bg = if (active) scheme.primary else scheme.surfaceVariant.copy(if (extra.raised) .9f else .5f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(74.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(bg),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = if (active) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ActionBar(onSave: () -> Unit, onShare: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .border(1.5.dp, scheme.primary, RoundedCornerShape(15.dp))
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
                .height(46.dp)
                .clip(RoundedCornerShape(15.dp))
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
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = LocalContext.current.getString(R.string.no_image),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(24.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------
// Tool panels
// ---------------------------------------------------------------------------------------

@Composable
private fun ToolPanel(
    tool: Tool,
    state: EditorState,
    onReset: () -> Unit,
    onAlign: (Float, Float) -> Unit,
    onPadding: (Float) -> Unit,
    onCorner: (Float) -> Unit,
    onShadow: () -> Unit,
    onBackground: (BackgroundSpec) -> Unit,
    onAmbient: () -> Unit,
    onScaleMode: (ScaleMode) -> Unit,
    onRotate: (Float) -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onText: (String) -> Unit,
    onTextPosition: (TextPosition) -> Unit,
    onTextStyle: (TextStylePreset) -> Unit,
    onTextSize: (Float) -> Unit,
    onGuides: () -> Unit,
    guidesOn: Boolean,
    onDone: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(scheme.surfaceVariant.copy(.4f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        when (tool) {
            Tool.CROP -> {
                SectionTitle(ctx.getString(R.string.panel_crop_title))
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    PillToggle(ctx.getString(R.string.label_fill), state.scaleMode == ScaleMode.FILL) {
                        onScaleMode(ScaleMode.FILL)
                    }
                    PillToggle(ctx.getString(R.string.label_fit), state.scaleMode == ScaleMode.FIT) {
                        onScaleMode(ScaleMode.FIT)
                    }
                    Spacer(Modifier.weight(1f))
                    PrimaryPill(ctx.getString(R.string.action_reset), onReset) {
                        FloatIcons.Grid(tint = scheme.onPrimary, modifier = Modifier.size(15.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Hint(ctx.getString(R.string.panel_crop_hint))
            }

            Tool.FRAME -> {
                SectionTitle(ctx.getString(R.string.panel_frame_title))
                Hint(ctx.getString(R.string.panel_frame_hint))
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AlignGrid(onAlign)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        SliderRow(ctx.getString(R.string.label_padding), state.paddingDp, 0f..160f, onPadding)
                        SliderRow(ctx.getString(R.string.label_corner), state.cornerDp, 0f..96f, onCorner)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            PillToggle("Shadow", state.shadow, onShadow)
                            PillToggle("Grid", guidesOn, onGuides)
                        }
                    }
                }
            }

            Tool.BACKGROUND -> {
                SectionTitle(ctx.getString(R.string.panel_background_title))
                Spacer(Modifier.height(5.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    BgKindChip(ctx.getString(R.string.label_auto), false, onAmbient)
                    BackgroundKind.entries.forEach { kind ->
                        val label = when (kind) {
                            BackgroundKind.COLOR -> ctx.getString(R.string.label_solid)
                            BackgroundKind.GRADIENT -> ctx.getString(R.string.label_gradient)
                            BackgroundKind.BLUR -> ctx.getString(R.string.label_blur)
                            BackgroundKind.PATTERN -> "Pattern"
                        }
                        BgKindChip(label, state.background.kind == kind) {
                            onBackground(state.background.copy(kind = kind))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Palette.swatches.forEach { colors ->
                        val selected = state.background.kind == BackgroundKind.GRADIENT &&
                            state.background.color == colors.first() &&
                            state.background.color2 == colors.last()
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(colors.map { Color(it) }))
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) scheme.primary else scheme.outline.copy(.4f),
                                    CircleShape,
                                )
                                .clickable {
                                    onBackground(
                                        state.background.copy(
                                            kind = BackgroundKind.GRADIENT,
                                            color = colors.first(),
                                            color2 = colors.last(),
                                        ),
                                    )
                                },
                        )
                    }
                }
            }

            Tool.TEXT -> TextPanel(state, onText, onTextPosition, onTextStyle, onTextSize)
            Tool.NONE -> Unit
        }

        if (tool != Tool.NONE) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniAction("-90°") { FloatIcons.Rotate(tint = scheme.onSurface, modifier = Modifier.size(19.dp)) } {
                    onRotate(-90f)
                }
                MiniAction("+90°") { FloatIcons.Rotate(tint = scheme.onSurface, modifier = Modifier.size(19.dp)) } {
                    onRotate(90f)
                }
                MiniAction(ctx.getString(R.string.tool_mirror_h)) {
                    FloatIcons.MirrorH(tint = scheme.onSurface, modifier = Modifier.size(19.dp))
                } { onFlipH() }
                MiniAction(ctx.getString(R.string.tool_mirror_v)) {
                    FloatIcons.MirrorV(tint = scheme.onSurface, modifier = Modifier.size(19.dp))
                } { onFlipV() }
                MiniAction(ctx.getString(R.string.action_close), primary = true) {
                    FloatIcons.Check(tint = scheme.onPrimary, modifier = Modifier.size(19.dp))
                } { onDone() }
            }
        }
    }
}

@Composable
private fun MiniAction(
    label: String,
    primary: Boolean = false,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) scheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        icon()
        Text(
            label,
            fontSize = 9.sp,
            color = if (primary) scheme.onPrimary else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

object Palette {
    val swatches: List<List<Long>> = listOf(
        listOf(0xFF7A0C2E, 0xFF3E101B),
        listOf(0xFF1C4A4A, 0xFF0B2626),
        listOf(0xFF2A2F3A, 0xFF10131A),
        listOf(0xFFF6E7E4, 0xFFE1C3BE),
        listOf(0xFFC1121F, 0xFF7A0C2E),
        listOf(0xFF3D405B, 0xFF81A4A8),
        listOf(0xFFE9C46A, 0xFFE76F51),
        listOf(0xFF118A7E, 0xFF17252A),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PillToggle(label: String, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = label,
        fontSize = 11.sp,
        color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) scheme.primary else Color.Transparent)
            .border(1.dp, if (active) Color.Transparent else scheme.outline.copy(.5f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun PrimaryPill(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(scheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(5.dp))
        Text(label, color = scheme.onPrimary, fontSize = 11.sp)
    }
}

@Composable
private fun BgKindChip(label: String, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = label,
        fontSize = 11.sp,
        color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) scheme.primary else scheme.surface.copy(.8f))
            .border(1.dp, if (active) Color.Transparent else scheme.outline.copy(.4f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("${value.toInt()}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.height(26.dp),
        )
    }
}

/** 3x3 align grid: relative positioning of the image inside its frame. */
@Composable
private fun AlignGrid(onAlign: (Float, Float) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(0f, 0.5f, 1f).forEach { fy ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(0f, 0.5f, 1f).forEach { fx ->
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(scheme.surface.copy(.85f))
                            .border(1.dp, scheme.outline.copy(.45f), RoundedCornerShape(7.dp))
                            .clickable { onAlign(fx, fy) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (fx == 0.5f && fy == 0.5f) scheme.primary
                                    else scheme.onSurfaceVariant.copy(.45f),
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextPanel(
    state: EditorState,
    onText: (String) -> Unit,
    onTextPosition: (TextPosition) -> Unit,
    onTextStyle: (TextStylePreset) -> Unit,
    onTextSize: (Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val focus = remember { FocusRequester() }
    var value by remember { mutableStateOf(state.text.text) }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focus.requestFocus() }
    }

    SectionTitle(ctx.getString(R.string.panel_text_title))
    Spacer(Modifier.height(5.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.outline.copy(.5f), RoundedCornerShape(11.dp))
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                ctx.getString(R.string.text_hint),
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant.copy(.55f),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = {
                if (it.length <= 80) {
                    value = it
                    onText(it)
                }
            },
            textStyle = TextStyle(fontSize = 12.sp, color = scheme.onSurface),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
    }
    Spacer(Modifier.height(7.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        PillToggle(ctx.getString(R.string.text_position_top), state.text.position == TextPosition.TOP_CENTER) {
            onTextPosition(TextPosition.TOP_CENTER)
        }
        PillToggle(ctx.getString(R.string.text_position_bottom), state.text.position == TextPosition.BOTTOM_CENTER) {
            onTextPosition(TextPosition.BOTTOM_CENTER)
        }
        Spacer(Modifier.weight(1f))
        StyleChip("A", Color.White, Color.Black, state.text.style == TextStylePreset.WHITE_ON_BLACK) {
            onTextStyle(TextStylePreset.WHITE_ON_BLACK)
        }
        StyleChip("A", Color.Black, Color.White, state.text.style == TextStylePreset.BLACK_ON_WHITE) {
            onTextStyle(TextStylePreset.BLACK_ON_WHITE)
        }
    }
    SliderRow(
        ctx.getString(R.string.label_size),
        state.text.sizeFraction * 1000f,
        30f..140f,
    ) { onTextSize(it / 1000f) }
}

@Composable
private fun StyleChip(text: String, fill: Color, stroke: Color, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) scheme.primaryContainer else scheme.surface)
            .border(
                if (active) 1.5.dp else 1.dp,
                if (active) scheme.primary else scheme.outline.copy(.5f),
                RoundedCornerShape(9.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // stroke then fill: exactly how the export renderer paints it
        Text(
            text = text,
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = stroke,
                drawStyle = Stroke(width = 3f, join = StrokeJoin.Round),
            ),
        )
        Text(
            text = text,
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = fill,
            ),
        )
    }
}
