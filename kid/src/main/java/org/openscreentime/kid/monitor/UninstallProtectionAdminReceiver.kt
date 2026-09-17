package org.openscreentime.kid.monitor

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * See #31 - registers this app as a (plain, non-Device-Owner) device administrator, purely
 * so [onDisableRequested] can show a custom warning before the admin can be turned off,
 * which is itself the first of two steps needed to uninstall while it's active.
 *
 * This is real friction, not a hard block: Android only lets a Device Owner - which needs
 * enrollment at factory-reset/first-setup time, not something addable to a phone already
 * in daily use - actually veto deactivation. A plain device admin like this one can only
 * ask; a kid who taps through the warning can still turn it off and uninstall. That's the
 * same honest tradeoff as every other permission this app asks for (see the passcode-lock
 * fail-open reasoning elsewhere): physical access to the device already implies some
 * baseline trust, so this is a deterrent and a prompt to talk to a parent first, not a
 * technical guarantee.
 */
class UninstallProtectionAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "This stops OpenScreenTime from tracking and enforcing limits on this device. Ask a parent before turning it off."
}
