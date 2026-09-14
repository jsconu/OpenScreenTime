package org.openscreentime.shared.repo

object FirestorePaths {
    const val PARENTS = "parents"
    const val CHILDREN = "children"
    const val DAILY_STATS = "dailyStats"
    const val PAIRING_CODES = "pairingCodes"

    fun childrenCollection(parentUid: String) = "$PARENTS/$parentUid/$CHILDREN"
    fun childDoc(parentUid: String, childId: String) = "${childrenCollection(parentUid)}/$childId"
    fun dailyStatsCollection(parentUid: String, childId: String) = "${childDoc(parentUid, childId)}/$DAILY_STATS"
    fun dailyStatsDoc(parentUid: String, childId: String, date: String) =
        "${dailyStatsCollection(parentUid, childId)}/$date"
}
