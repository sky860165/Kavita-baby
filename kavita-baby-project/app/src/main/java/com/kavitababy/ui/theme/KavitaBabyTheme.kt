// ui/theme/KavitaBabyTheme.kt
package com.kavitababy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KavitaColorScheme = darkColorScheme(
    primary = Color(0xFFFF6B9D),
    background = Color(0xFF1A1A2E),
    surface = Color(0xFF16213E),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun KavitaBabyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KavitaColorScheme,
        content = content
    )
}
