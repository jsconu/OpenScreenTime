package org.openscreentime.parent.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openscreentime.shared.model.DigestNotification
import org.openscreentime.shared.model.todayDateString
import org.openscreentime.shared.model.upsertDigestEntry

/**
 * Local, on-device store for the parent's own optional calm notification list - the same feature
 * the kid app has (see #20). Resets at local midnight. Nothing here is ever synced anywhere.
 */
class NotificationDigestStore(context: Context) {
    private val prefs = context.getSharedPreferences("notification_digest", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /** Off by default - a personal nicety, not part of core monitoring. */
    var optedIn: Boolean
        get() = prefs.getBoolean("optedIn", false)
        set(value) = prefs.edit().putBoolean("optedIn", value).apply()

    private fun rolloverIfNeeded() {
        val today = todayDateString()
        if (prefs.getString("date", null) != today) {
            prefs.edit().putString("date", today).putString("entries", "[]").apply()
        }
    }

    fun record(entry: DigestNotification) {
        if (!optedIn) return
        rolloverIfNeeded()
        val next = upsertDigestEntry(entries, entry)
        prefs.edit().putString("entries", json.encodeToString(next)).apply()
    }

    val entries: List<DigestNotification>
        get() {
            rolloverIfNeeded()
            return try {
                json.decodeFromString(prefs.getString("entries", "[]")!!)
            } catch (_: Exception) {
                emptyList()
            }
        }
}
