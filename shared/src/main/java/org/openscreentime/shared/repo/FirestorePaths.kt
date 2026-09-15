package org.openscreentime.shared.repo

object FirestorePaths {
    const val PARENTS = "parents"
    const val CHILDREN = "children"
    const val DAILY_STATS = "dailyStats"
    const val PAIRING_CODES = "pairingCodes"
    /**
     * Fixed document id for the parent's own self-tracking profile (see #8), rather
     * than the auto-generated id every other child gets. A linked kid device has no
     * permission to list/query the children collection (see firestore.rules), so it
     * needs to be able to construct this path directly to read it (#18) - a fixed id
     * means it never has to look the id up first.
     */
    const val SELF_CHILD_ID = "self"

    fun childrenCollection(parentUid: String) = "$PARENTS/$parentUid/$CHILDREN"
    fun childDoc(parentUid: String, childId: String) = "${childrenCollection(parentUid)}/$childId"
    fun dailyStatsCollection(parentUid: String, childId: String) = "${childDoc(parentUid, childId)}/$DAILY_STATS"
    fun dailyStatsDoc(parentUid: String, childId: String, date: String) =
        "${dailyStatsCollection(parentUid, childId)}/$date"
}
