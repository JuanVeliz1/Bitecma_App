package com.bitecma.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val BitecmaLightColors = lightColorScheme(
    primary = Color(0xFF1A3A5C),
    onPrimary = Color.White,
    secondary = Color(0xFF0A8F7E),
    onSecondary = Color.White,
    tertiary = Color(0xFF1D6FA4),
    background = Color(0xFFF4F6F9),
    onBackground = Color(0xFF1E293B),
    surface = Color.White,
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFEEF1F6),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFE0E5EF),
    error = Color(0xFFDC2626),
    onError = Color.White,
)

private val BitecmaDarkColors = darkColorScheme(
    primary = Color(0xFFE8EEF8),
    onPrimary = Color(0xFF10233C),
    secondary = Color(0xFF0A8F7E),
    onSecondary = Color.White,
    tertiary = Color(0xFF5EC4B8),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF0F1B2D),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF243246),
    error = Color(0xFFDC2626),
    onError = Color.White,
)

private val BitecmaTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

val ColorScheme.bitecmaTeal: Color
    get() = Color(0xFF0A8F7E)

val ColorScheme.bitecmaTealContainer: Color
    get() = if (isLight()) Color(0xFFD0F0EC) else Color(0xFF0F3C39)

val ColorScheme.bitecmaNavy: Color
    get() = if (isLight()) Color(0xFF1A3A5C) else Color(0xFFE8EEF8)

val ColorScheme.bitecmaNavyStrong: Color
    get() = if (isLight()) Color(0xFF0F2540) else Color(0xFF10233C)

val ColorScheme.bitecmaMutedText: Color
    get() = if (isLight()) Color(0xFF94A3B8) else Color(0xFF94A3B8)

val ColorScheme.bitecmaSubtleText: Color
    get() = if (isLight()) Color(0xFF475569) else Color(0xFFCBD5E1)

val ColorScheme.bitecmaCardBackground: Color
    get() = if (isLight()) Color.White else Color(0xFF0F1B2D)

val ColorScheme.bitecmaSoftBackground: Color
    get() = if (isLight()) Color(0xFFF4F6F9) else Color(0xFF0F172A)

val ColorScheme.bitecmaSoftBackgroundAlt: Color
    get() = if (isLight()) Color(0xFFEEF1F6) else Color(0xFF162235)

val ColorScheme.bitecmaBorder: Color
    get() = if (isLight()) Color(0xFFE0E5EF) else Color(0xFF243246)

val ColorScheme.bitecmaSuccessBg: Color
    get() = if (isLight()) Color(0xFFD1FAE5) else Color(0xFF103B31)

val ColorScheme.bitecmaAmberBg: Color
    get() = if (isLight()) Color(0xFFFEF3C7) else Color(0xFF4A3510)

val ColorScheme.bitecmaBlueBg: Color
    get() = if (isLight()) Color(0xFFDBEAFE) else Color(0xFF17324A)

val ColorScheme.bitecmaPurpleBg: Color
    get() = if (isLight()) Color(0xFFEDE9FE) else Color(0xFF35224E)

val ColorScheme.bitecmaDangerBg: Color
    get() = if (isLight()) Color(0xFFFEE2E2) else Color(0xFF4A1F24)

private fun ColorScheme.isLight(): Boolean = background.red > 0.5f

@Composable
fun BitecmaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) BitecmaDarkColors else BitecmaLightColors
    MaterialTheme(
        colorScheme = colors,
        typography = BitecmaTypography,
        content = content,
    )
}
