package dev.naicompanion.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

private val Purple = Color(0xFF7C5CDB)
private val PurpleLight = Color(0xFFC9A7FF)
private val Pink = Color(0xFFFF8AD1)
private val Ink = Color(0xFF1B1425)

private val DarkColors = darkColorScheme(
    primary = PurpleLight,
    onPrimary = Color(0xFF2A1A4A),
    primaryContainer = Color(0xFF4A3580),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Pink,
    onSecondary = Color(0xFF4A1030),
    secondaryContainer = Color(0xFF6B2A50),
    onSecondaryContainer = Color(0xFFFFD9EC),
    background = Ink,
    surface = Ink,
)

private val LightColors = lightColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF25005A),
    secondary = Color(0xFFB0296F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9EC),
    onSecondaryContainer = Color(0xFF3E0022),
)

/** Monospace style for the rendered-prompt preview, where exact characters matter. */
val PromptPreviewTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)

@Composable
fun NaiCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
