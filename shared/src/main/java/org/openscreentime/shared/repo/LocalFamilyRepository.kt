package org.openscreentime.shared.repo

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openscreentime.shared.model.AppList
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.model.FocusProfile
import org.openscreentime.shared.model.InstalledApp
import org.openscreentime.shared.model.PasscodeInfo
import org.openscreentime.shared.model.TrackingToggle
import org.openscreentime.shared.model.todayDateString
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * A family that lives entirely on one phone: the profile, its limits, the passcode that guards them
 * and the usage history are all in this phone's own storage, and nothing is ever sent anywhere.
 *
 * This is the whole backend of a local build. The trade is deliberate and worth naming: with no
 * second phone in the picture, limits are changed in person (the passcode-gated Parent controls
 * screen) rather than remotely, there is no pairing, and a parent cannot see or lock a child's phone
 * from their own. Everything a phone does by itself - counting time, enforcing limits and bedtime,
 * dumb phone mode, the calm list - is unaffected, because none of it ever needed a network.
 *
 * Calls that only mean something between two paired phones are [RemoteOnly]: they no-op here where
 * nothing has to be returned, and throw where a value does. No screen in a local build offers them.
 *
 * @param todayStats this phone's own usage for today, read from its [org.openscreentime.shared.util.DayLedger].
 */
class LocalFamilyRepository(
    context: Context,
    private val todayStats: () -> DailyStats,
    private val now: () -> Long = System::currentTimeMillis
) : FamilyRepository {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val handler = Handler(Looper.getMainLooper())
    private val profileListeners = mutableSetOf<(ChildProfile) -> Unit>()

    /** There is no account, so "who is this" is the phone itself, under one stable id. */
    override val currentUid: String get() = LOCAL_UID

    // --- The one profile this phone has ---

    private fun storedProfile(): ChildProfile? =
        prefs.getString(KEY_PROFILE, null)?.let { runCatching { json.decodeFromString<ChildProfile>(it) }.getOrNull() }

    private fun save(profile: ChildProfile) {
        prefs.edit().putString(KEY_PROFILE, json.encodeToString(profile)).apply()
        // Copied out first: a listener may unregister itself while being notified.
        for (listener in profileListeners.toList()) listener(profile)
    }

    /** Applies [change] to the stored profile, creating it first if this is a fresh install. */
    private fun edit(change: (ChildProfile) -> ChildProfile) {
        val current = storedProfile()
            ?: ChildProfile(id = Profiles.SELF_CHILD_ID, paired = true, deviceUid = LOCAL_UID)
        save(change(current))
    }

    override suspend fun getOrCreateSelfProfile(parentUid: String, name: String): ChildProfile {
        storedProfile()?.let { return it }
        val fresh = ChildProfile(
            id = Profiles.SELF_CHILD_ID,
            name = name,
            paired = true,
            deviceUid = LOCAL_UID,
            isSelf = true
        )
        save(fresh)
        return fresh
    }

    override fun listenChild(parentUid: String, childId: String, onChange: (ChildProfile) -> Unit): Registration {
        storedProfile()?.let(onChange)
        profileListeners += onChange
        return Registration { profileListeners -= onChange }
    }

    // --- Passcode: the only gate on changing limits when there is no second phone ---

    override suspend fun getParentPasscode(parentUid: String): PasscodeInfo? {
        val hash = prefs.getString(KEY_PASSCODE_HASH, null) ?: return null
        val salt = prefs.getString(KEY_PASSCODE_SALT, null) ?: return null
        return PasscodeInfo(hash, salt)
    }

    override suspend fun setParentPasscode(parentUid: String, hash: String, salt: String) {
        prefs.edit().putString(KEY_PASSCODE_HASH, hash).putString(KEY_PASSCODE_SALT, salt).apply()
        // Kept on the profile too, exactly as the cloud build does, so the same unlock screen reads it.
        edit { it.copy(parentPasscodeHash = hash, parentPasscodeSalt = salt) }
    }

    // --- Limits and state ---

    override suspend fun setLocked(parentUid: String, childId: String, locked: Boolean) =
        edit { it.copy(locked = locked) }

    override suspend fun updateDailyLimit(parentUid: String, childId: String, minutes: Int) =
        edit { it.copy(dailyLimitMinutes = minutes) }

    override suspend fun setAppLimit(parentUid: String, childId: String, packageName: String, minutes: Int) =
        edit { it.copy(appLimits = it.appLimits + (packageName to minutes)) }

    override suspend fun updateDailyUnlockGoal(parentUid: String, childId: String, goal: Int?) =
        edit { it.copy(dailyUnlockGoal = goal) }

    override suspend fun updateBedtimeWindow(parentUid: String, childId: String, startMinutes: Int?, endMinutes: Int?) =
        edit { it.copy(bedtimeStartMinutes = startMinutes, bedtimeEndMinutes = endMinutes) }

    override suspend fun addBlockedDomain(parentUid: String, childId: String, domain: String) =
        edit { it.copy(blockedDomains = (it.blockedDomains + domain).distinct()) }

    override suspend fun removeBlockedDomain(parentUid: String, childId: String, domain: String) =
        edit { it.copy(blockedDomains = it.blockedDomains - domain) }

    override suspend fun setAppListMember(
        parentUid: String,
        childId: String,
        list: AppList,
        packageName: String,
        member: Boolean
    ) = edit { profile ->
        val next = if (member) {
            (profile.packages(list) + packageName).distinct()
        } else {
            profile.packages(list) - packageName
        }
        when (list) {
            AppList.ALWAYS_ALLOWED -> profile.copy(alwaysAllowedPackages = next)
            AppList.FOCUS_ALLOWED -> profile.copy(focusAllowedPackages = next)
            AppList.TRAVEL_ALLOWED -> profile.copy(travelAllowedPackages = next)
            AppList.EXCLUDED_FROM_TOTAL -> profile.copy(excludedFromTotalPackages = next)
        }
    }

    override suspend fun setTrackingToggle(
        parentUid: String,
        childId: String,
        toggle: TrackingToggle,
        enabled: Boolean
    ) = edit { profile ->
        when (toggle) {
            TrackingToggle.TRACK_UNLOCKS -> profile.copy(trackUnlocks = enabled)
            TrackingToggle.TRACK_NOTIFICATIONS -> profile.copy(trackNotifications = enabled)
            TrackingToggle.SHOW_UNLOCKS_ON_KID -> profile.copy(showUnlocksOnKid = enabled)
            TrackingToggle.SHOW_NOTIFICATIONS_ON_KID -> profile.copy(showNotificationsOnKid = enabled)
            TrackingToggle.TRACK_WEBSITES -> profile.copy(trackWebsites = enabled)
        }
    }

    override suspend fun setFocusMode(parentUid: String, childId: String, enabled: Boolean) =
        edit { it.copy(focusMode = enabled) }

    override suspend fun setFocusProfile(parentUid: String, childId: String, profile: FocusProfile) =
        edit { it.copy(focusProfile = profile.wireValue) }

    override suspend fun addAllowedContact(parentUid: String, childId: String, number: String) =
        edit { it.copy(alwaysAllowedContacts = (it.alwaysAllowedContacts + number).distinct()) }

    override suspend fun removeAllowedContact(parentUid: String, childId: String, number: String) =
        edit { it.copy(alwaysAllowedContacts = it.alwaysAllowedContacts - number) }

    /** Unlike the rest of the "more time" flow, granting is local: the person is holding the phone. */
    override suspend fun grantExtraTime(parentUid: String, childId: String, minutes: Int) =
        edit { it.copy(temporaryUnlockUntilMs = now() + minutes * 60_000L, requestedExtraMinutes = null) }

    // --- Usage, kept day by day in this phone's own storage ---

    /**
     * Today's usage, archived every time it is read. That is what gives a local build a history to
     * draw a weekly report and a streak from without a sync worker or a server: whatever the last
     * reading of a day was is what that day keeps.
     */
    private fun snapshotToday(): DailyStats {
        val stats = todayStats()
        if (stats.date.isNotEmpty()) {
            prefs.edit().putString(KEY_STATS + stats.date, json.encodeToString(stats)).apply()
        }
        return stats
    }

    private fun archived(date: String): DailyStats? =
        prefs.getString(KEY_STATS + date, null)?.let { runCatching { json.decodeFromString<DailyStats>(it) }.getOrNull() }

    override fun listenDailyStats(
        parentUid: String,
        childId: String,
        date: String,
        onChange: (DailyStats) -> Unit
    ): Registration {
        // No document to watch, so today is re-read on a slow tick while a screen is showing it;
        // any other date is settled history and delivered once.
        if (date != todayDateString()) {
            archived(date)?.let(onChange)
            return Registration { }
        }
        var live = true
        val tick = object : Runnable {
            override fun run() {
                if (!live) return
                onChange(snapshotToday())
                handler.postDelayed(this, REFRESH_MS)
            }
        }
        handler.post(tick)
        return Registration {
            live = false
            handler.removeCallbacks(tick)
        }
    }

    override suspend fun getRecentDailyStats(parentUid: String, childId: String, days: Int): List<DailyStats> {
        snapshotToday()
        // Oldest first, excluding today. A day with nothing saved is simply absent, which the streak
        // treats as "cannot confirm" rather than an automatic pass - same as the cloud build.
        return (days downTo 1).mapNotNull { back -> archived(dateDaysAgo(back)) }
    }

    private fun dateDaysAgo(days: Int): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }

    /** Nothing to push to: the numbers are already where they are going to live. */
    override suspend fun pushDailyStats(parentUid: String, childId: String, stats: DailyStats) {
        if (stats.date.isNotEmpty()) {
            prefs.edit().putString(KEY_STATS + stats.date, json.encodeToString(stats)).apply()
        }
    }

    // --- Everything below needs a second phone ---

    override fun signOut() = Unit

    override suspend fun pushInstalledApps(parentUid: String, childId: String, apps: List<InstalledApp>) = Unit

    override fun listenInstalledApps(
        parentUid: String,
        childId: String,
        onChange: (List<InstalledApp>) -> Unit
    ): Registration = Registration { }

    override suspend fun sendPasswordReset(email: String) = Unit

    override suspend fun verifyAccountPassword(password: String) = false

    override suspend fun getParentSelfProfile(parentUid: String): ChildProfile? = null

    override fun listenChildren(parentUid: String, onChange: (List<ChildProfile>) -> Unit): Registration {
        onChange(emptyList())
        return Registration { }
    }

    override suspend fun proposeLimits(
        parentUid: String,
        childId: String,
        proposedDailyLimitMinutes: Int?,
        proposedAppLimits: Map<String, Int>?
    ) = Unit

    override suspend fun approveProposal(parentUid: String, childId: String, child: ChildProfile) = Unit

    override suspend fun declineProposal(parentUid: String, childId: String) = Unit

    override suspend fun requestExtraTime(parentUid: String, childId: String, minutes: Int) = Unit

    override suspend fun declineExtraTimeRequest(parentUid: String, childId: String) = Unit

    override suspend fun deleteChild(parentUid: String, childId: String) = Unit

    override suspend fun submitFeedback(parentUid: String, text: String, appVersion: String, device: String) = Unit

    override suspend fun signUpParent(email: String, password: String): String = noAccount()

    override suspend fun signInParent(email: String, password: String): String = noAccount()

    override suspend fun signInAnonymously(): String = LOCAL_UID

    override suspend fun createChild(parentUid: String, name: String): ChildProfile = noSecondPhone()

    override suspend fun regeneratePairingCode(parentUid: String, childId: String): String = noSecondPhone()

    override suspend fun claimPairingCode(code: String): Pair<String, ChildProfile> = noSecondPhone()

    private fun noAccount(): Nothing =
        throw UnsupportedOperationException(
            "This build keeps everything on this phone and has no account to sign in to."
        )

    private fun noSecondPhone(): Nothing =
        throw UnsupportedOperationException(
            "Pairing a second phone needs a cloud build - see docs/LOCAL_AND_CLOUD.md."
        )

    private companion object {
        const val PREFS = "local_family"
        const val KEY_PROFILE = "profile"
        const val KEY_PASSCODE_HASH = "passcode_hash"
        const val KEY_PASSCODE_SALT = "passcode_salt"
        const val KEY_STATS = "stats_"
        const val LOCAL_UID = "local"
        const val REFRESH_MS = 10_000L
    }
}
