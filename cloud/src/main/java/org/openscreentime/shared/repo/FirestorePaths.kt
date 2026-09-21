package org.openscreentime.shared.repo

object FirestorePaths {
    const val PARENTS = "parents"
    const val CHILDREN = "children"
    const val DAILY_STATS = "dailyStats"
    /** The apps installed on a paired kid device, so the parent app can offer limits for all of them. */
    const val DEVICE_INFO = "deviceInfo"
    const val INSTALLED_APPS_DOC = "installedApps"
    const val PAIRING_CODES = "pairingCodes"
    /** See #22 - in-app feedback. Write-only from the client; reviewed via the Firebase console. */
    const val FEEDBACK = "feedback"
    /**
     * Fixed document id for the parent's own self-tracking profile (see #8), rather
     * than the auto-generated id every other child gets. A linked kid device has no
     * permission to list/query the children collection (see firestore.rules), so it
     * needs to be able to construct this path directly to read it (#18) - a fixed id
     * means it never has to look the id up first.
     */
    const val SELF_CHILD_ID = Profiles.SELF_CHILD_ID

    fun childrenCollection(parentUid: String) = "$PARENTS/$parentUid/$CHILDREN"
    fun childDoc(parentUid: String, childId: String) = "${childrenCollection(parentUid)}/$childId"
    fun dailyStatsCollection(parentUid: String, childId: String) = "${childDoc(parentUid, childId)}/$DAILY_STATS"
    fun dailyStatsDoc(parentUid: String, childId: String, date: String) =
        "${dailyStatsCollection(parentUid, childId)}/$date"
    /** A kid device linked to this parent by pairing - see #39. */
    fun linkedDeviceDoc(parentUid: String, deviceUid: String) = "$PARENTS/$parentUid/linkedDevices/$deviceUid"
    fun deviceInfoCollection(parentUid: String, childId: String) = "${childDoc(parentUid, childId)}/$DEVICE_INFO"
    fun installedAppsDoc(parentUid: String, childId: String) =
        "${deviceInfoCollection(parentUid, childId)}/$INSTALLED_APPS_DOC"
}
