package com.zhousl.aether.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhousl.aether.data.AppThemeMode

// ============================================================
// 🧱 Pixel Aether — 像素主题
//   所有圆角 → 0dp
//   所有字体 → Monospace（等宽 / 像素风格）
// ============================================================

// ── 像素字体 ──
// 如需真正像素字体（Press Start 2P / VT323），
// 下载至 res/font/ 后改为 FontFamily(Font(R.font.press_start_2p))
private val PixelFontFamily = FontFamily.Monospace

// ── 像素版配色方案（保持字段与光照/深色对应） ──
private val LightAetherColors = lightColorScheme(
    primary = LightAetherPalette.primary,
    onPrimary = LightAetherPalette.onPrimary,
    primaryContainer = LightAetherPalette.primaryContainer,
    onPrimaryContainer = LightAetherPalette.onPrimaryContainer,
    secondary = LightAetherPalette.secondary,
    onSecondary = LightAetherPalette.onSecondary,
    secondaryContainer = LightAetherPalette.secondaryContainer,
    onSecondaryContainer = LightAetherPalette.onSecondaryContainer,
    background = LightAetherPalette.background,
    surface = LightAetherPalette.surface,
    surfaceVariant = LightAetherPalette.surfaceVariant,
    onSurface = LightAetherPalette.onSurface,
    onSurfaceVariant = LightAetherPalette.onSurfaceVariant,
    tertiary = LightAetherPalette.tertiary,
    error = LightAetherPalette.error,
    outline = LightAetherPalette.outline,
)

private val DarkAetherColors = darkColorScheme(
    primary = DarkAetherPalette.primary,
    onPrimary = DarkAetherPalette.onPrimary,
    primaryContainer = DarkAetherPalette.primaryContainer,
    onPrimaryContainer = DarkAetherPalette.onPrimaryContainer,
    secondary = DarkAetherPalette.secondary,
    onSecondary = DarkAetherPalette.onSecondary,
    secondaryContainer = DarkAetherPalette.secondaryContainer,
    onSecondaryContainer = DarkAetherPalette.onSecondaryContainer,
    background = DarkAetherPalette.background,
    surface = DarkAetherPalette.surface,
    surfaceVariant = DarkAetherPalette.surfaceVariant,
    onSurface = DarkAetherPalette.onSurface,
    onSurfaceVariant = DarkAetherPalette.onSurfaceVariant,
    tertiary = DarkAetherPalette.tertiary,
    error = DarkAetherPalette.error,
    outline = DarkAetherPalette.outline,
)

// ── 像素版文字排版 ──
// 等宽字体 + 像素整数字号 + 无 letter spacing（字距归零）
private val AetherTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 8.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.sp
    ),
)

// ── 像素形状：全部直角（0dp 圆角） ──
private val PixelShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

@Composable
fun AetherTheme(
    themeMode: AppThemeMode = AppThemeMode.System,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.System -> isSystemInDarkTheme()
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
    SideEffect {
        updateAetherPalette(darkTheme)
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkAetherColors else LightAetherColors,
        typography = AetherTypography,
        shapes = PixelShapes,
        content = content
    )
}