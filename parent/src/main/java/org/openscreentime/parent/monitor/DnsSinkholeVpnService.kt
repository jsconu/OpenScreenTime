package org.openscreentime.parent.monitor

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.openscreentime.parent.ParentApp
import org.openscreentime.parent.R
import org.openscreentime.parent.data.UsageStore
import org.openscreentime.parent.ui.MainActivity
import org.openscreentime.shared.net.BaseDnsSinkholeVpnService

/**
 * The website filter for the parent's OWN phone (the same filter a kid's phone has), and, if the parent
 * turns it on for themselves, website tracking (see #41). Optional: it only runs once the parent grants
 * the VPN prompt from the Permissions screen.
 */
class DnsSinkholeVpnService : BaseDnsSinkholeVpnService() {
    override val notificationId = 2003

    override fun sessionName(): String = getString(R.string.app_name)

    override fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, ParentApp.MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_monitor)
            .setContentTitle(getString(R.string.website_filter_notification_title))
            .setContentText(getString(R.string.website_filter_notification_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun blockedDomains(): List<String> = SelfDeviceState.blockedDomains
    override fun trackingWebsites(): Boolean = SelfDeviceState.trackWebsites
    override fun foregroundPackage(): String? = SelfDeviceState.foregroundPackage
    override fun saveWebsiteCounts(counts: Map<String, Int>) = UsageStore(this).addWebsiteCounts(counts)
}
