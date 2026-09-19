package org.openscreentime.kid.monitor

import android.app.Notification
import android.app.Person
import android.os.Build
import androidx.annotation.RequiresApi
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.openscreentime.kid.data.NotificationDigestStore
import org.openscreentime.kid.data.UsageStore
import org.openscreentime.shared.model.DigestNotification
import org.openscreentime.shared.model.isCallAllowedDuringBedtime
import org.openscreentime.shared.model.isInBedtimeWindow
import org.openscreentime.shared.model.nowMinutesOfDay
import org.openscreentime.shared.model.shouldIncludeInDigest

/**
 * Optional, off-by-default listener for the kid-app notification digest (see #20).
 * Records locally only; [onNotificationRemoved] is intentionally a no-op so the day's
 * list stays a digest of what arrived, not a live shade.
 *
 * Also does the texting half of #34's bedtime call/text blocking: any notification the
 * posting app tagged [Notification.CATEGORY_MESSAGE] gets dismissed during a bedtime
 * window unless its sender is in [LiveChildState.alwaysAllowedContacts] - independent of
 * the digest opt-in above, since it only needs the notification-listener permission
 * already granted for that feature, not the digest itself turned on. This mutes the
 * alert, it doesn't prevent delivery: true SMS/MMS blocking would require this app to
 * become the phone's default messaging app, a far larger undertaking (see the design
 * discussion on #34) - a kid who opens Messages directly during bedtime can still see a
 * muted text. When the sender's number can't be determined from the notification at all,
 * this deliberately does nothing rather than risk muting a message that was actually from
 * a parent.
 */
class NotificationDigestListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        maybeMuteBedtimeMessage(sbn)
        maybeCountNotification(sbn)

        val store = NotificationDigestStore(this)
        if (!store.optedIn) return

        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (!shouldIncludeInDigest(
                packageName = sbn.packageName,
                ownPackageName = packageName,
                isOngoing = sbn.isOngoing,
                isGroupSummary = isGroupSummary,
                title = title,
                text = text
            )
        ) {
            return
        }

        store.record(
            DigestNotification(
                key = sbn.key,
                packageName = sbn.packageName,
                appLabel = appLabelFor(sbn.packageName),
                title = title.trim(),
                text = text.trim(),
                postedAtMs = sbn.postTime
            )
        )
    }

    private fun appLabelFor(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    /**
     * See #35 - counts notifications received today, overall and by app, while a parent has
     * notification tracking on for this device. A count only: nothing about a notification's
     * content is kept, and this runs independently of the local digest opt-in above.
     */
    private fun maybeCountNotification(sbn: StatusBarNotification) {
        if (!LiveChildState.trackNotifications) return
        val notification = sbn.notification
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (!shouldIncludeInDigest(sbn.packageName, packageName, sbn.isOngoing, isGroupSummary, title, text)) return
        val usageStore = UsageStore(this)
        usageStore.cacheAppName(sbn.packageName, appLabelFor(sbn.packageName))
        usageStore.recordNotification(sbn.packageName)
    }

    private fun maybeMuteBedtimeMessage(sbn: StatusBarNotification) {
        if (sbn.notification.category != Notification.CATEGORY_MESSAGE) return
        val isInBedtime = isInBedtimeWindow(
            nowMinutesOfDay(), LiveChildState.bedtimeStartMinutes, LiveChildState.bedtimeEndMinutes
        )
        if (!isInBedtime) return
        // Notification.EXTRA_PEOPLE_LIST holds android.app.Person, which is API 28+.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val senderNumber = extractSenderPhoneNumber(sbn.notification) ?: return
        val allowed = isCallAllowedDuringBedtime(
            phoneNumber = senderNumber,
            nowMinutesOfDay = nowMinutesOfDay(),
            bedtimeStartMinutes = LiveChildState.bedtimeStartMinutes,
            bedtimeEndMinutes = LiveChildState.bedtimeEndMinutes,
            alwaysAllowedContacts = LiveChildState.alwaysAllowedContacts
        )
        if (!allowed) cancelNotification(sbn.key)
    }

    /** Modern messaging notifications (RCS/SMS via Google Messages, etc.) attach a `tel:` Person URI per sender. */
    @RequiresApi(Build.VERSION_CODES.P)
    @Suppress("DEPRECATION")
    private fun extractSenderPhoneNumber(notification: Notification): String? {
        val people = notification.extras.getParcelableArrayList<Person>(Notification.EXTRA_PEOPLE_LIST) ?: return null
        for (person in people) {
            val uri = person.uri ?: continue
            if (uri.startsWith("tel:")) return uri.removePrefix("tel:")
        }
        return null
    }
}
