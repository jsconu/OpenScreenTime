package org.openscreentime.kid.data

import android.content.Context

/** Local record of which parent/child this device is paired to. */
class PairingStore(context: Context) {
    private val prefs = context.getSharedPreferences("pairing", Context.MODE_PRIVATE)

    var parentUid: String?
        get() = prefs.getString("parentUid", null)
        set(value) = prefs.edit().putString("parentUid", value).apply()

    var childId: String?
        get() = prefs.getString("childId", null)
        set(value) = prefs.edit().putString("childId", value).apply()

    var childName: String?
        get() = prefs.getString("childName", null)
        set(value) = prefs.edit().putString("childName", value).apply()

    val isPaired: Boolean get() = parentUid != null && childId != null

    fun clear() = prefs.edit().clear().apply()
}
