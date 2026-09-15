package org.openscreentime.kid.data

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class TextSize(val scale: Float) {
    DEFAULT(1f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f)
}

/** Local, on-device display preferences - independent of any account or Firestore data. */
class AppearancePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = ThemeMode.entries.find { it.name == prefs.getString("themeMode", null) } ?: ThemeMode.SYSTEM
        set(value) = prefs.edit().putString("themeMode", value.name).apply()

    var textSize: TextSize
        get() = TextSize.entries.find { it.name == prefs.getString("textSize", null) } ?: TextSize.DEFAULT
        set(value) = prefs.edit().putString("textSize", value.name).apply()

    fun cycleThemeMode(): ThemeMode {
        val next = ThemeMode.entries[(themeMode.ordinal + 1) % ThemeMode.entries.size]
        themeMode = next
        return next
    }

    fun cycleTextSize(): TextSize {
        val next = TextSize.entries[(textSize.ordinal + 1) % TextSize.entries.size]
        textSize = next
        return next
    }
}

fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "Match device"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

fun TextSize.label(): String = when (this) {
    TextSize.DEFAULT -> "Default"
    TextSize.LARGE -> "Large"
    TextSize.EXTRA_LARGE -> "Extra large"
}
