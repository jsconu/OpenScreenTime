package org.openscreentime.kid.monitor

/**
 * The kid device's live view of the paired child's Firestore-synced settings - limits,
 * lock state, bedtime window, and the blocked-domains list. Written from one place
 * (KidApp.startLimitsListener()'s Firestore callback) and read by both
 * [AppLimitAccessibilityService] and [DnsSinkholeVpnService], so "what does this device
 * currently know about the child" has one name instead of being spread across two
 * Services' own companion objects.
 *
 * `@Volatile` on every field: written on the main thread from the Firestore listener,
 * read from whichever thread each reader's own work happens on ([AppLimitAccessibilityService]
 * reads from its Handler's thread; [DnsSinkholeVpnService] reads [blockedDomains] from its
 * own background packet-processing thread).
 */
object LiveChildState {
    @Volatile var limitsCache: Map<String, Int> = emptyMap()
    @Volatile var dailyLimitMinutes: Int = Int.MAX_VALUE
    @Volatile var lockedCache: Boolean = false
    /** Informational only (see #10) - factors into the status icon, never blocks. */
    @Volatile var dailyUnlockGoal: Int? = null
    /** Minutes since local midnight; either null = no bedtime window set. See #15. */
    @Volatile var bedtimeStartMinutes: Int? = null
    @Volatile var bedtimeEndMinutes: Int? = null
    /** See #19. */
    @Volatile var blockedDomains: List<String> = emptyList()
    /** See #23 - a parent-granted "more time" window; null or in the past means no active grant. */
    @Volatile var temporaryUnlockUntilMs: Long? = null
    /** See #28 - packages that bypass bedtime and every daily/app-limit check. */
    @Volatile var alwaysAllowedPackages: Set<String> = emptySet()
    /** See #34 - phone numbers that can still call/text through a bedtime block. */
    @Volatile var alwaysAllowedContacts: List<String> = emptyList()
}
