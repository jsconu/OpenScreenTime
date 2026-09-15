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
    val dailyUnlockGoal: Int? = null
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
        "dailyUnlockGoal" to dailyUnlockGoal
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
            dailyUnlockGoal = (map["dailyUnlockGoal"] as? Long)?.toInt()
        )
    }
}
