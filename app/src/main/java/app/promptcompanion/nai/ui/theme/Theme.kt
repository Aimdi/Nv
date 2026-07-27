package app.promptcompanion.nai.ui.theme

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

private val Ink = Color(0xFF14202B)
private val Paper = Color(0xFFF3EFE6)
private val Mist = Color(0xFFE4EDF2)
private val Tide = Color(0xFF2F6F8F)
private val Ember = Color(0xFFC45C26)
private val Deep = Color(0xFF0E1620)
private val Fog = Color(0xFFB7C7D1)

private val LightColors = lightColorScheme(
    primary = Tide,
    onPrimary = Color.White,
    secondary = Ember,
    onSecondary = Color.White,
    tertiary = Color(0xFF3F6B4F),
    background = Paper,
    onBackground = Ink,
    surface = Mist,
    onSurface = Ink,
    surfaceVariant = Color(0xFFD7E3EA),
    onSurfaceVariant = Color(0xFF3A4A56),
    outline = Color(0xFF7A8C98),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7EB6D1),
    onPrimary = Deep,
    secondary = Color(0xFFE09A6E),
    onSecondary = Deep,
    tertiary = Color(0xFF8FBF9B),
    background = Deep,
    onBackground = Fog,
    surface = Color(0xFF172230),
    onSurface = Fog,
    surfaceVariant = Color(0xFF243140),
    onSurfaceVariant = Color(0xFFB0C0CB),
    outline = Color(0xFF6F8290),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun PromptCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
