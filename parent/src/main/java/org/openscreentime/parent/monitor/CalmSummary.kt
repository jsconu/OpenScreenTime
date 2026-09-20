package org.openscreentime.parent.monitor

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.openscreentime.parent.ParentApp
import org.openscreentime.parent.R
import org.openscreentime.parent.ui.MainActivity

/**
 * The single, quiet notification that stands in for everything the calm mode hid: "N collected today - tap to
 * read". It is the one central entry point in the shade (there is also a Quick Settings tile, and a swipe down on
 * the dumb-phone home screen); tapping it opens the calm list inside the app.
 */
object CalmSummary {
    private const val NOTIFICATION_ID = 2050

    fun post(context: Context, count: Int) {
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_DIGEST, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, ParentApp.CALM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_monitor)
            .setContentTitle("Calm notifications")
            .setContentText("$count collected today - tap to read")
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
