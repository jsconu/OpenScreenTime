package org.openscreentime.kid.monitor

import android.content.Context
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.util.FocusPrefs

/**
 * The kid device's live view of the paired child's Firestore-synced settings - limits,
 * lock state, bedtime window, and the blocked-domains list. Written from one place
 * (KidApp.startLimitsListener()'s Firestore callback) and read by both
 * [AppLimitAccessibilityService] and [DnsSinkholeVpnService], so "what does this device
 * currently know about the child" has one name instead of being spread across two
 * Services' own companion objects.
 *
 * `@Volatile` on every field: written on the main thread from the Firestore listener,
 * read from whichever thread each reader's own work happens on ([AppLimitAccessibilityService]
 * reads from its Handler's thread; [DnsSinkholeVpnService] reads [blockedDomains] from its
 * own background packet-processing thread).
 *
 * The values are also persisted ([persist]) and restored ([restore]) by KidApp, because this
 * object is otherwise empty after every process restart - and the call-screening services,
 * SyncWorker and the accessibility service can all be the first thing to run in a fresh
 * process, before Firestore's listener has delivered anything. Without that they would
 * enforce the defaults below (no bedtime, no lock, no tracking) until the first snapshot.
 */
object LiveChildState {
    @Volatile var limitsCache: Map<String, Int> = emptyMap()
    @Volatile var dailyLimitMinutes: Int = Int.MAX_VALUE
    @Volatile var lockedCache: Boolean = false
    /** Informational only (see #10) - factors into the status icon, never blocks. */
    @Volatile var dailyUnlockGoal: Int? = null
    /** Minutes since local midnight; either null = no bedtime window set. See #15. */
    @Volatile var bedtimeStartMinutes: Int? = null
    @Volatile var bedtimeEndMinutes: Int? = null
    /** See #19. */
    @Volatile var blockedDomains: List<String> = emptyList()
    /** See #23 - a parent-granted "more time" window; null or in the past means no active grant. */
    @Volatile var temporaryUnlockUntilMs: Long? = null
    /** See #28 - packages that bypass bedtime and every daily/app-limit check. */
    @Volatile var alwaysAllowedPackages: Set<String> = emptySet()
    /** See #34 - phone numbers that can still call/text through a bedtime block. */
    @Volatile var alwaysAllowedContacts: List<String> = emptyList()
    /** See #35 - parent-controlled tracking toggles; nothing is collected while these are off. */
    @Volatile var trackUnlocks: Boolean = false
    @Volatile var trackNotifications: Boolean = false
    /** See #41 - count sites looked up while a browser is open (needs the website filter on). */
    @Volatile var trackWebsites: Boolean = false
    /** The package in front right now (not persisted); lets the website filter count only browser lookups. */
    @Volatile var foregroundPackage: String? = null
    /** The family passcode's salted hash, so the lock screen can check a parent's PIN offline. */
    @Volatile var parentPasscodeHash: String? = null
    @Volatile var parentPasscodeSalt: String? = null
    /** When a parent's timed unlock ends and the phone locks itself again; null = no re-lock pending. */
    @Volatile var relockAtMs: Long? = null

    private const val PREFS = "live_child_state"

    /** Copies a freshly-received profile into memory and to disk. */
    fun update(context: Context, child: ChildProfile) {
        limitsCache = child.appLimits
        dailyLimitMinutes = child.dailyLimitMinutes
        dailyUnlockGoal = child.dailyUnlockGoal
        bedtimeStartMinutes = child.bedtimeStartMinutes
        bedtimeEndMinutes = child.bedtimeEndMinutes
        blockedDomains = child.blockedDomains
        temporaryUnlockUntilMs = child.temporaryUnlockUntilMs
        alwaysAllowedPackages = child.alwaysAllowedPackages.toSet()
        alwaysAllowedContacts = child.alwaysAllowedContacts
        trackUnlocks = child.trackUnlocks
        trackNotifications = child.trackNotifications
        trackWebsites = child.trackWebsites
        lockedCache = child.locked
        // A parent locking it again (or it already being locked) cancels any pending timed re-lock.
        if (child.locked) relockAtMs = null
        parentPasscodeHash = child.parentPasscodeHash
        parentPasscodeSalt = child.parentPasscodeSalt
        persist(context)
    }

    /** Applies a parent's PIN-confirmed unlock immediately, without waiting for the sync round trip. */
    fun clearLock(context: Context, relockAtMs: Long? = null) {
        lockedCache = false
        this.relockAtMs = relockAtMs
        persist(context)
    }

    /** A timed unlock has run out: lock again on this phone (the caller also tells Firestore). */
    fun relockNow(context: Context) {
        lockedCache = true
        relockAtMs = null
        persist(context)
    }

    private fun persist(context: Context) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        e.putBoolean("has", true)
        // Maps/lists are stored as newline-joined strings; package names, numbers and domains
        // never contain a newline.
        e.putString("limits", limitsCache.entries.joinToString("\n") { "${it.key}=${it.value}" })
        e.putInt("daily", dailyLimitMinutes)
        e.putInt("goal", dailyUnlockGoal ?: -1)
        e.putInt("bedStart", bedtimeStartMinutes ?: -1)
        e.putInt("bedEnd", bedtimeEndMinutes ?: -1)
        e.putString("domains", blockedDomains.joinToString("\n"))
        e.putLong("tempUnlock", temporaryUnlockUntilMs ?: -1L)
        e.putString("packages", alwaysAllowedPackages.joinToString("\n"))
        e.putString("contacts", alwaysAllowedContacts.joinToString("\n"))
        e.putBoolean("trackUnlocks", trackUnlocks)
        e.putBoolean("trackNotifications", trackNotifications)
        e.putBoolean("trackWebsites", trackWebsites)
        e.putBoolean("locked", lockedCache)
        e.putLong("relockAt", relockAtMs ?: -1L)
        e.putString("pcHash", parentPasscodeHash)
        e.putString("pcSalt", parentPasscodeSalt)
        e.apply()
    }

    /** Loads the last-known state from disk, if there is any; call once at process start. */
    fun restore(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean("has", false)) return
        fun lines(key: String) = (p.getString(key, "") ?: "").split("\n").filter { it.isNotEmpty() }
        limitsCache = lines("limits").mapNotNull {
            val i = it.lastIndexOf('=')
            val minutes = if (i > 0) it.substring(i + 1).toIntOrNull() else null
            if (minutes == null) null else it.substring(0, i) to minutes
        }.toMap()
        dailyLimitMinutes = p.getInt("daily", Int.MAX_VALUE)
        dailyUnlockGoal = p.getInt("goal", -1).takeIf { it >= 0 }
        bedtimeStartMinutes = p.getInt("bedStart", -1).takeIf { it >= 0 }
        bedtimeEndMinutes = p.getInt("bedEnd", -1).takeIf { it >= 0 }
        blockedDomains = lines("domains")
        temporaryUnlockUntilMs = p.getLong("tempUnlock", -1L).takeIf { it >= 0 }
        alwaysAllowedPackages = lines("packages").toSet()
        alwaysAllowedContacts = lines("contacts")
        trackUnlocks = p.getBoolean("trackUnlocks", false)
        trackNotifications = p.getBoolean("trackNotifications", false)
        trackWebsites = p.getBoolean("trackWebsites", false)
        lockedCache = p.getBoolean("locked", false)
        relockAtMs = p.getLong("relockAt", -1L).takeIf { it >= 0 }
        parentPasscodeHash = p.getString("pcHash", null)
        parentPasscodeSalt = p.getString("pcSalt", null)
    }

    /** Forgets everything - used when this device is unpaired. */
    fun clear(context: Context) {
        FocusPrefs(context).clear(context, org.openscreentime.kid.ui.FocusLauncherActivity.component(context))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        limitsCache = emptyMap()
        dailyLimitMinutes = Int.MAX_VALUE
        dailyUnlockGoal = null
        bedtimeStartMinutes = null
        bedtimeEndMinutes = null
        blockedDomains = emptyList()
        temporaryUnlockUntilMs = null
        alwaysAllowedPackages = emptySet()
        alwaysAllowedContacts = emptyList()
        trackUnlocks = false
        trackNotifications = false
        trackWebsites = false
        foregroundPackage = null
        lockedCache = false
        parentPasscodeHash = null
        parentPasscodeSalt = null
        relockAtMs = null
    }
}
