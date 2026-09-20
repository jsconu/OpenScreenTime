package org.openscreentime.parent.monitor

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.openscreentime.parent.ParentApp
import org.openscreentime.parent.R
import org.openscreentime.parent.data.UsageStore
import org.openscreentime.parent.ui.MainActivity
import org.openscreentime.shared.util.BaseScreenMonitorService
import org.openscreentime.shared.util.DailyUsageStore
import org.openscreentime.shared.util.DeviceProfileState

/**
 * Self-tracking equivalent of the kid app's ScreenMonitorService (see #8): the shared loop in
 * [BaseScreenMonitorService] for the parent's own phone. Only runs once the parent has opted into self-tracking.
 */
class ScreenMonitorService : BaseScreenMonitorService() {

    override fun openUsageStore(): DailyUsageStore = UsageStore(applicationContext)
    override val profile: DeviceProfileState = SelfDeviceState

    override val notificationId = NOTIFICATION_ID
    override fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, ParentApp.MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_monitor)
            .setContentTitle(getString(R.string.self_monitor_notification_title))
            .setContentText(getString(R.string.self_monitor_notification_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        /** Also used by AppLimitAccessibilityService to update this same notification's icon. */
        const val NOTIFICATION_ID = 2001
    }
}
