package org.openscreentime.kid.monitor

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.openscreentime.kid.ui.MainActivity

/**
 * A static (posted once, never updated), ongoing foreground-service notification that opens
 * [MainActivity] when tapped - the shape [ScreenMonitorService] and [DnsSinkholeVpnService]'s
 * own notifications both need. [AppLimitAccessibilityService]'s status notification isn't
 * built with this: it's re-posted repeatedly with a dynamic icon and `setOnlyAlertOnce(true)`,
 * which is a different shape, not a third case of this one.
 */
fun buildOngoingNotification(
    context: Context,
    channelId: String,
    iconRes: Int,
    title: String,
    text: String
): Notification {
    val openIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )
    return NotificationCompat.Builder(context, channelId)
        .setSmallIcon(iconRes)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(openIntent)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()
}
