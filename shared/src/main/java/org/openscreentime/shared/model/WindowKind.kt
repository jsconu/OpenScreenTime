package org.openscreentime.shared.model

/**
 * What a window coming to the front actually is, for the purpose of deciding whose time it is.
 *
 * Android reports every window that takes focus - not only apps, but the keyboard, the notification
 * shade, the volume slider, the home screen. Treating all of them as apps is what made one person's
 * browser look undercounted: typing a search opened the keyboard, the keyboard became "the app in
 * front", and the browser stopped being credited until the keyboard closed again.
 */
enum class WindowKind {
    /** A real app: its time is its own. */
    APP,

    /**
     * Something drawn over the app a person is using - the keyboard, the notification shade, the
     * lock screen, a system dialog. The app underneath is still the one being used, so it keeps the
     * time.
     */
    OVERLAY,

    /** The home screen. Nobody is in an app, so no app is credited; it still counts as screen time. */
    HOME,

    /** OpenScreenTime itself, which never counts against anyone. */
    SELF
}

fun classifyWindow(
    packageName: String,
    ownPackage: String,
    homePackages: Set<String>,
    overlayPackages: Set<String>
): WindowKind = when {
    packageName == ownPackage -> WindowKind.SELF
    packageName in overlayPackages -> WindowKind.OVERLAY
    packageName in homePackages -> WindowKind.HOME
    else -> WindowKind.APP
}

/** Always drawn over whatever is underneath: the status bar, shade, lock screen and system dialogs. */
const val SYSTEM_UI_PACKAGE = "com.android.systemui"
