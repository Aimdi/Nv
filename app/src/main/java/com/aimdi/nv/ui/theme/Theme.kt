package com.aimdi.nv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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

// Ink + ink-wash palette — deliberate, not purple/cream AI defaults
private val Ink = Color(0xFF1A2332)
private val InkSoft = Color(0xFF2C3A4E)
private val Paper = Color(0xFFF3F0E8)
private val PaperDim = Color(0xFFE6E1D4)
private val Accent = Color(0xFFC45C26)
private val AccentSoft = Color(0xFFE08A5A)
private val Sea = Color(0xFF2F6F7E)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC8),
    onPrimaryContainer = Color(0xFF3A1600),
    secondary = Sea,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E7EF),
    onSecondaryContainer = Color(0xFF002F38),
    tertiary = InkSoft,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperDim,
    onSurfaceVariant = InkSoft,
    outline = Color(0xFF8A8478),
)

private val DarkColors = darkColorScheme(
    primary = AccentSoft,
    onPrimary = Color(0xFF4A1C00),
    primaryContainer = Color(0xFF6B3010),
    onPrimaryContainer = Color(0xFFFFDCC8),
    secondary = Color(0xFF8ECAD6),
    onSecondary = Color(0xFF003640),
    secondaryContainer = Color(0xFF1A4F5A),
    onSecondaryContainer = Color(0xFFC8E7EF),
    tertiary = Color(0xFFB8C4D4),
    background = Color(0xFF121820),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF121820),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF2A3340),
    onSurfaceVariant = Color(0xFFC2C8D0),
    outline = Color(0xFF8C939E),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
)

@Composable
fun NaiComposerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
