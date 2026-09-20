package org.openscreentime.kid.monitor

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import org.openscreentime.kid.KidApp
import org.openscreentime.kid.R
import org.openscreentime.kid.data.UsageStore
import org.openscreentime.kid.ui.MainActivity
import org.openscreentime.shared.model.STATUS_NOTIFICATION_TITLE
import org.openscreentime.shared.model.StatusTier
import org.openscreentime.shared.model.statusNotificationMessage

/**
 * The kid app's calm status: the thumbs up / open hand / stop icon in the top-left of the phone's status
 * bar, and the "Screen Time Status" card in the notification shade. It is one ongoing notification with a
 * fixed id, owned by [ScreenMonitorService] (which is what keeps it alive) and refreshed from two places:
 * that service's own timer, and [AppLimitAccessibilityService] whenever the app in front changes. It used
 * to depend on the accessibility service alone, so the icon never showed until that was on and had
 * ticked; now the monitoring service posts it from the moment it starts.
 */
object StatusNotification {

    fun build(context: Context, usageStore: UsageStore): Notification {
        val status = currentStatus(usageStore)
        val tier = status.tier
        val iconRes = when (tier) {
            StatusTier.STOP -> R.drawable.ic_status_stop
            StatusTier.CAUTION -> R.drawable.ic_status_caution
            StatusTier.GOOD -> R.drawable.ic_status_good
        }
        val openIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, KidApp.MONITOR_CHANNEL_ID)
            .setSmallIcon(iconRes)
            // Android forces every status-bar icon to a flat white silhouette (alpha channel only, RGB
            // ignored) - true for every app since Lollipop, not something a notification can opt out of.
            // setColor() only reaches the pulled-down notification shade, tinting the icon's background
            // circle there to match the tier, since that's the one place color can show at all.
            .setColor(tierColor(tier))
            .setContentTitle(STATUS_NOTIFICATION_TITLE)
            .setContentText(statusNotificationMessage(tier, status.pausedByLockOrBedtime))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    /** Re-posts the status with the current tier. Safe to call as often as you like. */
    fun post(context: Context, usageStore: UsageStore) {
        context.getSystemService(NotificationManager::class.java)
            .notify(ScreenMonitorService.NOTIFICATION_ID, build(context, usageStore))
    }

    /** Matches the three status-icon colors used elsewhere (e.g. the Dashboard's "Granted" text, the lock button). */
    private fun tierColor(tier: StatusTier): Int = when (tier) {
        StatusTier.GOOD -> Color.parseColor("#2E7D32")
        StatusTier.CAUTION -> Color.parseColor("#F57C00")
        StatusTier.STOP -> Color.parseColor("#B3261E")
    }
}
