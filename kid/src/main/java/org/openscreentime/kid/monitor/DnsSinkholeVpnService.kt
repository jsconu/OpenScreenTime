package org.openscreentime.kid.monitor

import android.app.Notification
import org.openscreentime.kid.R
import org.openscreentime.kid.KidApp
import org.openscreentime.kid.data.UsageStore
import org.openscreentime.shared.net.BaseDnsSinkholeVpnService

/**
 * The kid app's website filter (see #19) and, when a parent turns it on, website tracking (see #41). The
 * packet handling lives in [BaseDnsSinkholeVpnService]; this only says where this app keeps its settings.
 */
class DnsSinkholeVpnService : BaseDnsSinkholeVpnService() {
    override val notificationId = 1003

    override fun sessionName(): String = getString(R.string.app_name)

    override fun buildNotification(): Notification = buildOngoingNotification(
        context = this,
        channelId = KidApp.MONITOR_CHANNEL_ID,
        iconRes = R.drawable.ic_monitor,
        title = getString(R.string.website_filter_notification_title),
        text = getString(R.string.website_filter_notification_text)
    )

    override fun blockedDomains(): List<String> = LiveChildState.blockedDomains
    override fun trackingWebsites(): Boolean = LiveChildState.trackWebsites
    override fun foregroundPackage(): String? = LiveChildState.foregroundPackage
    override fun saveWebsiteCounts(counts: Map<String, Int>) = UsageStore(this).addWebsiteCounts(counts)
}
