package com.naicompanion.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCBB8F5),
    onPrimary = Color(0xFF33255A),
    primaryContainer = Color(0xFF4A3B72),
    onPrimaryContainer = Color(0xFFE9DEFF),
    secondary = Color(0xFFF2D16B),
    onSecondary = Color(0xFF3D2F00),
    secondaryContainer = Color(0xFF584400),
    onSecondaryContainer = Color(0xFFFFE9A8),
    background = Color(0xFF14121C),
    onBackground = Color(0xFFE7E2F0),
    surface = Color(0xFF14121C),
    onSurface = Color(0xFFE7E2F0),
    surfaceVariant = Color(0xFF494554),
    onSurfaceVariant = Color(0xFFCBC4D4),
)

/** Dark-first theme: the app is used alongside the dark NovelAI UI. */
@Composable
fun NaiCompanionTheme(content: @Composable () -> Unit) {
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        DarkColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
