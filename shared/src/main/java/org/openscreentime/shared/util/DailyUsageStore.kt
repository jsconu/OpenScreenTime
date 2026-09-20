package org.openscreentime.shared.util

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openscreentime.shared.model.UsageReader
import org.openscreentime.shared.model.capWebsites
import org.openscreentime.shared.model.countedScreenTimeMs
import org.openscreentime.shared.model.todayDateString

/**
 * Local, on-device accumulator for today's usage. Rolls over automatically at local midnight.
 * The app-name cache is not date-scoped, since package -> label rarely changes.
 *
 * The kid app and the parent's own-phone tracking each keep one under their own [prefsName] ("usage" and
 * "self_usage"), so what is saved on a phone stays exactly where it was. The friction-pause record is kept
 * by both but only used where a pause screen exists.
 */
open class DailyUsageStore(context: Context, prefsName: String) : UsageReader {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private fun rolloverIfNeeded() {
        val today = todayDateString()
        if (prefs.getString("date", null) != today) {
            prefs.edit()
                .putString("date", today)
                .putLong("totalScreenTimeMs", 0)
                .putLong("excludedMs", 0)
                .putInt("unlockCount", 0)
                .putString("appUsage", "{}")
                .putString("notificationsByApp", "{}")
                .putString("firstAppsAfterUnlock", "{}")
                .putString("websiteCounts", "{}")
                .remove("unlockAwaitingMs")
                .putBoolean("warnedDaily", false)
                .putStringSet("warnedApps", emptySet())
                .putStringSet("pausedApps", emptySet())
                .apply()
        }
    }

    fun addScreenTime(ms: Long) {
        if (ms <= 0) return
        rolloverIfNeeded()
        prefs.edit().putLong("totalScreenTimeMs", prefs.getLong("totalScreenTimeMs", 0) + ms).apply()
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

    /** Time spent in an app a parent excluded from the overall limit; taken off today's total. */
    fun addExcludedTime(ms: Long) {
        if (ms <= 0) return
        rolloverIfNeeded()
        prefs.edit().putLong("excludedMs", prefs.getLong("excludedMs", 0) + ms).apply()
    }

    /**
     * Today's screen time that counts toward the overall daily limit, including any session currently in
     * progress, less the time in apps excluded from it (see [addExcludedTime]). Used for daily-limit checks so a
     * single long unlocked session is still caught, and uploaded as the day's total so a parent sees the same
     * figure the limit uses.
     */
    override val liveTotalScreenTimeMs: Long
        get() {
            rolloverIfNeeded()
            val ended = prefs.getLong("totalScreenTimeMs", 0)
            val start = sessionStartMs
            val inProgress = if (start == null) 0L else System.currentTimeMillis() - start
            return countedScreenTimeMs(ended + inProgress, prefs.getLong("excludedMs", 0))
        }

    fun markDailyWarned() {
        rolloverIfNeeded()
        prefs.edit().putBoolean("warnedDaily", true).apply()
    }

    override val dailyWarned: Boolean
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

    override val warnedApps: Set<String>
        get() {
            rolloverIfNeeded()
            return prefs.getStringSet("warnedApps", emptySet()) ?: emptySet()
        }

    /** Apps that have already shown the friction pause (see #12) today, so it shows once per app per day. */
    fun markAppPaused(packageName: String) {
        rolloverIfNeeded()
        val set = pausedApps.toMutableSet()
        set.add(packageName)
        prefs.edit().putStringSet("pausedApps", set).apply()
    }

    override val pausedApps: Set<String>
        get() {
            rolloverIfNeeded()
            return prefs.getStringSet("pausedApps", emptySet()) ?: emptySet()
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

    // --- See #41: optional website tracking. Sites looked up while a browser was open; only written to
    // while the matching toggle is on. Site names only - never a page, search or time. ---

    /** Adds [counts] (site -> lookups) to today's tally, keeping only the busiest sites. */
    fun addWebsiteCounts(counts: Map<String, Int>) {
        if (counts.isEmpty()) return
        rolloverIfNeeded()
        val merged = websiteCounts.toMutableMap()
        counts.forEach { (site, n) -> merged[site] = (merged[site] ?: 0) + n }
        prefs.edit().putString("websiteCounts", json.encodeToString(capWebsites(merged))).apply()
    }

    val websiteCounts: Map<String, Int>
        get() {
            rolloverIfNeeded()
            return decode(prefs.getString("websiteCounts", "{}")!!)
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

    /** Today's counted screen time, for reports and sync; the same figure the daily limit uses. */
    val totalScreenTimeMs: Long
        get() = liveTotalScreenTimeMs

    val unlockCount: Int
        get() {
            rolloverIfNeeded()
            return prefs.getInt("unlockCount", 0)
        }

    override val appUsageMs: Map<String, Long>
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
