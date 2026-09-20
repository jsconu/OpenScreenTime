package org.openscreentime.parent.data

import android.content.Context

/** Local, per-phone choices about how this app's lock screen can be opened. */
class AppLockPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("app_lock", Context.MODE_PRIVATE)

    /** Off by default: opening the app with the phone's fingerprint or screen lock instead of the passcode. */
    var useDeviceAuth: Boolean
        get() = prefs.getBoolean("useDeviceAuth", false)
        set(value) = prefs.edit().putBoolean("useDeviceAuth", value).apply()
}
