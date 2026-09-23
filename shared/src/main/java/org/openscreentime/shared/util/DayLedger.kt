package org.openscreentime.shared.util

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openscreentime.shared.model.UsageReader
import org.openscreentime.shared.model.capWebsites
import org.openscreentime.shared.model.countedScreenTimeMs
import org.openscreentime.shared.model.todayDateString

/**
 * One phone's usage for today, and the rules for keeping it: it rolls over at local midnight, adds up screen-on
 * sessions, per-app time and counts, and takes time in excluded apps off the total that the daily limit uses. The
 * app-name cache is not date-scoped, since package -> label rarely changes.
 *
 * All the rules live here, over a [KeyValueStore] (the phone's SharedPreferences in [DailyUsageStore], memory in
 * tests) and a clock, so midnight, an open session and exclusion can be tested without a device. The key names are
 * the ones already saved on people's phones; keep them.
 */
open class DayLedger(
    private val store: KeyValueStore,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val todayString: () -> String = ::todayDateString,
    /**
     * Whether the screen is on right now. A session can only be "in progress" while it is: if the
     * screen-off broadcast was missed - the service that hears it was killed, which some phones do
     * routinely - the session would otherwise stay open and count every hour the phone sat locked.
     */
    private val isScreenOn: () -> Boolean = { true }
) : UsageReader {
    private val json = Json { ignoreUnknownKeys = true }

    private fun rolloverIfNeeded() {
        val today = todayString()
        if (store.getString("date", null) != today) {
            store.edit {
                putString("date", today)
                putLong("totalScreenTimeMs", 0)
                putLong("excludedMs", 0)
                putInt("unlockCount", 0)
                putString("appUsage", "{}")
                putString("notificationsByApp", "{}")
                putString("firstAppsAfterUnlock", "{}")
                putString("websiteCounts", "{}")
                remove("unlockAwaitingMs")
                putBoolean("warnedDaily", false)
                putStringSet("warnedApps", emptySet())
                putStringSet("pausedApps", emptySet())
                // A session still open at midnight belongs to both days. Leaving its start where it
                // was put the whole of it on the new day - so a phone left unlocked overnight, or one
                // whose screen-off went unheard, woke up with hours already on today's clock.
                if (store.getLong("sessionStartMs", -1) >= 0) putLong("sessionStartMs", startOfTodayMs())
            }
        }
    }

    private fun startOfTodayMs(): Long = java.util.Calendar.getInstance().apply {
        timeInMillis = nowMs()
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun addScreenTime(ms: Long) {
        if (ms <= 0) return
        rolloverIfNeeded()
        store.edit { putLong("totalScreenTimeMs", store.getLong("totalScreenTimeMs", 0) + ms) }
    }

    /** Call when the device is unlocked, to start a live screen-time session. */
    fun startSession() {
        rolloverIfNeeded()
        store.edit { putLong("sessionStartMs", nowMs()) }
    }

    /** Call when the screen turns off, to fold the just-finished session into today's total. */
    fun endSessionAndFlush() {
        val start = sessionStartMs ?: return
        addScreenTime(nowMs() - start)
        store.edit { remove("sessionStartMs") }
    }

    private val sessionStartMs: Long?
        get() {
            val v = store.getLong("sessionStartMs", -1)
            return if (v < 0) null else v
        }

    /** Time spent in an app a parent excluded from the overall limit; taken off today's total. */
    fun addExcludedTime(ms: Long) {
        if (ms <= 0) return
        rolloverIfNeeded()
        store.edit { putLong("excludedMs", store.getLong("excludedMs", 0) + ms) }
    }

    /**
     * Time in [packageName] in the foreground: added to that app's usage, and, if it does not
     * [countsTowardTotal] (a parent excluded it), also taken off today's total.
     */
    fun addForegroundTime(packageName: String, ms: Long, countsTowardTotal: Boolean) {
        addAppTime(packageName, ms)
        if (!countsTowardTotal) addExcludedTime(ms)
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
            val ended = store.getLong("totalScreenTimeMs", 0)
            val start = sessionStartMs
            // Only while the screen is actually on: an open session with the screen off is one whose
            // end was never heard, and counting it is how a day reached fourteen hours.
            val inProgress = if (start == null || !isScreenOn()) 0L else (nowMs() - start).coerceAtLeast(0)
            return countedScreenTimeMs(ended + inProgress, store.getLong("excludedMs", 0))
        }

    /** Today's counted screen time, for reports and sync; the same figure the daily limit uses. */
    val totalScreenTimeMs: Long
        get() = liveTotalScreenTimeMs

    fun markDailyWarned() {
        rolloverIfNeeded()
        store.edit { putBoolean("warnedDaily", true) }
    }

    override val dailyWarned: Boolean
        get() {
            rolloverIfNeeded()
            return store.getBoolean("warnedDaily", false)
        }

    fun markAppWarned(packageName: String) {
        rolloverIfNeeded()
        val set = warnedApps + packageName
        store.edit { putStringSet("warnedApps", set) }
    }

    override val warnedApps: Set<String>
        get() {
            rolloverIfNeeded()
            return store.getStringSet("warnedApps")
        }

    /** Apps that have already shown the friction pause (see #12) today, so it shows once per app per day. */
    fun markAppPaused(packageName: String) {
        rolloverIfNeeded()
        val set = pausedApps + packageName
        store.edit { putStringSet("pausedApps", set) }
    }

    override val pausedApps: Set<String>
        get() {
            rolloverIfNeeded()
            return store.getStringSet("pausedApps")
        }

    fun addAppTime(packageName: String, ms: Long) {
        if (ms <= 0) return
        rolloverIfNeeded()
        val map = appUsageMs.toMutableMap()
        map[packageName] = (map[packageName] ?: 0) + ms
        store.edit { putString("appUsage", json.encodeToString(map)) }
    }

    fun incrementUnlockCount() {
        rolloverIfNeeded()
        store.edit { putInt("unlockCount", unlockCount + 1) }
    }

    // --- See #35: optional tracking. Only ever written to while a parent has the matching toggle on. ---

    /** Counts one notification from [packageName] today - a count only, never the notification's content. */
    fun recordNotification(packageName: String) {
        rolloverIfNeeded()
        val map = notificationCountsByApp.toMutableMap()
        map[packageName] = (map[packageName] ?: 0) + 1
        store.edit { putString("notificationsByApp", json.encodeToString(map)) }
    }

    val notificationCountsByApp: Map<String, Int>
        get() {
            rolloverIfNeeded()
            return decode(store.getString("notificationsByApp", "{}")!!)
        }

    val notificationCount: Int
        get() = notificationCountsByApp.values.sum()

    /** Marks an unlock at [nowMs] as still waiting to see which app is opened first. */
    fun markUnlockAwaitingFirstApp(nowMs: Long) {
        rolloverIfNeeded()
        store.edit { putLong("unlockAwaitingMs", nowMs) }
    }

    val unlockAwaitingMs: Long?
        get() {
            val v = store.getLong("unlockAwaitingMs", -1)
            return if (v < 0) null else v
        }

    fun clearUnlockAwaiting() {
        store.edit { remove("unlockAwaitingMs") }
    }

    /** Records [packageName] as the app opened first after the pending unlock, and stops waiting. */
    fun recordFirstAppAfterUnlock(packageName: String) {
        rolloverIfNeeded()
        val map = firstAppsAfterUnlock.toMutableMap()
        map[packageName] = (map[packageName] ?: 0) + 1
        store.edit {
            putString("firstAppsAfterUnlock", json.encodeToString(map))
            remove("unlockAwaitingMs")
        }
    }

    val firstAppsAfterUnlock: Map<String, Int>
        get() {
            rolloverIfNeeded()
            return decode(store.getString("firstAppsAfterUnlock", "{}")!!)
        }

    // --- See #41: optional website tracking. Sites looked up while a browser was open; only written to
    // while the matching toggle is on. Site names only - never a page, search or time. ---

    /** Adds [counts] (site -> lookups) to today's tally, keeping only the busiest sites. */
    fun addWebsiteCounts(counts: Map<String, Int>) {
        if (counts.isEmpty()) return
        rolloverIfNeeded()
        val merged = websiteCounts.toMutableMap()
        counts.forEach { (site, n) -> merged[site] = (merged[site] ?: 0) + n }
        store.edit { putString("websiteCounts", json.encodeToString(capWebsites(merged))) }
    }

    val websiteCounts: Map<String, Int>
        get() {
            rolloverIfNeeded()
            return decode(store.getString("websiteCounts", "{}")!!)
        }

    fun cacheAppName(packageName: String, name: String) {
        if (appNames[packageName] == name) return
        val map = appNames.toMutableMap()
        map[packageName] = name
        store.edit { putString("appNames", json.encodeToString(map)) }
    }

    val date: String
        get() {
            rolloverIfNeeded()
            return store.getString("date", todayString())!!
        }

    val unlockCount: Int
        get() {
            rolloverIfNeeded()
            return store.getInt("unlockCount", 0)
        }

    override val appUsageMs: Map<String, Long>
        get() {
            rolloverIfNeeded()
            return decode(store.getString("appUsage", "{}")!!)
        }

    val appNames: Map<String, String>
        get() = decode(store.getString("appNames", "{}")!!)

    private inline fun <reified T> decode(raw: String): Map<String, T> =
        try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            emptyMap()
        }
}
