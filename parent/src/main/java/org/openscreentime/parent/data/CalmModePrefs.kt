package org.openscreentime.parent.data

import android.content.Context

/** Local choices for the parent's calm notifications. */
class CalmModePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("calm_mode", Context.MODE_PRIVATE)

    /**
     * Off by default. When on, other apps' notifications are taken out of the shade and collected in the calm
     * list instead; calls, texts, alarms, sign-in codes and system messages still come through.
     */
    var hideOthers: Boolean
        get() = prefs.getBoolean("hideOthers", false)
        set(value) = prefs.edit().putBoolean("hideOthers", value).apply()
}
