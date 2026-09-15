package org.openscreentime.kid.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.openscreentime.kid.data.TextSize
import org.openscreentime.kid.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    secondary = Color(0xFF558B2F)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF81C784),
    secondary = Color(0xFFAED581)
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
