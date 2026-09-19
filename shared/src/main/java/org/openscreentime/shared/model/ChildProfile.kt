package org.openscreentime.shared.model

/**
 * A child profile owned by a parent account. Lives at
 * parents/{parentUid}/children/{childId} in Firestore.
 */
data class ChildProfile(
    val id: String = "",
    val name: String = "",
    val pairingCode: String = "",
    val paired: Boolean = false,
    val deviceUid: String? = null,
    val dailyLimitMinutes: Int = 120,
    val appLimits: Map<String, Int> = emptyMap(),
    /** True while a parent has hit "Lock now" - blocks all apps on the kid device immediately. */
    val locked: Boolean = false,
    /**
     * A copy of the parent's passcode hash/salt (see [org.openscreentime.shared.util.PasscodeHasher]),
     * denormalized onto every child so the paired kid device can verify a passcode entered locally
     * ("parent mode") without needing read access to the parent's own account document.
     */
    val parentPasscodeHash: String? = null,
    val parentPasscodeSalt: String? = null,
    /**
     * True for the one special child doc, per parent, that represents the parent's
     * OWN device rather than a paired kid's - see #8. Created and claimed directly
     * by the parent app (deviceUid = the parent's own uid) with no pairing code,
     * since the parent app is already signed in as parentUid.
     */
    val isSelf: Boolean = false,
    /** Informational only - never enforced/blocked. See #10. */
    val dailyUnlockGoal: Int? = null,
    /**
     * A kid-proposed daily limit awaiting parent approval, or null when there's no
     * pending proposal. See #14 - autonomy-supportive negotiated limits: the kid app
     * can write this (and [proposedAppLimits]) directly, with no passcode required,
     * since the whole point is a kid can suggest a change without a parent present.
     * Approving copies this into [dailyLimitMinutes] and clears both proposal fields;
     * declining just clears them.
     */
    val proposedDailyLimitMinutes: Int? = null,
    /** A kid-proposed replacement for [appLimits] awaiting parent approval. See #14. */
    val proposedAppLimits: Map<String, Int>? = null,
    /**
     * Minutes since local midnight (0-1439). Both null = no bedtime window set. A full
     * block, independent of the daily minute-count limit - see #15. [bedtimeStartMinutes]
     * may be greater than [bedtimeEndMinutes] for a window that spans midnight (e.g.
     * 21:00-07:00 is 1260-420); see [isInBedtimeWindow] for the wraparound-safe check.
     */
    val bedtimeStartMinutes: Int? = null,
    val bedtimeEndMinutes: Int? = null,
    /**
     * Domains blocked device-wide, in any browser, via the kid device's local DNS-sinkhole
     * VPN (see #19). Suffix-matched - blocking "tiktok.com" also blocks "m.tiktok.com" (see
     * [isDomainBlocked]). Shown read-only in the kid app; only a parent can edit it.
     */
    val blockedDomains: List<String> = emptyList(),
    /**
     * A kid-requested "more time" amount in minutes (5 or 15), awaiting parent approval, or
     * null when there's no pending request - see #23. Requested from the block screen, so
     * it only makes sense while the kid is actually blocked; the kid app writes this with no
     * passcode required, same autonomy-supportive pattern as [proposedDailyLimitMinutes].
     * Granting sets [temporaryUnlockUntilMs] and clears this; declining just clears it.
     */
    val requestedExtraMinutes: Int? = null,
    /**
     * Epoch milliseconds until which the kid is temporarily let through bedtime and any
     * daily/app-limit block - never a parent lock, which stays absolute. Set only by a
     * parent granting a [requestedExtraMinutes] request, never by the kid device itself.
     * See #23 and [decideEnforcement].
     */
    val temporaryUnlockUntilMs: Long? = null,
    /**
     * Packages that stay usable no matter what - they bypass the daily limit, every
     * per-app limit, and bedtime, the same way [temporaryUnlockUntilMs] does. Meant for a
     * small allowlist a parent trusts unconditionally (a phone/calling app, maps), not a
     * per-app limit override. Unlike [temporaryUnlockUntilMs], a parent lock still always
     * wins - that stays the one absolute signal, same reasoning as #23. See
     * [decideEnforcement].
     */
    val alwaysAllowedPackages: List<String> = emptyList(),
    /**
     * Phone numbers that can still call/text during bedtime, when every other number is
     * blocked - see #34. Meant for a parent's own number(s), so a kid is never truly
     * unreachable overnight. Normalized loosely (digits only, last-10-compared) when
     * matched against an incoming call/text, not stored normalized, so a parent can enter
     * a number however they'd naturally type it.
     */
    val alwaysAllowedContacts: List<String> = emptyList(),
    /**
     * Optional, parent-controlled tracking categories - see #35. Off by default, and nothing
     * is collected on the device for a category until its toggle is on. [trackUnlocks] adds
     * "which app was opened first after each unlock" and puts unlocks in the weekly report and
     * daily digest; [trackNotifications] counts notifications received, overall and by app
     * (counts only, never content). Both work the same for the parent's own self profile.
     */
    val trackUnlocks: Boolean = false,
    val trackNotifications: Boolean = false,
    /**
     * Whether today's count for a tracked category is also shown on the kid's own phone, with
     * a note that watching counts can feed compulsive checking. Off by default, and only
     * meaningful while the matching track flag is on. Unused on the self profile.
     */
    val showUnlocksOnKid: Boolean = false,
    val showNotificationsOnKid: Boolean = false
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "pairingCode" to pairingCode,
        "paired" to paired,
        "deviceUid" to deviceUid,
        "dailyLimitMinutes" to dailyLimitMinutes,
        "appLimits" to appLimits,
        "locked" to locked,
        "parentPasscodeHash" to parentPasscodeHash,
        "parentPasscodeSalt" to parentPasscodeSalt,
        "isSelf" to isSelf,
        "dailyUnlockGoal" to dailyUnlockGoal,
        "proposedDailyLimitMinutes" to proposedDailyLimitMinutes,
        "proposedAppLimits" to proposedAppLimits,
        "bedtimeStartMinutes" to bedtimeStartMinutes,
        "bedtimeEndMinutes" to bedtimeEndMinutes,
        "blockedDomains" to blockedDomains,
        "requestedExtraMinutes" to requestedExtraMinutes,
        "temporaryUnlockUntilMs" to temporaryUnlockUntilMs,
        "alwaysAllowedPackages" to alwaysAllowedPackages,
        "alwaysAllowedContacts" to alwaysAllowedContacts,
        "trackUnlocks" to trackUnlocks,
        "trackNotifications" to trackNotifications,
        "showUnlocksOnKid" to showUnlocksOnKid,
        "showNotificationsOnKid" to showNotificationsOnKid
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(id: String, map: Map<String, Any?>): ChildProfile = ChildProfile(
            id = id,
            name = map["name"] as? String ?: "",
            pairingCode = map["pairingCode"] as? String ?: "",
            paired = map["paired"] as? Boolean ?: false,
            deviceUid = map["deviceUid"] as? String,
            dailyLimitMinutes = (map["dailyLimitMinutes"] as? Long)?.toInt() ?: 120,
            appLimits = (map["appLimits"] as? Map<String, Long>)?.mapValues { it.value.toInt() } ?: emptyMap(),
            locked = map["locked"] as? Boolean ?: false,
            parentPasscodeHash = map["parentPasscodeHash"] as? String,
            parentPasscodeSalt = map["parentPasscodeSalt"] as? String,
            isSelf = map["isSelf"] as? Boolean ?: false,
            dailyUnlockGoal = (map["dailyUnlockGoal"] as? Long)?.toInt(),
            proposedDailyLimitMinutes = (map["proposedDailyLimitMinutes"] as? Long)?.toInt(),
            proposedAppLimits = (map["proposedAppLimits"] as? Map<String, Long>)?.mapValues { it.value.toInt() },
            bedtimeStartMinutes = (map["bedtimeStartMinutes"] as? Long)?.toInt(),
            bedtimeEndMinutes = (map["bedtimeEndMinutes"] as? Long)?.toInt(),
            blockedDomains = (map["blockedDomains"] as? List<String>) ?: emptyList(),
            requestedExtraMinutes = (map["requestedExtraMinutes"] as? Long)?.toInt(),
            temporaryUnlockUntilMs = map["temporaryUnlockUntilMs"] as? Long,
            alwaysAllowedPackages = (map["alwaysAllowedPackages"] as? List<String>) ?: emptyList(),
            alwaysAllowedContacts = (map["alwaysAllowedContacts"] as? List<String>) ?: emptyList(),
            trackUnlocks = map["trackUnlocks"] as? Boolean ?: false,
            trackNotifications = map["trackNotifications"] as? Boolean ?: false,
            showUnlocksOnKid = map["showUnlocksOnKid"] as? Boolean ?: false,
            showNotificationsOnKid = map["showNotificationsOnKid"] as? Boolean ?: false
        )
    }
}
