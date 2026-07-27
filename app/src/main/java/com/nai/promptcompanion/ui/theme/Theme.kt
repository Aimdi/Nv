package com.nai.promptcompanion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark-first palette echoing NovelAI's deep-purple aesthetic. The app is an
// offline composer used alongside the NovelAI PWA, so a fixed brand scheme is
// preferred over dynamic color.
private val NaiDarkColors = darkColorScheme(
    primary = Color(0xFFC4B5FD),
    onPrimary = Color(0xFF2D1B4E),
    primaryContainer = Color(0xFF45307A),
    onPrimaryContainer = Color(0xFFE9DEFF),
    secondary = Color(0xFF9BE7D8),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF1E5049),
    onSecondaryContainer = Color(0xFFB7FFF0),
    tertiary = Color(0xFFF5B8D0),
    onTertiary = Color(0xFF4A1D31),
    background = Color(0xFF100B1D),
    onBackground = Color(0xFFE8E2F4),
    surface = Color(0xFF171029),
    onSurface = Color(0xFFE8E2F4),
    surfaceVariant = Color(0xFF241A3D),
    onSurfaceVariant = Color(0xFFCFC4E8),
    surfaceContainer = Color(0xFF1D1531),
    surfaceContainerHigh = Color(0xFF271C40),
    outline = Color(0xFF8D7FB0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun NaiCompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NaiDarkColors,
        content = content,
    )
}
