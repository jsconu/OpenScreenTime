package org.openscreentime.kid.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openscreentime.shared.model.todayDateString

/**
 * Local, on-device accumulator for today's usage. Rolls over automatically at local midnight.
 * The app-name cache is not date-scoped, since package -> label rarely changes.
 */
class UsageStore(context: Context) {
    private val prefs = context.getSharedPreferences("usage", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private fun rolloverIfNeeded() {
        val today = todayDateString()
        if (prefs.getString("date", null) != today) {
            prefs.edit()
                .putString("date", today)
                .putLong("totalScreenTimeMs", 0)
                .putInt("unlockCount", 0)
                .putString("appUsage", "{}")
                .apply()
        }
    }

    fun addScreenTime(ms: Long) {
        if (ms <= 0) return
        rolloverIfNeeded()
        prefs.edit().putLong("totalScreenTimeMs", totalScreenTimeMs + ms).apply()
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
