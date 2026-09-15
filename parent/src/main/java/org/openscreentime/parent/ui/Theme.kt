package org.openscreentime.parent.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.openscreentime.parent.data.TextSize
import org.openscreentime.parent.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    secondary = Color(0xFF00838F)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFF80DEEA)
)

@Composable
fun OpenScreenTimeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    textSize: TextSize = TextSize.DEFAULT,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * textSize.scale)
    ) {
        MaterialTheme(
            colorScheme = if (useDark) DarkColors else LightColors,
            content = content
        )
    }
}
