package org.openscreentime.parent.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openscreentime.shared.model.todayDateString

/**
 * Local, on-device accumulator for the parent's own today's usage (self-tracking, see #8).
 * Rolls over automatically at local midnight. Mirrors the kid app's UsageStore exactly -
 * duplicated rather than shared, consistent with how this codebase keeps the two apps
 * independent rather than introducing cross-app coupling for UI/service-layer code.
 */
class UsageStore(context: Context) {
    private val prefs = context.getSharedPreferences("self_usage", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private fun rolloverIfNeeded() {
        val today = todayDateString()
        if (prefs.getString("date", null) != today) {
            prefs.edit()
                .putString("date", today)
                .putLong("totalScreenTimeMs", 0)
                .putInt("unlockCount", 0)
                .putString("appUsage", "{}")
                .putString("notificationsByApp", "{}")
                .putString("firstAppsAfterUnlock", "{}")
                .remove("unlockAwaitingMs")
                .putBoolean("warnedDaily", false)
                .putStringSet("warnedApps", emptySet())
                .apply()
        }
    }

    fun addScreenTime(ms: Long) {
        if (ms <= 0) return
        rolloverIfNeeded()
        prefs.edit().putLong("totalScreenTimeMs", totalScreenTimeMs + ms).apply()
    }

    /** Call when the device is unlocked, to start a live screen-time session. */
    fun startSession() {
        rolloverIfNeeded()
        prefs.edit().putLong("sessionStartMs", System.currentTimeMillis()).apply()
    }

    /** Call when the screen turns off, to fold the just-finished session into today's total. */
    fun endSessionAndFlush() {
        val start = sessionStartMs ?: return
        addScreenTime(System.currentTimeMillis() - start)
        prefs.edit().remove("sessionStartMs").apply()
    }

    private val sessionStartMs: Long?
        get() {
            val v = prefs.getLong("sessionStartMs", -1)
            return if (v < 0) null else v
        }

    /**
     * Today's total screen time including any session currently in progress -
     * unlike [totalScreenTimeMs], which only reflects sessions already ended.
     * Used for daily-limit checks so a single long unlocked session is still caught.
     */
    val liveTotalScreenTimeMs: Long
        get() {
            val base = totalScreenTimeMs
            val start = sessionStartMs ?: return base
            return base + (System.currentTimeMillis() - start)
        }

    fun markDailyWarned() {
        rolloverIfNeeded()
        prefs.edit().putBoolean("warnedDaily", true).apply()
    }

    val dailyWarned: Boolean
        get() {
            rolloverIfNeeded()
            return prefs.getBoolean("warnedDaily", false)
        }

    fun markAppWarned(packageName: String) {
        rolloverIfNeeded()
        val set = warnedApps.toMutableSet()
        set.add(packageName)
        prefs.edit().putStringSet("warnedApps", set).apply()
    }

    val warnedApps: Set<String>
        get() {
            rolloverIfNeeded()
            return prefs.getStringSet("warnedApps", emptySet()) ?: emptySet()
        }

    fun addAppTime(packageName: String, ms: Long) {
        if (ms <= 0) return
        rolloverIfNeeded()
        val map = appUsageMs.toMutableMap()
        map[packageName] = (map[packageName] ?: 0) + ms
        prefs.edit().putString("appUsage", json.encodeToString(map)).apply()
    }

    fun incrementUnlockCount() {
        rolloverIfNeeded()
        prefs.edit().putInt("unlockCount", unlockCount + 1).apply()
    }

    // --- See #35: optional tracking. Only ever written to while a parent has the matching toggle on. ---

    /** Counts one notification from [packageName] today - a count only, never the notification's content. */
    fun recordNotification(packageName: String) {
        rolloverIfNeeded()
        val map = notificationCountsByApp.toMutableMap()
        map[packageName] = (map[packageName] ?: 0) + 1
        prefs.edit().putString("notificationsByApp", json.encodeToString(map)).apply()
    }

    val notificationCountsByApp: Map<String, Int>
        get() {
            rolloverIfNeeded()
            return decode(prefs.getString("notificationsByApp", "{}")!!)
        }

    val notificationCount: Int
        get() = notificationCountsByApp.values.sum()

    /** Marks an unlock at [nowMs] as still waiting to see which app is opened first. */
    fun markUnlockAwaitingFirstApp(nowMs: Long) {
        rolloverIfNeeded()
        prefs.edit().putLong("unlockAwaitingMs", nowMs).apply()
    }

    val unlockAwaitingMs: Long?
        get() {
            val v = prefs.getLong("unlockAwaitingMs", -1)
            return if (v < 0) null else v
        }

    fun clearUnlockAwaiting() {
        prefs.edit().remove("unlockAwaitingMs").apply()
    }

    /** Records [packageName] as the app opened first after the pending unlock, and stops waiting. */
    fun recordFirstAppAfterUnlock(packageName: String) {
        rolloverIfNeeded()
        val map = firstAppsAfterUnlock.toMutableMap()
        map[packageName] = (map[packageName] ?: 0) + 1
        prefs.edit()
            .putString("firstAppsAfterUnlock", json.encodeToString(map))
            .remove("unlockAwaitingMs")
            .apply()
    }

    val firstAppsAfterUnlock: Map<String, Int>
        get() {
            rolloverIfNeeded()
            return decode(prefs.getString("firstAppsAfterUnlock", "{}")!!)
        }

    fun cacheAppName(packageName: String, name: String) {
        if (appNames[packageName] == name) return
        val map = appNames.toMutableMap()
        map[packageName] = name
        prefs.edit().putString("appNames", json.encodeToString(map)).apply()
    }

    val date: String
        get() {
            rolloverIfNeeded()
            return prefs.getString("date", todayDateString())!!
        }

    val totalScreenTimeMs: Long
        get() {
            rolloverIfNeeded()
            return prefs.getLong("totalScreenTimeMs", 0)
        }

    val unlockCount: Int
        get() {
            rolloverIfNeeded()
            return prefs.getInt("unlockCount", 0)
        }

    val appUsageMs: Map<String, Long>
        get() {
            rolloverIfNeeded()
            return decode(prefs.getString("appUsage", "{}")!!)
        }

    val appNames: Map<String, String>
        get() = decode(prefs.getString("appNames", "{}")!!)

    private inline fun <reified T> decode(raw: String): Map<String, T> =
        try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            emptyMap()
        }
}
