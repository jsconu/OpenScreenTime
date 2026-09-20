package org.openscreentime.shared.model

/**
 * "Dumb phone" (Focus mode) - see #42. A parent can switch it on for themselves or for a child: the phone keeps
 * calling, texting and two-step sign-in apps, plus whichever apps the parent adds, and everything else is one
 * tap away from the home screen. Adults also get a "Travel" profile that is less strict, for tickets, maps and
 * the like. This file is the pure rules; the launcher screen and the accessibility enforcement live in the apps.
 */

enum class FocusProfile(val wireValue: String) {
    STANDARD("standard"),
    TRAVEL("travel");

    companion object {
        fun fromWireValue(value: String?): FocusProfile = entries.find { it.wireValue == value } ?: STANDARD
    }
}

/** An app shown on the Focus home screen. */
data class FocusApp(val packageName: String, val label: String)

/** What a parent chose for one phone. [allowed] always applies; [travelAllowed] only while in the Travel profile. */
data class FocusConfig(
    val enabled: Boolean = false,
    val profile: FocusProfile = FocusProfile.STANDARD,
    val allowed: Set<String> = emptySet(),
    val travelAllowed: Set<String> = emptySet()
)

/** Parts of Android that must keep working or the phone becomes unusable: system UI, permission dialogs, calls. */
val FOCUS_SYSTEM_PACKAGES: Set<String> = setOf(
    "android", "com.android.systemui", "com.google.android.gms", "com.google.android.gsf",
    "com.android.permissioncontroller", "com.google.android.permissioncontroller",
    "com.android.emergency", "com.android.server.telecom", "com.android.phone", "com.android.incallui",
    "com.android.providers.telephony", "com.android.providers.contacts",
    "com.google.android.dialer", "com.android.dialer", "com.samsung.android.incallui",
    "com.samsung.android.dialer", "com.samsung.android.app.telephonyui"
)

/** Two-step sign-in (authenticator) apps: allowed by default so a login code is never locked away. */
val AUTHENTICATOR_PACKAGES: Set<String> = setOf(
    "com.google.android.apps.authenticator2", "com.azure.authenticator", "com.microsoft.msa.authenticator",
    "com.authy.authy", "com.duosecurity.duomobile", "com.lastpass.authenticator", "com.twofasapp",
    "com.beemdevelopment.aegis", "org.shadowice.flocke.andotp", "com.okta.android.auth", "io.ente.auth",
    "com.yubico.yubioath", "com.bitwarden.authenticator", "com.salesforce.authenticator"
)

/**
 * The extra apps the Travel profile lets through by default, for showing tickets and getting around: wallet and
 * boarding passes, maps, email, camera and photos (a ticket is often a screenshot), ride-share, translation,
 * calendar. A parent can add more (an airline app, a hotel app) and none of this is used outside Travel.
 */
val TRAVEL_DEFAULT_PACKAGES: Set<String> = setOf(
    "com.google.android.apps.walletnfcrel", "com.samsung.android.spay", "com.google.android.apps.maps",
    "com.google.android.gm", "com.google.android.apps.photos", "com.google.android.GoogleCamera",
    "com.android.camera", "com.android.camera2", "com.sec.android.app.camera", "com.sec.android.gallery3d",
    "com.google.android.apps.translate", "com.ubercab", "me.lyft.android", "com.airbnb.android",
    "com.booking", "com.google.android.calendar", "com.samsung.android.calendar", "com.google.android.apps.docs"
)

/**
 * True if [packageName] may be in front right now. Everything is allowed when Focus mode is off, during an
 * "all apps" window ([openUntilMs] in the future), or when nothing is in front. [essential] is what the
 * phone itself needs (its dialer, its texting app, the home screen, the keyboard, this app), worked out on the
 * device because those packages differ from phone to phone.
 */
fun isFocusAllowed(
    packageName: String?,
    config: FocusConfig,
    essential: Set<String>,
    openUntilMs: Long?,
    nowMs: Long
): Boolean {
    if (!config.enabled || packageName == null) return true
    if (openUntilMs != null && openUntilMs > nowMs) return true
    if (packageName in essential || packageName in FOCUS_SYSTEM_PACKAGES || packageName in AUTHENTICATOR_PACKAGES) return true
    if (packageName in config.allowed) return true
    return config.profile == FocusProfile.TRAVEL &&
        (packageName in TRAVEL_DEFAULT_PACKAGES || packageName in config.travelAllowed)
}

/** The apps to list on a Focus home screen, out of everything [installed]: the allowed ones, essentials first. */
fun focusHomePackages(installed: Set<String>, essentialOrdered: List<String>, config: FocusConfig): List<String> {
    val allowedNow = mutableListOf<String>()
    essentialOrdered.filter { it in installed }.forEach { allowedNow += it }
    (installed - allowedNow.toSet()).filter {
        it in AUTHENTICATOR_PACKAGES || it in config.allowed ||
            (config.profile == FocusProfile.TRAVEL && (it in TRAVEL_DEFAULT_PACKAGES || it in config.travelAllowed))
    }.sorted().forEach { allowedNow += it }
    return allowedNow
}
