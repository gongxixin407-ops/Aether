package com.zhousl.aether.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.zhousl.aether.data.AppThemeMode

// ============================================================
// 🎮 Pixel Aether — 8-bit Pixel Art Color Palette
// ============================================================

/** 纯像素色板 */
object PixelColors {
    val Black           = Color(0xFF000000)
    val CrtBlack        = Color(0xFF0A0E14)
    val DarkBlue        = Color(0xFF1A1C2C)
    val Parchment       = Color(0xFFF5F0E1)
    val NesPurple       = Color(0xFF7C3AED)
    val NesDarkPurple   = Color(0xFF5B21B6)
    val GbGreen         = Color(0xFF306230)
    val GbLightGreen    = Color(0xFF8BAC0F)
    val PhosphorGreen   = Color(0xFF00FF41)
    val PixelWhite      = Color(0xFFE8E4DA)
    val PixelGray       = Color(0xFF9E9E9E)
    val PixelDarkGray   = Color(0xFF424242)
    val NesRed          = Color(0xFFE60000)
    val NesBlue         = Color(0xFF2424FF)
    val NesYellow       = Color(0xFFF0E800)
    val Cyan            = Color(0xFF00E5FF)
    val Coral           = Color(0xFFFF6B6B)
}

data class AetherPalette(
    val background: Color,
    val backgroundGradientTop: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceHigher: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val outlineSoft: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val error: Color,
    val messageBubble: Color,
    val scrim: Color,
)

// ── 浅色模式：羊皮纸 + NES 紫 + Game Boy 绿 ──
internal val LightAetherPalette = AetherPalette(
    background           = PixelColors.Parchment,
    backgroundGradientTop = PixelColors.Parchment,           // 像素风格 — 无渐变
    surface              = PixelColors.PixelWhite,
    surfaceHigh          = PixelColors.PixelWhite,
    surfaceHigher        = Color(0xFFDDD5C0),
    surfaceVariant       = Color(0xFFE8E0CC),
    outline              = PixelColors.Black,                // 纯黑粗线框
    outlineSoft          = PixelColors.PixelDarkGray,
    onSurface            = PixelColors.Black,
    onSurfaceVariant     = PixelColors.PixelDarkGray,
    primary              = PixelColors.NesPurple,
    onPrimary            = PixelColors.PixelWhite,
    primaryContainer     = PixelColors.NesDarkPurple,
    onPrimaryContainer   = PixelColors.PixelWhite,
    secondary            = PixelColors.GbGreen,
    onSecondary          = PixelColors.PixelWhite,
    secondaryContainer   = PixelColors.GbLightGreen,
    onSecondaryContainer = PixelColors.Black,
    tertiary             = PixelColors.NesBlue,
    error                = PixelColors.NesRed,
    messageBubble        = Color(0xFFE8DCF0),
    scrim                = Color(0x44000000),
)

// ── 深色模式：CRT 黑底 + 磷光绿 + 荧光色 ──
internal val DarkAetherPalette = AetherPalette(
    background           = PixelColors.CrtBlack,
    backgroundGradientTop = PixelColors.CrtBlack,            // 像素风格 — 无渐变
    surface              = PixelColors.DarkBlue,
    surfaceHigh          = Color(0xFF222538),
    surfaceHigher        = PixelColors.PixelDarkGray,
    surfaceVariant       = PixelColors.PixelDarkGray,
    outline              = PixelColors.PhosphorGreen,        // 荧光绿边框
    outlineSoft          = PixelColors.PixelGray,
    onSurface            = PixelColors.PhosphorGreen,
    onSurfaceVariant     = PixelColors.PixelGray,
    primary              = PixelColors.NesPurple,
    onPrimary            = PixelColors.PixelWhite,
    primaryContainer     = PixelColors.NesDarkPurple,
    onPrimaryContainer   = PixelColors.PixelWhite,
    secondary            = PixelColors.GbLightGreen,
    onSecondary          = PixelColors.Black,
    secondaryContainer   = PixelColors.GbGreen,
    onSecondaryContainer = PixelColors.PixelWhite,
    tertiary             = PixelColors.Cyan,
    error                = PixelColors.NesRed,
    messageBubble        = Color(0xFF2E2240),
    scrim                = Color(0x88000000),
)

private var currentPalette by mutableStateOf(LightAetherPalette)

internal fun updateAetherPalette(darkTheme: Boolean) {
    val palette = if (darkTheme) {
        DarkAetherPalette
    } else {
        LightAetherPalette
    }
    if (currentPalette != palette) {
        currentPalette = palette
    }
}

val AetherBackground: Color
    get() = currentPalette.background

val AetherBackgroundGradientTop: Color
    get() = currentPalette.backgroundGradientTop

val AetherSurface: Color
    get() = currentPalette.surface

val AetherSurfaceHigh: Color
    get() = currentPalette.surfaceHigh

val AetherSurfaceHigher: Color
    get() = currentPalette.surfaceHigher

val AetherSurfaceVariant: Color
    get() = currentPalette.surfaceVariant

val AetherOutline: Color
    get() = currentPalette.outline

val AetherOutlineSoft: Color
    get() = currentPalette.outlineSoft

val AetherOnSurface: Color
    get() = currentPalette.onSurface

val AetherOnSurfaceVariant: Color
    get() = currentPalette.onSurfaceVariant

val AetherPrimary: Color
    get() = currentPalette.primary

val AetherOnPrimary: Color
    get() = currentPalette.onPrimary

val AetherPrimaryContainer: Color
    get() = currentPalette.primaryContainer

val AetherOnPrimaryContainer: Color
    get() = currentPalette.onPrimaryContainer

val AetherSecondary: Color
    get() = currentPalette.secondary

val AetherOnSecondary: Color
    get() = currentPalette.onSecondary

val AetherSecondaryContainer: Color
    get() = currentPalette.secondaryContainer

val AetherOnSecondaryContainer: Color
    get() = currentPalette.onSecondaryContainer

val AetherTertiary: Color
    get() = currentPalette.tertiary

val AetherError: Color
    get() = currentPalette.error

val AetherMessageBubble: Color
    get() = currentPalette.messageBubble

val AetherScrim: Color
    get() = currentPalette.scrim