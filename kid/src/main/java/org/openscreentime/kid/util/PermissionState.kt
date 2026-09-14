package org.openscreentime.kid.util

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import org.openscreentime.kid.monitor.AppLimitAccessibilityService

data class PermissionState(
    val overlay: Boolean,
    val accessibility: Boolean,
    val notifications: Boolean
) {
    val allGranted: Boolean get() = overlay && accessibility && notifications
}

fun checkPermissions(context: Context): PermissionState = PermissionState(
    overlay = Settings.canDrawOverlays(context),
    accessibility = isAccessibilityServiceEnabled(context),
    notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
)

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
