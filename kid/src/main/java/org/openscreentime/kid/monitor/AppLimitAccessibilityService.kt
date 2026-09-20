package org.openscreentime.kid.monitor

import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.openscreentime.kid.KidApp
import org.openscreentime.kid.R
import org.openscreentime.kid.data.PairingStore
import org.openscreentime.kid.data.UsageStore
import org.openscreentime.kid.ui.BlockOverlayActivity
import org.openscreentime.kid.ui.PauseOverlayActivity
import org.openscreentime.shared.model.BlockReason
import org.openscreentime.shared.model.EnforcementSettings
import org.openscreentime.shared.util.BaseAppLimitAccessibilityService
import org.openscreentime.shared.util.DailyUsageStore
import org.openscreentime.shared.util.DeviceProfileSettings

/**
 * The kid app's foreground guard: the shared loop in [BaseAppLimitAccessibilityService], reading a child's live
 * profile ([KidEnforcementSettings]), with the friction pause (#12) and a parent's timed unlock that locks again.
 */
class AppLimitAccessibilityService : BaseAppLimitAccessibilityService() {

    override val settings: EnforcementSettings = DeviceProfileSettings(LiveChildState)
    override fun openUsageStore(): DailyUsageStore = UsageStore(applicationContext)

    override val warningChannelId = KidApp.WARNING_CHANNEL_ID
    override val warningNotificationId = 1002
    override val warningIconRes = R.drawable.ic_monitor

    override val supportsPause = true

    override fun onConnected() {
        // Show the right icon straight away instead of waiting for the first tick.
        updateStatusNotification()
    }

    override fun showBlockOverlay(reason: BlockReason) {
        val overlay = Intent(this, BlockOverlayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(BlockOverlayActivity.EXTRA_REASON, reason.wireValue)
        startActivity(overlay)
    }

    /** See #12 - a brief, dismissible breath, not a block, shown once per app per day. */
    override fun showPauseOverlay(appName: String) {
        val overlay = Intent(this, PauseOverlayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(PauseOverlayActivity.EXTRA_APP_NAME, appName)
        startActivity(overlay)
    }

    override fun relockNow() {
        LiveChildState.relockNow(applicationContext)
        val pairing = PairingStore(applicationContext)
        val parentUid = pairing.parentUid
        val childId = pairing.childId
        if (parentUid != null && childId != null) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { (application as KidApp).repository.setLocked(parentUid, childId, true) }
            }
        }
    }

    /**
     * Calm status signal (see #9) instead of exact numbers - updates the existing ongoing
     * notification's icon rather than posting a new one, so this never interrupts/alerts,
     * just reflects current state whenever it's glanced at.
     */
    override fun updateStatusNotification() {
        StatusNotification.post(this, usageStore)
    }
}
