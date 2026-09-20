package org.openscreentime.kid.monitor

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
        // The very first notification is already the right icon, and the service keeps it fresh itself,
        // so the status-bar icon doesn't wait on the accessibility service.
        startForeground(NOTIFICATION_ID, StatusNotification.build(this, usageStore))
        handler.postDelayed(refreshStatus, STATUS_REFRESH_MS)
    }

    private val handler = Handler(Looper.getMainLooper())

    private val refreshStatus = object : Runnable {
        override fun run() {
            StatusNotification.post(this@ScreenMonitorService, usageStore)
            handler.postDelayed(this, STATUS_REFRESH_MS)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refreshStatus)
        runCatching { unregisterReceiver(receiver) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        /** Also used by AppLimitAccessibilityService to update this same notification's icon. */
        const val NOTIFICATION_ID = 1001
        private const val STATUS_REFRESH_MS = 30_000L
    }
}
