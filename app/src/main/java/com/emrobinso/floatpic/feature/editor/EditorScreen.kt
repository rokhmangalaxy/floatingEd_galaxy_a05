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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import androidx.compose.ui.graphics.asImageBitmap
import com.emrobinso.floatpic.R
import com.emrobinso.floatpic.core.model.AspectPreset
import com.emrobinso.floatpic.core.model.BackgroundKind
import com.emrobinso.floatpic.core.model.BackgroundSpec
import com.emrobinso.floatpic.core.model.EditorState
import com.emrobinso.floatpic.core.model.ScaleMode
import com.emrobinso.floatpic.core.model.TextAlign
import com.emrobinso.floatpic.core.model.TextAnchor
import com.emrobinso.floatpic.feature.ai.AiPanel
import com.emrobinso.floatpic.core.model.TextStylePreset
import com.emrobinso.floatpic.core.model.Transform
import com.emrobinso.floatpic.ui.Haptics
import com.emrobinso.floatpic.ui.icons.FloatIcons
import com.emrobinso.floatpic.ui.theme.AppTheme
import com.emrobinso.floatpic.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Tool { NONE, CROP, FRAME, BACKGROUND, TEXT, MIRROR, ROTATE, RATIO, AI, HISTORY, RESET }

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
    onRotatePreview: (Float) -> Unit,
    onRotateCommit: (Float) -> Unit,
    onResetAll: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onText: (String) -> Unit,
    onTextStyle: (TextStylePreset) -> Unit,
    onTextSize: (Float) -> Unit,
    onTextAnchor: (TextAnchor) -> Unit,
    onTextRotationPreview: (Float) -> Unit,
    onTextRotationCommit: (Float) -> Unit,
    onAiApply: (Bitmap) -> Unit,
    onInfo: (String) -> Unit,
    onPickImage: () -> Unit,
    history: List<com.emrobinso.floatpic.data.HistoryEntry>,
    onOpenHistory: (com.emrobinso.floatpic.data.HistoryEntry) -> Unit,
    onDeleteHistory: (com.emrobinso.floatpic.data.HistoryEntry) -> Unit,
    // resolves the on-disk thumbnail for an entry (entries only carry the file name)
    thumbPath: (com.emrobinso.floatpic.data.HistoryEntry) -> String,
) {
    var tool by remember { mutableStateOf(Tool.NONE) }
    var guides by remember { mutableStateOf(false) }
    // bumped on every AI open; used as the panel's `key` so reopening starts from scratch
    var aiSession by remember { mutableIntStateOf(0) }
    val extra = LocalExtra.current
    val scheme = MaterialTheme.colorScheme
    val radius = extra.cardRadius.dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
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
                .fillMaxWidth()
                // No statusBarsPadding / nav-bar spacer here: the card floats mid-screen
                // and never touches the system bars, so those insets were pure empty space
                // (the "boros space" bands above the top bar and under the buttons).
                .imePadding(),
        ) {
            TopBar(
                title = state.aspect.chipLabel(),
                dark = theme == AppTheme.MAROON_DARK,
                canUndo = canUndo,
                canRedo = canRedo,
                onClose = onClose,
                onUndo = onUndo,
                onRedo = onRedo,
                onThemeToggle = onThemeToggle,
                onMinimize = onMinimize,
            )

            // ---- STABLE ZONE -------------------------------------------------------
            // Everything here keeps its position. Only the overlay panel below moves,
            // because it is drawn *over* the preview instead of pushing it.
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                // The card hugs its content now, so there is no leftover band above the
                // preview. The square is as wide as the card, capped by the screen height
                // so a tall-aspect phone never overflows.
                val screenH = LocalConfiguration.current.screenHeightDp.toFloat()
                val side = min(maxWidth.value, screenH * 0.42f).dp
                // tap on the empty margin of the square closes the open panel; a tap on
                // the image itself is consumed by the preview below.
                val closePanel = { if (tool != Tool.NONE) tool = Tool.NONE }

                Box(
                    Modifier
                        .size(side)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(.34f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(.14f),
                                ),
                            ),
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(.30f),
                            RoundedCornerShape(12.dp),
                        )
                        .pointerInput(Unit) { detectTapGestures { closePanel() } },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loading -> LoadingState()
                        error != null -> ErrorState(error)
                        bitmap == null -> EmptyState(onPickImage)
                        else -> EditorViewport(
                            state = state,
                            bitmap = bitmap,
                            haptics = haptics,
                            showGuides = guides || tool == Tool.CROP || tool == Tool.FRAME,
                            onPreview = onPreview,
                            onCommit = onCommit,
                            onTapOutside = closePanel,
                            // inset so the ratio window never touches the square frame
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp),
                        )
                    }

                    // the only flexible element: a panel floating over the preview.
                    // It is drawn last => on top of the preview, without moving anything.
                    // plain `if` on purpose: AnimatedVisibility would resolve to the
                    // ColumnScope overload here and fight the BoxScope receiver.
                    if (tool != Tool.NONE) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 4.dp),
                        ) {
                            OverlayPanel(
                                tool = tool,
                                state = state,
                                bitmap = bitmap,
                                haptics = haptics,
                                history = history,
                                guidesOn = guides,
                                onGuides = { guides = !guides },
                                onAspect = onAspect,
                                onReset = onReset,
                                onAlign = onAlign,
                                onPadding = onPadding,
                                onCorner = onCorner,
                                onShadow = onShadow,
                                onBackground = onBackground,
                                onAmbient = onAmbient,
                                onScaleMode = onScaleMode,
                                onRotatePreview = onRotatePreview,
                                onRotateCommit = onRotateCommit,
                                onResetAll = onResetAll,
                                onFlipH = onFlipH,
                                onFlipV = onFlipV,
                                onText = onText,
                                onTextStyle = onTextStyle,
                                onTextSize = onTextSize,
                                onTextAnchor = onTextAnchor,
                                onTextRotationPreview = onTextRotationPreview,
                                onTextRotationCommit = onTextRotationCommit,
                                onAiApply = onAiApply,
                                onInfo = onInfo,
                                onDone = { tool = Tool.NONE },
                                onOpenHistory = onOpenHistory,
                                onDeleteHistory = onDeleteHistory,
                                thumbPath = thumbPath,
                            )
                        }
                    }
                }
            }

            // ---- icon row (always visible, never shifts) ---------------------------
            ToolRow(
                active = tool,
                onTool = {
                    haptics.tick()
                    val next = if (tool == it) Tool.NONE else it
                    if (next == Tool.AI) aiSession++
                    tool = next
                },
            )

            ActionBar(onSave = onSave, onShare = onShare)
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    canUndo: Boolean,
    canRedo: Boolean,
    dark: Boolean,
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = scheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 4.dp)
                .weight(1f, fill = false),
        )
        RoundIconButton(enabled = canUndo, onClick = onUndo) {
            FloatIcons.Undo(tint = if (canUndo) scheme.onSurface else scheme.onSurface.copy(.28f))
        }
        RoundIconButton(enabled = canRedo, onClick = onRedo) {
            FloatIcons.Redo(tint = if (canRedo) scheme.onSurface else scheme.onSurface.copy(.28f))
        }
        RoundIconButton(onClick = onThemeToggle) {
            if (dark) FloatIcons.Sun(tint = scheme.onSurface)
            else FloatIcons.Moon(tint = scheme.onSurface)
        }
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
private fun RatioChips(
    selected: AspectPreset,
    onSelect: (AspectPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RatioOrder.forEach { preset ->
                val active = preset == selected
                Text(
                    text = preset.chipLabel(),
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) scheme.primary else scheme.surfaceVariant.copy(.55f))
                        .border(
                            1.dp,
                            if (active) Color.Transparent else scheme.outline.copy(.35f),
                            RoundedCornerShape(50),
                        )
                        .clickable { onSelect(preset) }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun RowScope.ToolSlot(
    t: Tool,
    active: Tool,
    onTool: (Tool) -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val tint = if (active == t) scheme.onPrimary else scheme.onSurface

    val label = when (t) {
        Tool.RATIO -> ctx.getString(R.string.tool_ratio)
        Tool.CROP -> ctx.getString(R.string.tool_crop)
        Tool.FRAME -> ctx.getString(R.string.tool_frame)
        Tool.BACKGROUND -> ctx.getString(R.string.tool_background)
        Tool.TEXT -> ctx.getString(R.string.tool_text)
        Tool.MIRROR -> ctx.getString(R.string.tool_mirror)
        Tool.ROTATE -> ctx.getString(R.string.tool_rotate)
        Tool.AI -> ctx.getString(R.string.tool_ai)
        Tool.HISTORY -> ctx.getString(R.string.history_short)
        Tool.RESET -> ctx.getString(R.string.action_reset_all)
        Tool.NONE -> ""
    }
    if (t == Tool.NONE) return

    toolItem(label, active == t, {
        when (t) {
            Tool.RATIO -> FloatIcons.Ratio(tint = tint)
            Tool.CROP -> FloatIcons.Crop(tint = tint)
            Tool.FRAME -> FloatIcons.Position(tint = tint)
            Tool.BACKGROUND -> FloatIcons.Background(tint = tint)
            Tool.TEXT -> FloatIcons.TextIcon(tint = tint)
            Tool.MIRROR -> FloatIcons.MirrorH(tint = tint)
            Tool.ROTATE -> FloatIcons.RotateTool(tint = tint)
            Tool.AI -> FloatIcons.Sparkle(tint = tint)
            Tool.HISTORY -> FloatIcons.History(tint = tint)
            Tool.RESET -> FloatIcons.ResetAll(tint = tint)
            Tool.NONE -> Unit
        }
    }) { onTool(t) }
}

@Composable
private fun ToolRow(active: Tool, onTool: (Tool) -> Unit) {
    // ONE horizontally scrollable dock instead of the cramped 2x5 grid. Chip style (rounded
    // surface + label-free icon) makes the row read as a real toolbar.
    val order = listOf(
        Tool.RATIO, Tool.CROP, Tool.FRAME, Tool.BACKGROUND, Tool.TEXT,
        Tool.MIRROR, Tool.ROTATE, Tool.AI, Tool.HISTORY, Tool.RESET,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        order.forEach { t -> ToolSlot(t, active, onTool) }
    }
}

/** One dock chip: rounded surface, fixed square, so the scroll row never jitters. */
@Composable
private fun RowScope.toolItem(
    label: String,
    active: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (active) scheme.primary
                else scheme.surface.copy(alpha = 0.55f),
            )
            .border(
                width = 1.dp,
                color = if (active) Color.Transparent else scheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(13.dp),
            )
            .semantics { contentDescription = label }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
        if (active) {
            // keeps the icon row visually "selected" without adding labels
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(width = 12.dp, height = 2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(scheme.onPrimary),
            )
        }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryStrip(
    history: List<com.emrobinso.floatpic.data.HistoryEntry>,
    onOpen: (com.emrobinso.floatpic.data.HistoryEntry) -> Unit,
    onDelete: (com.emrobinso.floatpic.data.HistoryEntry) -> Unit,
    bare: Boolean = false,
    thumbPath: (com.emrobinso.floatpic.data.HistoryEntry) -> String,
) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = if (bare) 0.dp else 10.dp)) {
        if (!bare) {
            Text(
                ctx.getString(R.string.history_title, history.size),
                fontSize = 10.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp, start = 2.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            history.forEach { entry ->
                var thumb by remember(entry.id) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(entry.id) {
                    thumb = withContext(Dispatchers.IO) {
                        android.graphics.BitmapFactory.decodeFile(thumbPath(entry))
                    }
                }
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.surfaceVariant)
                        .border(1.dp, scheme.outline.copy(.35f), RoundedCornerShape(10.dp))
                        .combinedClickable(
                            onClick = { onOpen(entry) },
                            onLongClickLabel = ctx.getString(R.string.history_delete),
                            onLongClick = { onDelete(entry) },
                        ),
                ) {
                    val image = thumb
                    if (image != null) {
                        androidx.compose.foundation.Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = ctx.getString(R.string.history_open),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
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
private fun EmptyState(onPick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            FloatIcons.Gallery(tint = scheme.primary, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                ctx.getString(R.string.empty_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = scheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                ctx.getString(R.string.empty_body),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.primary)
                    .clickable(onClick = onPick)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(ctx.getString(R.string.pick_image), color = scheme.onPrimary, fontSize = 12.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Tool panels
// ---------------------------------------------------------------------------------------

@Composable
private fun OverlayPanel(
    tool: Tool,
    state: EditorState,
    bitmap: Bitmap?,
    haptics: Haptics,
    history: List<com.emrobinso.floatpic.data.HistoryEntry>,
    guidesOn: Boolean,
    onGuides: () -> Unit,
    onAspect: (AspectPreset) -> Unit,
    onOpenHistory: (com.emrobinso.floatpic.data.HistoryEntry) -> Unit,
    onDeleteHistory: (com.emrobinso.floatpic.data.HistoryEntry) -> Unit,
    thumbPath: (com.emrobinso.floatpic.data.HistoryEntry) -> String,
    onReset: () -> Unit,
    onAlign: (Float, Float) -> Unit,
    onPadding: (Float) -> Unit,
    onCorner: (Float) -> Unit,
    onShadow: () -> Unit,
    onBackground: (BackgroundSpec) -> Unit,
    onAmbient: () -> Unit,
    onScaleMode: (ScaleMode) -> Unit,
    onRotatePreview: (Float) -> Unit,
    onRotateCommit: (Float) -> Unit,
    onResetAll: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onText: (String) -> Unit,
    onTextStyle: (TextStylePreset) -> Unit,
    onTextSize: (Float) -> Unit,
    onTextAnchor: (TextAnchor) -> Unit,
    onTextRotationPreview: (Float) -> Unit,
    onTextRotationCommit: (Float) -> Unit,
    onAiApply: (Bitmap) -> Unit,
    onInfo: (String) -> Unit,
    onDone: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = scheme.surface.copy(alpha = 0.97f),
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    AlignGrid { fx, fy -> haptics.snap(); onAlign(fx, fy) }
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

            Tool.TEXT -> TextPanel(
                state = state,
                onText = onText,
                onTextStyle = onTextStyle,
                onTextSize = onTextSize,
                onTextAnchor = onTextAnchor,
                onTextRotationPreview = onTextRotationPreview,
                onTextRotationCommit = onTextRotationCommit,
            )

            Tool.MIRROR -> {
                SectionTitle(ctx.getString(R.string.panel_mirror_title))
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MirrorTile(
                        label = ctx.getString(R.string.tool_mirror_h),
                        active = state.transform.flipH,
                        icon = { FloatIcons.MirrorH(tint = scheme.primary, modifier = Modifier.size(22.dp)) },
                        onClick = onFlipH,
                        modifier = Modifier.weight(1f),
                    )
                    MirrorTile(
                        label = ctx.getString(R.string.tool_mirror_v),
                        active = state.transform.flipV,
                        icon = { FloatIcons.MirrorV(tint = scheme.primary, modifier = Modifier.size(22.dp)) },
                        onClick = onFlipV,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Hint(ctx.getString(R.string.panel_mirror_hint))
            }

            Tool.ROTATE -> RotatePanel(
                rotation = state.transform.rotation,
                onPreview = onRotatePreview,
                onCommit = onRotateCommit,
            )

            Tool.RESET -> {
                SectionTitle(ctx.getString(R.string.panel_reset_title))
                Spacer(Modifier.height(4.dp))
                Hint(ctx.getString(R.string.panel_reset_hint))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryPill(ctx.getString(R.string.action_reset_all), onResetAll) {
                        FloatIcons.ResetAll(tint = scheme.onPrimary, modifier = Modifier.size(15.dp))
                    }
                    PillToggle("Grid", guidesOn, onGuides)
                }
            }

            Tool.AI -> key(aiSession) {
                AiPanel(
                    source = bitmap,
                    onApply = onAiApply,
                    onInfo = onInfo,
                )
            }

            Tool.RATIO -> {
                SectionTitle(ctx.getString(R.string.panel_ratio_title))
                Spacer(Modifier.height(6.dp))
                RatioChips(
                    selected = state.aspect,
                    onSelect = { haptics.tick(); onAspect(it) },
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    PillToggle(ctx.getString(R.string.label_fill), state.scaleMode == ScaleMode.FILL) {
                        onScaleMode(ScaleMode.FILL)
                    }
                    PillToggle(ctx.getString(R.string.label_fit), state.scaleMode == ScaleMode.FIT) {
                        onScaleMode(ScaleMode.FIT)
                    }
                    Spacer(Modifier.weight(1f))
                    PillToggle("Grid", guidesOn, onGuides)
                }
            }

            Tool.HISTORY -> {
                SectionTitle(ctx.getString(R.string.history_title, history.size))
                Spacer(Modifier.height(6.dp))
                if (history.isEmpty()) {
                    Hint(ctx.getString(R.string.history_empty))
                } else {
                    HistoryStrip(
                        history = history,
                        onOpen = onOpenHistory,
                        onDelete = onDeleteHistory,
                        bare = true,
                        thumbPath = thumbPath,
                    )
                }
            }

            Tool.NONE -> Unit
        }
    }
    }
}

@Composable
private fun MirrorTile(
    label: String,
    active: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) scheme.primaryContainer else scheme.surface.copy(.85f))
            .border(
                if (active) 1.5.dp else 1.dp,
                if (active) scheme.primary else scheme.outline.copy(.45f),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        icon()
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 9.sp,
            color = if (active) scheme.primary else scheme.onSurfaceVariant,
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
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    detents: Int = 0,
    onPreview: ((Float) -> Unit)? = null,
    onCommit: ((Float) -> Unit)? = null,
    onChange: (Float) -> Unit = {},
) {
    var live by remember { mutableStateOf(value) }
    LaunchedEffect(value) { live = value }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("${live.toInt()}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = live.coerceIn(range),
            onValueChange = { v ->
                live = v
                if (onPreview != null) onPreview(v) else onChange(v)
            },
            onValueChangeFinished = { onCommit?.invoke(live.coerceIn(range)) },
            valueRange = range,
            steps = detents,
            modifier = Modifier.height(26.dp),
        )
    }
}

/**
 * Rotation is its own tool: the pinch gesture no longer rotates (a two finger zoom always
 * carries a little twist, which made the picture spin while zooming).
 *
 * The slider range is user configurable — min, max and step are editable, so the tool works
 * for "±15° fine correction" as well as for "full 0..360° sweep".
 */
@Composable
private fun RotatePanel(
    rotation: Float,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current

    // range config lives with the panel; defaults cover a full turn in 1° steps
    var min by remember { mutableStateOf("-180") }
    var max by remember { mutableStateOf("180") }
    var step by remember { mutableStateOf("1") }
    var snap by remember { mutableStateOf(true) }
    // last value pushed by the slider (the `rotation` parameter is stale mid-drag)
    var lastValue by remember { mutableStateOf(rotation) }

    val minV = min.toFloatOrNull() ?: -180f
    val maxV = max.toFloatOrNull() ?: 180f
    val safeMin = minOf(minV, maxV)
    val safeMax = maxOf(minV, maxV)
    val stepV = (step.toFloatOrNull() ?: 1f).coerceIn(0.1f, 90f)

    fun quantise(value: Float): Float {
        val clamped = value.coerceIn(safeMin, safeMax)
        return if (snap) (kotlin.math.round(clamped / stepV) * stepV).coerceIn(safeMin, safeMax)
        else clamped
    }

    LaunchedEffect(rotation) { lastValue = rotation }

    SectionTitle(ctx.getString(R.string.panel_rotate_title))
    Spacer(Modifier.height(4.dp))

    // current value + quick presets
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = ctx.getString(R.string.rotation_format, rotation.toInt()),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        PillToggle("0°", kotlin.math.abs(rotation) < 0.5f) { onCommit(0f) }
        Spacer(Modifier.width(6.dp))
        PillToggle("90°", kotlin.math.abs(rotation - 90f) < 0.5f) { onCommit(90f) }
        Spacer(Modifier.width(6.dp))
        PillToggle("180°", kotlin.math.abs(rotation - 180f) < 0.5f) { onCommit(180f) }
        Spacer(Modifier.width(6.dp))
        PillToggle("270°", kotlin.math.abs(rotation - 270f) < 0.5f) { onCommit(270f) }
    }

    // Slider() throws when valueRange is empty, so never let min meet max.
    val rangeEnd = if (safeMax > safeMin) safeMax else safeMin + 1f
    // steps = number of detents BETWEEN the ends (0 = continuous). Too many would be
    // expensive to draw, so cap it.
    val detents = if (!snap) 0 else (((rangeEnd - safeMin) / stepV) - 1f).toInt().coerceIn(0, 60)

    Slider(
        value = rotation.coerceIn(safeMin, rangeEnd),
        onValueChange = {
            lastValue = quantise(it)
            onPreview(lastValue)
        },
        // commit what the caller actually holds: `rotation` is a parameter and does not
        // update during the drag, so reading it here would snap back to the start angle.
        onValueChangeFinished = { onCommit(quantise(lastValue)) },
        valueRange = safeMin..rangeEnd,
        steps = detents,
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
    )

    // custom range: min / max / step
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RangeField("min", min) { min = it }
        RangeField("max", max) { max = it }
        RangeField("step", step) { step = it }
        PillToggle("Snap", snap) { snap = !snap }
    }
    Spacer(Modifier.height(4.dp))
    Hint(ctx.getString(R.string.panel_rotate_hint))
}

/** Small numeric input used by the rotate range editor. */
@Composable
private fun RangeField(label: String, value: String, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(scheme.surfaceVariant.copy(.55f))
            .border(1.dp, scheme.outline.copy(.35f), RoundedCornerShape(9.dp))
            .padding(horizontal = 7.dp),
    ) {
        Text(label, fontSize = 9.sp, color = scheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        BasicTextField(
            value = value,
            onValueChange = { raw -> onChange(raw.filter { it.isDigit() || it == '-' || it == '.' }.take(6)) },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            ),
            modifier = Modifier.width(34.dp),
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
    onTextStyle: (TextStylePreset) -> Unit,
    onTextSize: (Float) -> Unit,
    onTextAnchor: (TextAnchor) -> Unit,
    onTextRotationPreview: (Float) -> Unit,
    onTextRotationCommit: (Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var value by remember { mutableStateOf(state.text.text) }
    // NOTE: no auto-focus on purpose. Requesting focus when the panel opens popped the
    // keyboard immediately, which covered most of the floating card.

    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(ctx.getString(R.string.panel_text_title))
        Spacer(Modifier.weight(1f))
        // Paste lives next to the title, where it is always visible — the old one was
        // squeezed into the chip row and effectively disappeared.
        Box(
            Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(scheme.primary.copy(alpha = 0.16f))
                .border(1.dp, scheme.primary.copy(alpha = 0.55f), RoundedCornerShape(9.dp))
                .padding(horizontal = 9.dp)
                .semantics { contentDescription = ctx.getString(R.string.action_paste) }
                .clickable {
                    val pasted = clipboard.getText()?.text?.replace("\n", " ")?.trim()
                    if (!pasted.isNullOrBlank()) {
                        val next = pasted.take(120)
                        value = next
                        onText(next)
                        keyboard?.hide()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FloatIcons.Paste(tint = scheme.primary)
                Spacer(Modifier.width(5.dp))
                Text(
                    ctx.getString(R.string.action_paste),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                )
            }
        }
    }
    Spacer(Modifier.height(5.dp))

    // two-line caption box (the old single line hid long captions behind a scroll)
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.outline.copy(.5f), RoundedCornerShape(11.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        contentAlignment = Alignment.TopStart,
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
                if (it.length <= 120) {
                    value = it
                    onText(it)
                }
            },
            textStyle = TextStyle(fontSize = 12.sp, color = scheme.onSurface),
            singleLine = false,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(6.dp))

    // POSITION: the same 3x3 dot grid as the picture-position tool, but for the caption.
    // Top row = above, middle = centred, bottom = below; left/centre/right horizontally.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            ctx.getString(R.string.label_text_position),
            fontSize = 10.sp,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        PositionGrid(selected = state.text.anchor, onPick = onTextAnchor)
        Spacer(Modifier.weight(1f))
        StyleChip("A", Color.White, Color.Black, state.text.style == TextStylePreset.WHITE_ON_BLACK) {
            onTextStyle(TextStylePreset.WHITE_ON_BLACK)
        }
        Spacer(Modifier.width(5.dp))
        StyleChip("A", Color.Black, Color.White, state.text.style == TextStylePreset.BLACK_ON_WHITE) {
            onTextStyle(TextStylePreset.BLACK_ON_WHITE)
        }
    }
    Spacer(Modifier.height(4.dp))
    SliderRow(
        ctx.getString(R.string.label_size),
        state.text.sizeFraction * 1000f,
        30f..140f,
    ) { onTextSize(it / 1000f) }
    // rotation is a plain slider now: -180..180 in 15 degree detents
    SliderRow(
        ctx.getString(R.string.label_text_rotation),
        state.text.rotation,
        -180f..180f,
        onPreview = onTextRotationPreview,
        onCommit = onTextRotationCommit,
        detents = 23,
    )
}

/** Compact 3x3 position picker; the same language as the picture-position grid. */
@Composable
private fun PositionGrid(selected: TextAnchor, onPick: (TextAnchor) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        TextAnchor.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { anchor ->
                    val active = selected == anchor
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) scheme.primary else scheme.surface)
                            .border(
                                1.dp,
                                if (active) Color.Transparent else scheme.outline.copy(.45f),
                                RoundedCornerShape(6.dp),
                            )
                            .clickable { onPick(anchor) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(if (active) 5.dp else 4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) scheme.onPrimary else scheme.onSurfaceVariant.copy(.45f),
                                ),
                        )
                    }
                }
            }
        }
    }
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
