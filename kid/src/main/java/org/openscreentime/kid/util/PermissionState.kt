package org.openscreentime.kid.util

import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import org.openscreentime.kid.monitor.AppLimitAccessibilityService
import org.openscreentime.kid.monitor.UninstallProtectionAdminReceiver

data class PermissionState(
    val overlay: Boolean,
    val accessibility: Boolean,
    val notifications: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    /** See #19 - the local DNS-sinkhole website filter. */
    val vpn: Boolean
) {
    val allGranted: Boolean
        get() = overlay && accessibility && notifications && ignoringBatteryOptimizations && vpn
}

/**
 * One callback per [PermissionState] field, in the same order - the request-side mirror of
 * that read-side bundle. Deliberately doesn't include the notification-digest listener
 * request: that permission is optional and separate from [PermissionState.allGranted] on
 * purpose (see #20), so it stays a standalone parameter on StatusScreen rather than being
 * folded into "the permissions this screen requests to finish core setup."
 */
data class PermissionActions(
    val onRequestOverlay: () -> Unit,
    val onRequestAccessibility: () -> Unit,
    val onRequestNotifications: () -> Unit,
    val onRequestBatteryExemption: () -> Unit,
    val onRequestVpn: () -> Unit
)

/** Separate from [PermissionState.allGranted] - the digest is optional (see #20). */
fun isNotificationListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/**
 * See #31 - also separate from [PermissionState.allGranted]: this is a deterrent against
 * the kid uninstalling the app, not something core monitoring needs to function, so it
 * lives behind the passcode-gated Parent controls screen rather than as a StatusScreen
 * setup step. See [UninstallProtectionAdminReceiver] for what this actually does and doesn't
 * guarantee.
 */
fun isDeviceAdminActive(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    return dpm.isAdminActive(ComponentName(context, UninstallProtectionAdminReceiver::class.java))
}

/**
 * See #34 - bedtime call blocking needs both the call-screening (incoming) and
 * call-redirection (outgoing) roles, only grantable via [android.app.role.RoleManager] on
 * Android 10+ (API 29). Below that, this always reports false - there's no equivalent way
 * to request either role on older Android versions, so bedtime call blocking simply isn't
 * offered there.
 */
fun isCallScreeningRoleHeld(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager ?: return false
    return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}

/** See [isCallScreeningRoleHeld] - the outgoing-call counterpart. */
fun isCallRedirectionRoleHeld(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager ?: return false
    return roleManager.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION)
}

fun checkPermissions(context: Context): PermissionState = PermissionState(
    overlay = Settings.canDrawOverlays(context),
    accessibility = isAccessibilityServiceEnabled(context),
    notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
    ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(context),
    // VpnService.prepare() returns null once consent has already been granted, and an
    // Intent to launch for consent otherwise - so "already granted" is the null case.
    vpn = VpnService.prepare(context) == null
)

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = "${context.packageName}/${AppLimitAccessibilityService::class.java.canonicalName}"
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
    for (service in splitter) {
        if (service.equals(expected, ignoreCase = true)) return true
    }
    return false
}
