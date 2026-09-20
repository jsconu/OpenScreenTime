package org.openscreentime.shared.util

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * The long-running foreground service both apps use to measure "screen time": time spent unlocked between
 * ACTION_USER_PRESENT and the next ACTION_SCREEN_OFF, and an unlock counted each time the device is unlocked
 * (see [recordScreenEvent]). The kid app runs it for a child's phone, the parent app for the parent's own once they
 * opt in to tracking themselves; each supplies its usage store, notification and (optionally) a periodic refresh.
 */
abstract class BaseScreenMonitorService : Service() {

    protected abstract fun openUsageStore(): DailyUsageStore

    /** This phone's live profile, for the tracking choices. */
    protected abstract val profile: DeviceProfileState

    protected abstract val notificationId: Int
    protected abstract fun buildNotification(): Notification

    /** If set, [onRefresh] runs this often (the kid app re-posts its status icon). */
    protected open val refreshIntervalMs: Long? = null
    protected open fun onRefresh() {}

    protected lateinit var usageStore: DailyUsageStore
        private set

    private val handler = Handler(Looper.getMainLooper())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val event = when (intent.action) {
                Intent.ACTION_USER_PRESENT -> ScreenEvent.UNLOCKED
                Intent.ACTION_SCREEN_OFF -> ScreenEvent.SCREEN_OFF
                else -> return
            }
            usageStore.recordScreenEvent(event, trackUnlocks = profile.trackUnlocks)
        }
    }

    private val refresh = object : Runnable {
        override fun run() {
            onRefresh()
            refreshIntervalMs?.let { handler.postDelayed(this, it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        usageStore = openUsageStore()
        registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        )
        startForeground(notificationId, buildNotification())
        refreshIntervalMs?.let { handler.postDelayed(refresh, it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refresh)
        runCatching { unregisterReceiver(receiver) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
