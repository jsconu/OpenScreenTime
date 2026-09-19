package org.openscreentime.shared.model

/** The four parent-controlled tracking toggles on a [ChildProfile]; [field] is the Firestore field name. */
enum class TrackingToggle(val field: String) {
    TRACK_UNLOCKS("trackUnlocks"),
    TRACK_NOTIFICATIONS("trackNotifications"),
    SHOW_UNLOCKS_ON_KID("showUnlocksOnKid"),
    SHOW_NOTIFICATIONS_ON_KID("showNotificationsOnKid")
}

/** A per-app count of something - notifications received, or being the first app opened after an unlock. */
data class AppCount(
    val packageName: String = "",
    val appName: String = "",
    val count: Int = 0
)

/**
 * See #35 - how long after an unlock the first foreground app still counts as "the first app
 * opened after unlocking." Past this, the phone was unlocked without an immediate reach for
 * anything (or the kid is already mid-session), so nothing is recorded.
 */
const val UNLOCK_FIRST_APP_WINDOW_MS = 45_000L

/**
 * True if [packageName] coming to the foreground at [nowMs] should be recorded as the first app
 * used after the unlock at [unlockedAtMs]. [ignoredPackages] is the launcher/system UI - landing
 * on the home screen right after an unlock isn't "an app" - and [ownPackageName] is this app.
 */
fun isFirstAppAfterUnlock(
    packageName: String,
    ownPackageName: String,
    ignoredPackages: Set<String>,
    unlockedAtMs: Long?,
    nowMs: Long
): Boolean {
    if (unlockedAtMs == null) return false
    if (packageName == ownPackageName || packageName in ignoredPackages) return false
    return nowMs - unlockedAtMs in 0..UNLOCK_FIRST_APP_WINDOW_MS
}

/** True once [nowMs] is past the window where an unlock could still produce a first-app record. */
fun hasUnlockWindowExpired(unlockedAtMs: Long, nowMs: Long): Boolean =
    nowMs - unlockedAtMs > UNLOCK_FIRST_APP_WINDOW_MS

/** Counts keyed by package, plus a package -> label cache, into a list sorted most-first. */
fun toAppCounts(counts: Map<String, Int>, names: Map<String, String>): List<AppCount> =
    counts.map { (pkg, count) -> AppCount(pkg, names[pkg] ?: pkg, count) }
        .sortedByDescending { it.count }

/**
 * Shown next to any count a parent has chosen to display on a kid's phone (see #35). Counting
 * itself is the risk being named: a running number invites checking, and checking is the
 * behavior this app is trying not to feed.
 */
const val TRACKING_DISPLAY_NOTE =
    "Tracking numbers like these can make phone use feel more compulsive, not less - " +
        "glance at them rather than checking them."
