package org.openscreentime.parent.data

import android.content.Context

/**
 * Local record of the parent's own self-tracked profile (see #8), if they've opted in.
 * Unlike the kid app's PairingStore, there's no pairing code involved - the parent app
 * is already signed in as its own account, so "claiming" the self profile is immediate.
 */
class SelfProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("self_profile", Context.MODE_PRIVATE)

    var childId: String?
        get() = prefs.getString("childId", null)
        set(value) = prefs.edit().putString("childId", value).apply()

    val isTracking: Boolean get() = childId != null

    fun clear() = prefs.edit().clear().apply()
}
