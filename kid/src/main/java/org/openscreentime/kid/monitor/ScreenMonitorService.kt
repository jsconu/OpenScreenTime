package org.openscreentime.kid.monitor

import android.app.Notification
import org.openscreentime.kid.data.UsageStore
import org.openscreentime.shared.util.BaseScreenMonitorService
import org.openscreentime.shared.util.DailyUsageStore
import org.openscreentime.shared.util.DeviceProfileState

/**
 * The kid app's screen-time service: the shared loop in [BaseScreenMonitorService], plus the calm status icon. The
 * very first notification is already the right icon, and the service keeps it fresh itself, so the status-bar icon
 * doesn't wait on the accessibility service.
 */
class ScreenMonitorService : BaseScreenMonitorService() {

    override fun openUsageStore(): DailyUsageStore = UsageStore(applicationContext)
    override val profile: DeviceProfileState = LiveChildState

    override val notificationId = NOTIFICATION_ID
    override fun buildNotification(): Notification = StatusNotification.build(this, usageStore)

    override val refreshIntervalMs: Long = STATUS_REFRESH_MS
    override fun onRefresh() {
        StatusNotification.post(this, usageStore)
    }

    companion object {
        /** Also used by AppLimitAccessibilityService to update this same notification's icon. */
        const val NOTIFICATION_ID = 1001
        private const val STATUS_REFRESH_MS = 30_000L
    }
}
