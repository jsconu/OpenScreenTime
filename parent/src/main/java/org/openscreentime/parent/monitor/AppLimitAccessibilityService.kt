package org.openscreentime.parent.monitor

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.openscreentime.parent.ParentApp
import org.openscreentime.parent.R
import org.openscreentime.parent.data.SelfProfileStore
import org.openscreentime.parent.data.UsageStore
import org.openscreentime.parent.ui.BlockOverlayActivity
import org.openscreentime.parent.ui.MainActivity
import org.openscreentime.shared.model.BlockReason
import org.openscreentime.shared.model.EnforcementSettings
import org.openscreentime.shared.model.STATUS_NOTIFICATION_TITLE
import org.openscreentime.shared.model.StatusTier
import org.openscreentime.shared.model.computeStatusTier
import org.openscreentime.shared.model.isInBedtimeWindow
import org.openscreentime.shared.model.nowMinutesOfDay
import org.openscreentime.shared.model.statusNotificationMessage
import org.openscreentime.shared.util.BaseAppLimitAccessibilityService
import org.openscreentime.shared.util.DailyUsageStore
import org.openscreentime.shared.util.DeviceProfileSettings

/**
 * Self-tracking equivalent of the kid app's foreground guard (see #8) - enforces the parent's own limits on
 * their own device, the same way the kid app enforces a child's, through the shared loop in
 * [BaseAppLimitAccessibilityService]. Only runs once the parent has opted into self-tracking and granted this
 * device's own accessibility permission. Unlike the kid app it has no friction pause and no "more time" grants.
 */
class AppLimitAccessibilityService : BaseAppLimitAccessibilityService() {

    override val settings: EnforcementSettings = DeviceProfileSettings(SelfDeviceState)
    override fun openUsageStore(): DailyUsageStore = UsageStore(applicationContext)

    override val warningChannelId = ParentApp.WARNING_CHANNEL_ID
    override val warningNotificationId = 2002
    override val warningIconRes = R.drawable.ic_monitor

    override fun showBlockOverlay(reason: BlockReason) {
        val overlay = Intent(this, BlockOverlayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(BlockOverlayActivity.EXTRA_REASON, reason.wireValue)
        startActivity(overlay)
    }

    /** A timed "Unlock with passcode" has run out - lock this phone again, here and in Firestore. */
    override fun relockNow() {
        SelfDeviceState.relockNow(applicationContext)
        val repository = (application as ParentApp).repository
        val parentUid = repository.currentUid
        val childId = SelfProfileStore(applicationContext).childId
        if (parentUid != null && childId != null) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { repository.setLocked(parentUid, childId, true) }
            }
        }
    }

    /**
     * Calm status signal (see #9) instead of exact numbers - updates the existing ongoing
     * notification's icon rather than posting a new one, so this never interrupts/alerts,
     * just reflects current state whenever it's glanced at.
     */
    override fun updateStatusNotification() {
        val isInBedtime = isInBedtimeWindow(nowMinutesOfDay(), SelfDeviceState.bedtimeStartMinutes, SelfDeviceState.bedtimeEndMinutes)
        val tier = computeStatusTier(
            locked = SelfDeviceState.lockedCache,
            isInBedtime = isInBedtime,
            dailyLimitMinutes = SelfDeviceState.dailyLimitMinutes,
            liveTotalScreenTimeMs = usageStore.liveTotalScreenTimeMs,
            dailyUnlockGoal = SelfDeviceState.dailyUnlockGoal,
            unlockCount = usageStore.unlockCount
        )
        val iconRes = when (tier) {
            StatusTier.STOP -> R.drawable.ic_status_stop
            StatusTier.CAUTION -> R.drawable.ic_status_caution
            StatusTier.GOOD -> R.drawable.ic_status_good
        }

        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, ParentApp.MONITOR_CHANNEL_ID)
            .setSmallIcon(iconRes)
            // See the kid app's AppLimitAccessibilityService: Android forces every
            // status-bar icon to a flat white silhouette regardless of setColor() -
            // this only reaches the pulled-down notification shade's icon circle.
            .setColor(statusTierColor(tier))
            .setContentTitle(STATUS_NOTIFICATION_TITLE)
            .setContentText(statusNotificationMessage(tier, pausedByLockOrBedtime = SelfDeviceState.lockedCache || isInBedtime))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        getSystemService(NotificationManager::class.java).notify(ScreenMonitorService.NOTIFICATION_ID, notification)
    }
}

/** Matches the three status-icon colors used elsewhere (e.g. the Dashboard's "Granted" text, the lock button). */
private fun statusTierColor(tier: StatusTier): Int = when (tier) {
    StatusTier.GOOD -> Color.parseColor("#2E7D32")
    StatusTier.CAUTION -> Color.parseColor("#F57C00")
    StatusTier.STOP -> Color.parseColor("#B3261E")
}
