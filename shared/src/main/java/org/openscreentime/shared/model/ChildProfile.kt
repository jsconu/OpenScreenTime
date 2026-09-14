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
    val appLimits: Map<String, Int> = emptyMap()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "pairingCode" to pairingCode,
        "paired" to paired,
        "deviceUid" to deviceUid,
        "dailyLimitMinutes" to dailyLimitMinutes,
        "appLimits" to appLimits
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
            appLimits = (map["appLimits"] as? Map<String, Long>)?.mapValues { it.value.toInt() } ?: emptyMap()
        )
    }
}
