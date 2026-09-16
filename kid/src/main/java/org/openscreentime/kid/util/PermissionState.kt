package org.openscreentime.kid.util

import android.content.Context
import android.net.VpnService
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import org.openscreentime.kid.monitor.AppLimitAccessibilityService

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
