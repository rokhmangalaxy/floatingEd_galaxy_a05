package com.emrobinso.floatpic.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable

@Serializable
enum class AppTheme {
    /** Pastel Maroon, light. */
    PASTEL_MAROON,

    /** Pastel Maroon, dark mode. */
    MAROON_DARK,

    /** Legacy soft-UI palette (kept for old drafts/history entries). */
    NEUMORPHISM;

    /** The toggle in the editor switches between exactly these two. */
    fun toggled(): AppTheme = if (this == MAROON_DARK) PASTEL_MAROON else MAROON_DARK
}

/** Extra tokens both themes expose to components (neumorphic shadows, etc.). */
data class ExtraTokens(
    val softShadow: Color,
    val softLight: Color,
    val cardRadius: Int, // dp
    val raised: Boolean, // whether cards should look embossed
)

val LocalExtra = staticCompositionLocalOf {
    ExtraTokens(Color.Transparent, Color.Transparent, 28, false)
}

private val maroon = Color(0xFF7A0C2E)
private val maroonBright = Color(0xFFC1121F)
private val plum = Color(0xFF3E101B)
private val cream = Color(0xFFF6E7E4)
private val sugar = Color(0xFFFFF9F7)

private val neuLight = Color(0xFFE9ECF2)
private val neuDark = Color(0xFF22262E)
private val neuAccent = Color(0xFF5B6CFF)

private val MaroonLight = lightColorScheme(
    primary = maroon,
    onPrimary = sugar,
    primaryContainer = Color(0xFFF3D7DB),
    onPrimaryContainer = plum,
    secondary = plum,
    background = cream,
    onBackground = plum,
    surface = sugar,
    onSurface = plum,
    surfaceVariant = Color(0xFFF0DCDC),
    onSurfaceVariant = Color(0xFF6B3A45),
    outline = Color(0xFFCBA9AE),
)

private val MaroonDark = darkColorScheme(
    primary = maroonBright,
    onPrimary = sugar,
    primaryContainer = Color(0xFF5C0A22),
    onPrimaryContainer = sugar,
    background = plum,
    onBackground = cream,
    surface = Color(0xFF2A0B12),
    onSurface = cream,
    surfaceVariant = Color(0xFF43111C),
    onSurfaceVariant = Color(0xFFE8C9CC),
    outline = Color(0xFF7A4049),
)

private val NeuLight = lightColorScheme(
    primary = neuAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2F5),
    onPrimaryContainer = Color(0xFF232838),
    background = neuLight,
    onBackground = Color(0xFF2A2F3A),
    surface = neuLight,
    onSurface = Color(0xFF2A2F3A),
    surfaceVariant = Color(0xFFEDF0F6),
    onSurfaceVariant = Color(0xFF525A69),
    outline = Color(0xFFC3C8D4),
)

private val NeuDark = darkColorScheme(
    primary = Color(0xFF8FA0FF),
    onPrimary = Color(0xFF161A22),
    primaryContainer = Color(0xFF3A4470),
    onPrimaryContainer = Color(0xFFE6E9F0),
    background = neuDark,
    onBackground = Color(0xFFE6E9F0),
    surface = neuDark,
    onSurface = Color(0xFFE6E9F0),
    surfaceVariant = Color(0xFF2B303A),
    onSurfaceVariant = Color(0xFFB8BFCC),
    outline = Color(0xFF3C434F),
)

private val FloatTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
        ),
    )
}

@Composable
fun FloatPicTheme(theme: AppTheme, dark: Boolean, content: @Composable () -> Unit) {
    val useDark = dark || theme == AppTheme.MAROON_DARK
    val scheme = when (theme) {
        AppTheme.PASTEL_MAROON, AppTheme.MAROON_DARK -> if (useDark) MaroonDark else MaroonLight
        AppTheme.NEUMORPHISM -> if (useDark) NeuDark else NeuLight
    }
    val extra = when (theme) {
        AppTheme.PASTEL_MAROON, AppTheme.MAROON_DARK -> ExtraTokens(
            softShadow = Color(0x1F7A0C2E),
            softLight = Color(0x66FFFFFF),
            cardRadius = 28,
            raised = false,
        )
        AppTheme.NEUMORPHISM -> ExtraTokens(
            softShadow = if (dark) Color(0xFF171A20) else Color(0xFFC3C8D4),
            softLight = if (dark) Color(0xFF2E333D) else Color(0xFFFFFFFF),
            cardRadius = 28,
            raised = true,
        )
    }
    CompositionLocalProvider(LocalExtra provides extra) {
        MaterialTheme(
            colorScheme = scheme,
            typography = FloatTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
