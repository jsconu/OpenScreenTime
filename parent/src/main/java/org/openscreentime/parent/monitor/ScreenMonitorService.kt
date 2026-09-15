package org.openscreentime.parent.monitor

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.openscreentime.parent.ParentApp
import org.openscreentime.parent.R
import org.openscreentime.parent.data.UsageStore
import org.openscreentime.parent.ui.MainActivity

/**
 * Self-tracking equivalent of the kid app's ScreenMonitorService (see #8): measures the
 * parent's own "screen time" as time spent unlocked between ACTION_USER_PRESENT and the
 * next ACTION_SCREEN_OFF, and counts an unlock every time this device is unlocked.
 * Only runs once the parent has opted into self-tracking.
 */
class ScreenMonitorService : Service() {

    private lateinit var usageStore: UsageStore

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> {
                    usageStore.startSession()
                    usageStore.incrementUnlockCount()
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

    private fun buildNotification(): Notification {
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
        private const val NOTIFICATION_ID = 2001
    }
}
