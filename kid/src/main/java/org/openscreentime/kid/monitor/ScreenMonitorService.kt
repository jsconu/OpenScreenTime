package org.openscreentime.kid.monitor

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import org.openscreentime.kid.KidApp
import org.openscreentime.kid.R
import org.openscreentime.kid.data.UsageStore

/**
 * Long-running foreground service that measures "screen time" as time spent
 * unlocked between ACTION_USER_PRESENT and the next ACTION_SCREEN_OFF, and
 * counts an unlock every time the device is unlocked.
 */
class ScreenMonitorService : Service() {

    private lateinit var usageStore: UsageStore

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> {
                    usageStore.startSession()
                    usageStore.incrementUnlockCount()
                    // See #35 - only while a parent has unlock tracking on for this profile.
                    if (LiveChildState.trackUnlocks) usageStore.markUnlockAwaitingFirstApp(System.currentTimeMillis())
                }
                Intent.ACTION_SCREEN_OFF -> {
                    usageStore.endSessionAndFlush()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        usageStore = UsageStore(applicationContext)
        registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        )
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(receiver) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification = buildOngoingNotification(
        context = this,
        channelId = KidApp.MONITOR_CHANNEL_ID,
        iconRes = R.drawable.ic_monitor,
        title = getString(R.string.monitor_notification_title),
        text = getString(R.string.monitor_notification_text)
    )

    companion object {
        /** Also used by AppLimitAccessibilityService to update this same notification's icon. */
        const val NOTIFICATION_ID = 1001
    }
}
