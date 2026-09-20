package org.openscreentime.shared.util

import android.content.Context
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.EnforcementSettings

/**
 * This phone's live copy of its person's profile: limits, lock, bedtime, blocked sites, tracking choices, the
 * family passcode's hash, and (through [FocusMode]) the dumb-phone choice. The kid app keeps one for the paired child
 * (`LiveChildState`), the parent app one for the parent's own phone (`SelfDeviceState`); they differ only in the
 * preferences file they save to and the Focus launcher they drive, so a new profile field is added here once.
 *
 * Written from one place (the app's Firestore listener, via [update]) and read by the accessibility service, the
 * website filter, call screening and sync work. `@Volatile` throughout: written on the main thread, read from
 * whichever thread each reader works on.
 *
 * The values are saved ([update] persists) and restored ([restore], called once at process start) because this
 * object is otherwise empty after every process restart, and a service can be the first thing to run in a fresh
 * process, before Firestore has delivered anything. Without that they would enforce the defaults below (no bedtime,
 * no lock, no tracking) until the first snapshot.
 */
open class DeviceProfileState(
    private val prefsName: String,
    /** The Focus mode to keep in step with the profile, if this phone has a dumb-phone home screen. */
    private val focus: ((Context) -> FocusMode)? = null
) {
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
    /** The family passcode's salted hash, so a lock screen can check a parent's PIN offline (a child's phone only). */
    @Volatile var parentPasscodeHash: String? = null
    @Volatile var parentPasscodeSalt: String? = null
    /** When a timed unlock ends and the phone locks itself again; null = no re-lock pending. */
    @Volatile var relockAtMs: Long? = null

    /** Copies a freshly-received profile into memory and to disk, and brings Focus mode in step with it. */
    fun update(context: Context, child: ChildProfile) {
        copyFrom(child)
        focus?.invoke(context)?.sync(child)
        persist(context)
    }

    /** Applies a PIN-confirmed unlock immediately, without waiting for the sync round trip. */
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

    /** Loads the last-known state from disk, if there is any; call once at process start. */
    fun restore(context: Context) {
        decode(context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).all)
    }

    /** Forgets everything - used when this phone is unpaired or stops being tracked. */
    fun clear(context: Context) {
        focus?.invoke(context)?.stop()
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().apply()
        reset()
    }

    internal fun copyFrom(child: ChildProfile) {
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
    }

    /**
     * What is saved, as plain key/value pairs. Maps/lists are stored as newline-joined strings; package names,
     * numbers and domains never contain a newline. The keys are the ones already on people's phones - keep them.
     */
    internal fun encode(): Map<String, Any?> = linkedMapOf(
        "has" to true,
        "limits" to limitsCache.entries.joinToString("\n") { "${it.key}=${it.value}" },
        "daily" to dailyLimitMinutes,
        "goal" to (dailyUnlockGoal ?: -1),
        "bedStart" to (bedtimeStartMinutes ?: -1),
        "bedEnd" to (bedtimeEndMinutes ?: -1),
        "domains" to blockedDomains.joinToString("\n"),
        "tempUnlock" to (temporaryUnlockUntilMs ?: -1L),
        "packages" to alwaysAllowedPackages.joinToString("\n"),
        "contacts" to alwaysAllowedContacts.joinToString("\n"),
        "trackUnlocks" to trackUnlocks,
        "trackNotifications" to trackNotifications,
        "trackWebsites" to trackWebsites,
        "locked" to lockedCache,
        "relockAt" to (relockAtMs ?: -1L),
        "pcHash" to parentPasscodeHash,
        "pcSalt" to parentPasscodeSalt
    )

    /** The reverse of [encode]. Anything missing keeps its default; nothing saved yet leaves everything alone. */
    internal fun decode(saved: Map<String, *>) {
        if (saved["has"] != true) return
        fun lines(key: String) = (saved[key] as? String).orEmpty().split("\n").filter { it.isNotEmpty() }
        limitsCache = lines("limits").mapNotNull {
            val i = it.lastIndexOf('=')
            val minutes = if (i > 0) it.substring(i + 1).toIntOrNull() else null
            if (minutes == null) null else it.substring(0, i) to minutes
        }.toMap()
        dailyLimitMinutes = saved["daily"] as? Int ?: Int.MAX_VALUE
        dailyUnlockGoal = (saved["goal"] as? Int)?.takeIf { it >= 0 }
        bedtimeStartMinutes = (saved["bedStart"] as? Int)?.takeIf { it >= 0 }
        bedtimeEndMinutes = (saved["bedEnd"] as? Int)?.takeIf { it >= 0 }
        blockedDomains = lines("domains")
        temporaryUnlockUntilMs = (saved["tempUnlock"] as? Long)?.takeIf { it >= 0 }
        alwaysAllowedPackages = lines("packages").toSet()
        alwaysAllowedContacts = lines("contacts")
        trackUnlocks = saved["trackUnlocks"] as? Boolean ?: false
        trackNotifications = saved["trackNotifications"] as? Boolean ?: false
        trackWebsites = saved["trackWebsites"] as? Boolean ?: false
        lockedCache = saved["locked"] as? Boolean ?: false
        relockAtMs = (saved["relockAt"] as? Long)?.takeIf { it >= 0 }
        parentPasscodeHash = saved["pcHash"] as? String
        parentPasscodeSalt = saved["pcSalt"] as? String
    }

    internal fun reset() {
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

    private fun persist(context: Context) {
        val editor = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
        for ((key, value) in encode()) {
            when (value) {
                null -> editor.remove(key)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()
    }
}

/** The foreground guard's view of a [DeviceProfileState]: its limits are whatever this phone currently knows. */
class DeviceProfileSettings(private val state: DeviceProfileState) : EnforcementSettings {
    override val locked get() = state.lockedCache
    override val bedtimeStartMinutes get() = state.bedtimeStartMinutes
    override val bedtimeEndMinutes get() = state.bedtimeEndMinutes
    override val dailyLimitMinutes get() = state.dailyLimitMinutes
    override fun appLimitMinutes(packageName: String) = state.limitsCache[packageName]
    override val temporaryUnlockUntilMs get() = state.temporaryUnlockUntilMs
    override val alwaysAllowedPackages get() = state.alwaysAllowedPackages
    override val relockAtMs get() = state.relockAtMs
    override var foregroundPackage: String?
        get() = state.foregroundPackage
        set(value) { state.foregroundPackage = value }
}
