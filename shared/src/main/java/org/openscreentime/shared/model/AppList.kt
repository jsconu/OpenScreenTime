package org.openscreentime.shared.model

/**
 * The per-app lists a parent keeps on a person's profile. Each is a list of package names stored under [field]
 * on the profile in Firestore (so [field] must never change), edited one app at a time.
 */
enum class AppList(val field: String) {
    /** Apps that stay usable whatever the limits or bedtime say (see #28). A parent lock still wins. */
    ALWAYS_ALLOWED("alwaysAllowedPackages"),

    /** Extra apps kept on the "dumb phone" home screen, on top of calls, texts and sign-in codes (see #42). */
    FOCUS_ALLOWED("focusAllowedPackages"),

    /** Extra apps kept on an adult's dumb phone only while the Travel profile is on (see #42). */
    TRAVEL_ALLOWED("travelAllowedPackages"),

    /** Apps whose time does not add to the overall daily limit. */
    EXCLUDED_FROM_TOTAL("excludedFromTotalPackages")
}
