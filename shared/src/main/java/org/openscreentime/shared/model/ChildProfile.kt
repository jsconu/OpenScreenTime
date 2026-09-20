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
     * Never stored on the parent's own self profile, which every linked kid device can read (#18).
     * Because a paired kid device can read this, a 4-6 digit passcode is guessable offline - the
     * passcode is a deterrent, not a boundary (see the note at the top of firestore.rules).
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
     * daily/app-limit block - never a parent lock, which stays absolute. By design set only
     * by a parent granting a [requestedExtraMinutes] request - but that's enforced by the apps,
     * not by the security rules: a paired kid device's own credentials can technically write it
     * (see the note at the top of firestore.rules). See #23 and [decideEnforcement].
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
     * Optional, parent-controlled tracking categories - see #35. Off by default. [trackUnlocks]
     * adds "which app was opened first after each unlock" and shows unlocks in the weekly report
     * and daily digest (the plain daily unlock count is always kept, for the unlock goal);
     * [trackNotifications] counts notifications received, overall and by app (counts only, never
     * content) - nothing about notifications is collected until it's on. Both work the same for
     * the parent's own self profile.
     */
    val trackUnlocks: Boolean = false,
    val trackNotifications: Boolean = false,
    /**
     * See #41 - counts which SITES this device looks up while a browser is open (names only, never pages
     * or time), via the website filter. Needs that filter turned on on the device.
     */
    val trackWebsites: Boolean = false,
    /**
     * "Dumb phone" (Focus mode) - see #42. [focusAllowedPackages] are extra apps allowed on top of calls, texts
     * and two-step sign-in; [travelAllowedPackages] only while [focusProfile] is "travel" (adults). Written
     * only by the parent account - a kid device never changes any of these.
     */
    val focusMode: Boolean = false,
    val focusProfile: String = FocusProfile.STANDARD.wireValue,
    val focusAllowedPackages: List<String> = emptyList(),
    val travelAllowedPackages: List<String> = emptyList(),
    /**
     * Apps whose time does not add to the overall daily limit (an audiobook or reading app, maps, a school app).
     * Their time still shows in the per-app usage and their own per-app limit still applies; to keep one usable
     * after the daily limit is reached, also mark it [alwaysAllowedPackages]. Written only by the parent account.
     */
    val excludedFromTotalPackages: List<String> = emptyList(),
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
        "trackWebsites" to trackWebsites,
        "focusMode" to focusMode,
        "focusProfile" to focusProfile,
        "focusAllowedPackages" to focusAllowedPackages,
        "travelAllowedPackages" to travelAllowedPackages,
        "excludedFromTotalPackages" to excludedFromTotalPackages,
        "showUnlocksOnKid" to showUnlocksOnKid,
        "showNotificationsOnKid" to showNotificationsOnKid
    )

    companion object {
        /**
         * A kid device can write these fields directly (see firestore.rules), so a wrong-typed value
         * must degrade to "ignored," never throw - a ClassCastException inside a Firestore snapshot
         * listener would crash the parent app on every load until the document was repaired.
         */
        private fun Map<*, *>.toIntMap(): Map<String, Int> =
            entries.mapNotNull { (k, v) -> if (k is String && v is Number) k to v.toInt() else null }.toMap()

        @Suppress("UNCHECKED_CAST")
        fun fromMap(id: String, map: Map<String, Any?>): ChildProfile = ChildProfile(
            id = id,
            name = map["name"] as? String ?: "",
            pairingCode = map["pairingCode"] as? String ?: "",
            paired = map["paired"] as? Boolean ?: false,
            deviceUid = map["deviceUid"] as? String,
            dailyLimitMinutes = (map["dailyLimitMinutes"] as? Number)?.toInt() ?: 120,
            appLimits = (map["appLimits"] as? Map<*, *>)?.toIntMap() ?: emptyMap(),
            locked = map["locked"] as? Boolean ?: false,
            parentPasscodeHash = map["parentPasscodeHash"] as? String,
            parentPasscodeSalt = map["parentPasscodeSalt"] as? String,
            isSelf = map["isSelf"] as? Boolean ?: false,
            dailyUnlockGoal = (map["dailyUnlockGoal"] as? Number)?.toInt(),
            proposedDailyLimitMinutes = (map["proposedDailyLimitMinutes"] as? Number)?.toInt(),
            proposedAppLimits = (map["proposedAppLimits"] as? Map<*, *>)?.toIntMap(),
            bedtimeStartMinutes = (map["bedtimeStartMinutes"] as? Number)?.toInt(),
            bedtimeEndMinutes = (map["bedtimeEndMinutes"] as? Number)?.toInt(),
            blockedDomains = (map["blockedDomains"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            requestedExtraMinutes = (map["requestedExtraMinutes"] as? Number)?.toInt(),
            temporaryUnlockUntilMs = (map["temporaryUnlockUntilMs"] as? Number)?.toLong(),
            alwaysAllowedPackages = (map["alwaysAllowedPackages"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            alwaysAllowedContacts = (map["alwaysAllowedContacts"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            trackUnlocks = map["trackUnlocks"] as? Boolean ?: false,
            trackNotifications = map["trackNotifications"] as? Boolean ?: false,
            trackWebsites = map["trackWebsites"] as? Boolean ?: false,
            focusMode = map["focusMode"] as? Boolean ?: false,
            focusProfile = map["focusProfile"] as? String ?: FocusProfile.STANDARD.wireValue,
            focusAllowedPackages = (map["focusAllowedPackages"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            travelAllowedPackages = (map["travelAllowedPackages"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            excludedFromTotalPackages = (map["excludedFromTotalPackages"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            showUnlocksOnKid = map["showUnlocksOnKid"] as? Boolean ?: false,
            showNotificationsOnKid = map["showNotificationsOnKid"] as? Boolean ?: false
        )
    }
}
