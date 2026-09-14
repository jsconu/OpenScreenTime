package org.openscreentime.kid.monitor

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.openscreentime.kid.KidApp
import org.openscreentime.kid.R
import org.openscreentime.kid.data.UsageStore
import org.openscreentime.kid.ui.MainActivity

/**
 * Long-running foreground service that measures "screen time" as time spent
 * unlocked between ACTION_USER_PRESENT and the next ACTION_SCREEN_OFF, and
 * counts an unlock every time the device is unlocked.
 */
class ScreenMonitorService : Service() {

    private lateinit var usageStore: UsageStore
    private var unlockedAtMs: Long? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> {
                    unlockedAtMs = System.currentTimeMillis()
                    usageStore.incrementUnlockCount()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    unlockedAtMs?.let { start ->
                        usageStore.addScreenTime(System.currentTimeMillis() - start)
                    }
                    unlockedAtMs = null
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
        return NotificationCompat.Builder(this, KidApp.MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_monitor)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
