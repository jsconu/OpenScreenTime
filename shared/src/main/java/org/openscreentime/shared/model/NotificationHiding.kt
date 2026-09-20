package org.openscreentime.shared.model

/** The few facts about a posted notification that decide whether calm mode may take it out of the shade. */
data class NotificationFacts(
    val packageName: String,
    /** The notification's category, e.g. "call" or "alarm" (Android's `Notification.CATEGORY_*` values); may be null. */
    val category: String?,
    val isOngoing: Boolean
)

// Values of Android's Notification.CATEGORY_* constants, spelled out so this rule stays plain Kotlin and testable.
private val NEVER_HIDDEN_CATEGORIES = setOf(
    "call", "alarm", "navigation", "sys", "err", "transport", "missed_call"
)

/**
 * Calm mode's one safety rule (see #42): may this notification be removed from the shade? Everything is hidden
 * (and kept on the phone in the calm list) except what someone needs to see when it happens:
 *  - this app's own notifications and anything ongoing (a call in progress, music, navigation, a timer);
 *  - the phone's essential apps (dialer, texting, contacts, home screen, keyboard) and sign-in-code apps;
 *  - system components;
 *  - calls, alarms, navigation, system and error notifications, whichever app posted them.
 * [essentialPackages] is worked out on the device (see `resolveEssentialPackages`) since it differs by phone maker.
 */
fun shouldHideNotification(
    facts: NotificationFacts,
    ownPackage: String,
    essentialPackages: Set<String>
): Boolean = when {
    facts.packageName == ownPackage -> false
    facts.isOngoing -> false
    facts.packageName in essentialPackages -> false
    facts.packageName in FOCUS_SYSTEM_PACKAGES -> false
    facts.packageName in AUTHENTICATOR_PACKAGES -> false
    facts.category in NEVER_HIDDEN_CATEGORIES -> false
    else -> true
}
