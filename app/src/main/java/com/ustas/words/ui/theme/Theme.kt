package com.ustas.words.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = lightColorScheme(
    primary = AccentOrange,
    onPrimary = Color.White,
    secondary = TileColor,
    onSecondary = Color.White,
    background = DeepGreen,
    onBackground = TileText,
    surface = WheelBackground,
    onSurface = WheelLetter
)

@Composable
fun WordsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}
