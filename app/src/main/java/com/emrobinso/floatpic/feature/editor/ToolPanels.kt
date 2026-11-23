package com.emrobinso.floatpic.feature.editor

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emrobinso.floatpic.R
import com.emrobinso.floatpic.core.model.AlignAnchor
import com.emrobinso.floatpic.core.model.BackgroundKind
import com.emrobinso.floatpic.core.model.BackgroundSpec
import com.emrobinso.floatpic.core.model.EditorState
import com.emrobinso.floatpic.core.model.ScaleMode
import com.emrobinso.floatpic.core.model.TextPosition
import com.emrobinso.floatpic.core.model.TextStylePreset
import com.emrobinso.floatpic.ui.icons.FloatIcons
import kotlinx.coroutines.delay

@Composable
fun ToolPanel(
    tool: Tool,
    state: EditorState,
    hasBitmap: Boolean,
    onCropApply: () -> Unit,
    onFrameAlign: (Float, Float) -> Unit,
    onPadding: (Float) -> Unit,
    onCorner: (Float) -> Unit,
    onShadow: () -> Unit,
    onBackground: (BackgroundSpec) -> Unit,
    onAmbient: () -> Unit,
    onScaleMode: (ScaleMode) -> Unit,
    onRotate90: (Float) -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onText: (String) -> Unit,
    onTextPosition: (TextPosition) -> Unit,
    onTextStyle: (TextStylePreset) -> Unit,
    onTextSize: (Float) -> Unit,
    onTextCommit: () -> Unit,
    onDone: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(scheme.surfaceVariant.copy(alpha = .45f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        when (tool) {
            Tool.CROP -> {
                SectionTitle(ctx.getString(R.string.panel_crop_title))
                Hint(ctx.getString(R.string.panel_crop_hint))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillToggle(
                        label = ctx.getString(R.string.label_fill),
                        active = state.scaleMode == ScaleMode.FILL,
                        onClick = { onScaleMode(ScaleMode.FILL) },
                    )
                    PillToggle(
                        label = ctx.getString(R.string.label_fit),
                        active = state.scaleMode == ScaleMode.FIT,
                        onClick = { onScaleMode(ScaleMode.FIT) },
                    )
                    Spacer(Modifier.weight(1f))
                    PrimaryPill(
                        label = ctx.getString(R.string.action_reset),
                        onClick = onCropApply,
                    ) { FloatIcons.Grid(tint = scheme.onPrimary, modifier = Modifier.size(15.dp)) }
                }
            }

            Tool.FRAME -> {
                SectionTitle(ctx.getString(R.string.panel_frame_title))
                Hint(ctx.getString(R.string.panel_frame_hint))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AlignGrid(onFrameAlign)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        SliderRow(
                            label = ctx.getString(R.string.label_padding),
                            value = state.paddingDp,
                            range = 0f..160f,
                            onChange = onPadding,
                        )
                        SliderRow(
                            label = ctx.getString(R.string.label_corner),
                            value = state.cornerDp,
                            range = 0f..96f,
                            onChange = onCorner,
                        )
                        PillToggle(
                            label = "Shadow",
                            active = state.shadow,
                            onClick = onShadow,
                        )
                    }
                }
            }

            Tool.BACKGROUND -> {
                SectionTitle(ctx.getString(R.string.panel_background_title))
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BgKindChip(ctx.getString(R.string.label_auto), false) { onAmbient() }
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
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Palette.swatches.forEach { colors ->
                        val selected = state.background.kind == BackgroundKind.GRADIENT &&
                            state.background.color == colors.first() &&
                            state.background.color2 == colors.last()
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(colors.map { Color(it) }))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) scheme.primary else scheme.outline.copy(.4f),
                                    shape = CircleShape,
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
                onTextPosition = onTextPosition,
                onTextStyle = onTextStyle,
                onTextSize = onTextSize,
                onTextCommit = onTextCommit,
            )

            Tool.NONE -> Unit
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            LabeledMini(
                label = "-90°",
                icon = { FloatIcons.Rotate(tint = scheme.onSurface, modifier = Modifier.size(20.dp)) },
                onClick = { onRotate90(-90f) },
            )
            LabeledMini(
                label = "+90°",
                icon = { FloatIcons.Rotate(tint = scheme.onSurface, modifier = Modifier.size(20.dp)) },
                onClick = { onRotate90(90f) },
            )
            LabeledMini(
                label = ctx.getString(R.string.tool_mirror_h),
                icon = { FloatIcons.MirrorH(tint = scheme.onSurface, modifier = Modifier.size(20.dp)) },
                onClick = onFlipH,
            )
            LabeledMini(
                label = ctx.getString(R.string.tool_mirror_v),
                icon = { FloatIcons.MirrorV(tint = scheme.onSurface, modifier = Modifier.size(20.dp)) },
                onClick = onFlipV,
            )
            LabeledMini(
                label = ctx.getString(R.string.action_close),
                icon = { FloatIcons.Check(tint = scheme.onSurface, modifier = Modifier.size(20.dp)) },
                onClick = onDone,
            )
        }
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
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 14.sp,
    )
}

@Composable
private fun PillToggle(label: String, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = label,
        fontSize = 12.sp,
        color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) scheme.primary else Color.Transparent)
            .border(1.dp, if (active) Color.Transparent else scheme.outline.copy(.5f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun PrimaryPill(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(scheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(label, color = scheme.onPrimary, fontSize = 12.sp)
    }
}

@Composable
private fun BgKindChip(label: String, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = label,
        fontSize = 12.sp,
        color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) scheme.primary else scheme.surfaceVariant.copy(.8f))
            .border(1.dp, if (active) Color.Transparent else scheme.outline.copy(.4f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    )
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("${value.toInt()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.height(28.dp),
        )
    }
}

/** 3x3 align grid — relative positioning of the image inside its frame. */
@Composable
private fun AlignGrid(onAlign: (Float, Float) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(0f, 0.5f, 1f).forEach { fy ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0f, 0.5f, 1f).forEach { fx ->
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.surface.copy(alpha = .8f))
                            .border(1.dp, scheme.outline.copy(.5f), RoundedCornerShape(8.dp))
                            .clickable { onAlign(fx, fy) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (fx == 0.5f && fy == 0.5f) scheme.primary else scheme.onSurfaceVariant.copy(.5f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledMini(label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        icon()
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun TextPanel(
    state: EditorState,
    onText: (String) -> Unit,
    onTextPosition: (TextPosition) -> Unit,
    onTextStyle: (TextStylePreset) -> Unit,
    onTextSize: (Float) -> Unit,
    onTextCommit: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val focus = remember { FocusRequester() }
    var value by remember(state.text.text) { mutableStateOf(state.text.text) }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
    }

    SectionTitle(ctx.getString(R.string.panel_text_title))
    Spacer(Modifier.height(6.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.outline.copy(.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                ctx.getString(R.string.text_hint),
                fontSize = 13.sp,
                color = scheme.onSurfaceVariant.copy(.6f),
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
            textStyle = TextStyle(fontSize = 13.sp, color = scheme.onSurface),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PillToggle(ctx.getString(R.string.text_position_top), state.text.position == TextPosition.TOP_CENTER) {
            onTextPosition(TextPosition.TOP_CENTER); onTextCommit()
        }
        PillToggle(ctx.getString(R.string.text_position_bottom), state.text.position == TextPosition.BOTTOM_CENTER) {
            onTextPosition(TextPosition.BOTTOM_CENTER); onTextCommit()
        }
        Spacer(Modifier.weight(1f))
        StyleChip("A", Color.White, Color.Black, state.text.style == TextStylePreset.WHITE_ON_BLACK) {
            onTextStyle(TextStylePreset.WHITE_ON_BLACK); onTextCommit()
        }
        StyleChip("A", Color.Black, Color.White, state.text.style == TextStylePreset.BLACK_ON_WHITE) {
            onTextStyle(TextStylePreset.BLACK_ON_WHITE); onTextCommit()
        }
    }
    SliderRow(
        label = ctx.getString(R.string.label_size),
        value = state.text.sizeFraction * 1000f,
        range = 35f..140f,
        onChange = { onTextSize(it / 1000f) },
    )
    if (state.text.text.isNotEmpty()) {
        Text(
            ctx.getString(R.string.text_clear),
            fontSize = 11.sp,
            color = scheme.primary,
            modifier = Modifier
                .clickable { onText(""); onTextCommit() }
                .padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun StyleChip(text: String, fill: Color, stroke: Color, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) scheme.primaryContainer else scheme.surface)
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) scheme.primary else scheme.outline.copy(.5f),
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = fill,
                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.4f,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
            ),
        )
    }
}
