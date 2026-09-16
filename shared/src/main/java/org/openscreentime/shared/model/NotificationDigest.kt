package org.openscreentime.shared.model

import kotlinx.serialization.Serializable

/** Cap so a noisy day of notifications can't grow the local digest without bound. */
const val MAX_DIGEST_ENTRIES = 200

/**
 * One notification in the kid-app digest (see #20). Stored only on the kid device -
 * never synced to Firestore or shown in the parent app.
 */
@Serializable
data class DigestNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAtMs: Long
)

/** Notifications from one app, newest first. */
data class DigestAppGroup(
    val packageName: String,
    val appLabel: String,
    val entries: List<DigestNotification>
)

/**
 * Ongoing / foreground-service / group-summary rows and this app's own notifications
 * are skipped so the digest stays a calm list of other apps, not a mirror of our
 * monitoring banners. Blank title+text is skipped because there is nothing to read.
 */
fun shouldIncludeInDigest(
    packageName: String,
    ownPackageName: String,
    isOngoing: Boolean,
    isGroupSummary: Boolean,
    title: String,
    text: String
): Boolean {
    if (packageName == ownPackageName) return false
    if (isOngoing) return false
    if (isGroupSummary) return false
    return title.isNotBlank() || text.isNotBlank()
}

/** Replace an existing row with the same [DigestNotification.key], then keep the newest [MAX_DIGEST_ENTRIES]. */
fun upsertDigestEntry(
    entries: List<DigestNotification>,
    incoming: DigestNotification
): List<DigestNotification> {
    val without = entries.filterNot { it.key == incoming.key }
    return (without + incoming)
        .sortedBy { it.postedAtMs }
        .takeLast(MAX_DIGEST_ENTRIES)
}

/** Group by app, newest group and newest row first. */
fun groupDigestByApp(entries: List<DigestNotification>): List<DigestAppGroup> {
    return entries
        .groupBy { it.packageName }
        .map { (_, groupEntries) ->
            val sorted = groupEntries.sortedByDescending { it.postedAtMs }
            DigestAppGroup(
                packageName = sorted.first().packageName,
                appLabel = sorted.first().appLabel,
                entries = sorted
            )
        }
        .sortedByDescending { it.entries.first().postedAtMs }
}
