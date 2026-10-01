package com.zhuquan.codeview.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Flat, high-contrast palette: ink on near-white, indigo accent, hairline outlines. */
@Immutable
data class Pal(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    val text: Color,
    val dim: Color,
    val faint: Color,
    val line: Color,
    val primary: Color,
    val primarySoft: Color,
    val onPrimary: Color,
    val codeBg: Color,
    val kw: Color,
    val str: Color,
    val com: Color,
    val num: Color,
    val tag: Color,
    val attr: Color,
    val punc: Color,
    val fn: Color,
    val danger: Color,
)

val LightPal = Pal(
    dark = false,
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceHi = Color(0xFFF4F6FA),
    text = Color(0xFF0F172A),
    dim = Color(0xFF64748B),
    faint = Color(0xFF94A3B8),
    line = Color(0xFFE6EAF0),
    primary = Color(0xFF4F46E5),
    primarySoft = Color(0xFFEEF2FF),
    onPrimary = Color(0xFFFFFFFF),
    codeBg = Color(0xFFFBFCFE),
    kw = Color(0xFF7C3AED),
    str = Color(0xFF0E9F6E),
    com = Color(0xFF9AA6B8),
    num = Color(0xFFD97706),
    tag = Color(0xFF2563EB),
    attr = Color(0xFF0891B2),
    punc = Color(0xFF64748B),
    fn = Color(0xFFDB2777),
    danger = Color(0xFFDC2626),
)

val DarkPal = Pal(
    dark = true,
    bg = Color(0xFF0B1120),
    surface = Color(0xFF101827),
    surfaceHi = Color(0xFF182136),
    text = Color(0xFFE6EAF2),
    dim = Color(0xFF97A4B8),
    faint = Color(0xFF6B7A90),
    line = Color(0xFF1E2A3E),
    primary = Color(0xFF8B95FF),
    primarySoft = Color(0xFF1D2352),
    onPrimary = Color(0xFF0B1120),
    codeBg = Color(0xFF0D1523),
    kw = Color(0xFFC7B9FF),
    str = Color(0xFF7FE0B0),
    com = Color(0xFF5F6E85),
    num = Color(0xFFF7C566),
    tag = Color(0xFF8FB8FF),
    attr = Color(0xFF6FD8E8),
    punc = Color(0xFF97A4B8),
    fn = Color(0xFFF5A8CD),
    danger = Color(0xFFF87171),
)

val Mono = FontFamily.Monospace

val LocalPal = staticCompositionLocalOf { LightPal }

object AppTheme {
    val colors: Pal
        @Composable get() = LocalPal.current
}

@Composable
fun CodeViewTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val pal = if (dark) DarkPal else LightPal
    val scheme = if (dark) {
        darkColorScheme(
            primary = pal.primary,
            onPrimary = pal.onPrimary,
            background = pal.bg,
            onBackground = pal.text,
            surface = pal.surface,
            onSurface = pal.text,
            surfaceVariant = pal.surfaceHi,
            onSurfaceVariant = pal.dim,
            outline = pal.line,
            error = pal.danger,
        )
    } else {
        lightColorScheme(
            primary = pal.primary,
            onPrimary = pal.onPrimary,
            background = pal.bg,
            onBackground = pal.text,
            surface = pal.surface,
            onSurface = pal.text,
            surfaceVariant = pal.surfaceHi,
            onSurfaceVariant = pal.dim,
            outline = pal.line,
            error = pal.danger,
        )
    }
    val base = MaterialTheme.typography
    val typography = base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
    )
    CompositionLocalProvider(LocalPal provides pal) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
