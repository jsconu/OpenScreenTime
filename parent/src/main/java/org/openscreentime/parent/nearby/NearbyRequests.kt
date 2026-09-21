package org.openscreentime.parent.nearby

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.openscreentime.parent.ParentApp
import org.openscreentime.parent.R
import org.openscreentime.parent.ui.MainActivity
import org.openscreentime.shared.nearby.NearbyMessage

/**
 * Telling a parent that their kid's phone asked for something.
 *
 * One notification, no sound beyond the system default for its channel, and no way to grant from
 * the notification itself: opening the app to decide is a deliberate beat of friction, and a
 * notification action would make "yes" the easiest thing to do while distracted. That is the whole
 * of the design principle this project is built on - see the README.
 */
object NearbyRequests {

    fun notify(context: Context, message: NearbyMessage) {
        val (title, text) = describe(message) ?: return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, ParentApp.WARNING_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun describe(message: NearbyMessage): Pair<String, String>? = when (message) {
        is NearbyMessage.MoreTimeRequest ->
            "${message.childName} asked for ${message.minutes} more minutes" to
                "Open OpenScreenTime to give them the time, or leave it - their phone stays as it is either way."
        is NearbyMessage.LimitSuggestion -> {
            val daily = message.dailyLimitMinutes
            "${message.childName} suggested a change" to
                if (daily != null) {
                    "They'd like a daily limit of $daily minutes. Open OpenScreenTime to look at it together."
                } else {
                    "Open OpenScreenTime to see what they suggested."
                }
        }
        else -> null
    }

    private const val NOTIFICATION_ID = 4821
}
