package com.emrobinso.floatpic.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AspectPreset(val ratio: Float?, val outputWidth: Int, val outputHeight: Int) {
    ORIGINAL(null, 0, 0),
    SQUARE(1f, 1080, 1080),
    PORTRAIT_4_5(4f / 5f, 1080, 1350),
    STORY_9_16(9f / 16f, 1080, 1920),
    LANDSCAPE_16_9(16f / 9f, 1920, 1080),
    CLASSIC_3_2(3f / 2f, 1920, 1280),
    CLASSIC_4_3(4f / 3f, 1440, 1080),
    PORTRAIT_2_3(2f / 3f, 1280, 1920),
    PORTRAIT_3_4(3f / 4f, 1080, 1440),
    FIVE_4(5f / 4f, 1350, 1080),
    PHONE_9_20(9f / 20f, 1080, 2400),
}

@Serializable
enum class ScaleMode { FILL, FIT }

@Serializable
enum class AlignAnchor(val fx: Float, val fy: Float) {
    TOP_LEFT(0f, 0f), TOP_CENTER(0.5f, 0f), TOP_RIGHT(1f, 0f),
    CENTER_LEFT(0f, 0.5f), CENTER(0.5f, 0.5f), CENTER_RIGHT(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f), BOTTOM_CENTER(0.5f, 1f), BOTTOM_RIGHT(1f, 1f),
}

@Serializable
enum class BackgroundKind { COLOR, GRADIENT, BLUR, PATTERN }

@Serializable
data class BackgroundSpec(
    val kind: BackgroundKind = BackgroundKind.COLOR,
    val color: Long = 0xFF111111,
    val color2: Long = 0xFF2A0B12,
    val angle: Float = 135f,
    val blurStrength: Float = 24f,
    val patternId: String = "dots",
) {
    companion object {
        val Dark = BackgroundSpec(kind = BackgroundKind.COLOR, color = 0xFF111111)
    }
}

@Serializable
enum class TextPosition { TOP_CENTER, BOTTOM_CENTER }

/** Horizontal alignment of the caption inside its line box. */
@Serializable
enum class TextAlign { LEFT, CENTER, RIGHT }

@Serializable
enum class TextStylePreset { WHITE_ON_BLACK, BLACK_ON_WHITE }

@Serializable
data class TextOverlay(
    val text: String = "",
    val position: TextPosition = TextPosition.BOTTOM_CENTER,
    val style: TextStylePreset = TextStylePreset.WHITE_ON_BLACK,
    val sizeFraction: Float = 0.075f,
    /** left / center / right inside the frame; defaults to centered */
    val align: TextAlign = TextAlign.CENTER,
) {
    val isVisible: Boolean get() = text.isNotBlank()
}

/**
 * Single source of truth for everything the renderer draws.
 * Geometry in "source pixels" of the (downscaled but oriented) source bitmap.
 */
@Serializable
data class Transform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotation: Float = 0f,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
)

@Serializable
data class EditorState(
    val aspect: AspectPreset = AspectPreset.ORIGINAL,
    val scaleMode: ScaleMode = ScaleMode.FILL,
    val transform: Transform = Transform(),
    val background: BackgroundSpec = BackgroundSpec.Dark,
    val paddingDp: Float = 0f,
    val cornerDp: Float = 0f,
    val shadow: Boolean = false,
    val text: TextOverlay = TextOverlay(),
) {
    val hasVisualEdits: Boolean
        get() = paddingDp > 0f || cornerDp > 0f || shadow || text.isVisible
}
