package org.openscreentime.shared.model

import org.openscreentime.shared.model.StatusTier.CAUTION
import org.openscreentime.shared.model.StatusTier.GOOD
import org.openscreentime.shared.model.StatusTier.STOP

/**
 * The calm status signal's wording, in one place so the kid app, the parent app and the in-app
 * explanation can't drift apart. The signal is a small icon in the top-left of the phone's status
 * bar (thumbs up / open hand / stop); pulling the notification shade down shows the same tier as
 * a "Screen Time Status" card with one of these short messages.
 */
const val STATUS_NOTIFICATION_TITLE = "Screen Time Status"

/** Shown for the moment before the first status has been worked out. */
const val STATUS_NOTIFICATION_STARTING_TEXT = "Checking your screen time..."

const val STATUS_GOOD_MESSAGE = "Great job! You're on track."
const val STATUS_CAUTION_MESSAGE = "Slow down"
const val STATUS_STOP_MESSAGE = "You've reached your overall screen time limit"

/** For a stop that isn't about the daily limit: a parent lock or bedtime. */
const val STATUS_PAUSED_MESSAGE = "Screen time is paused right now"

/**
 * The message under [STATUS_NOTIFICATION_TITLE] for a [tier]. [pausedByLockOrBedtime] keeps the
 * stop message honest: the stop icon also shows during a parent lock or bedtime, when "you've
 * reached your limit" would be untrue.
 */
fun statusNotificationMessage(tier: StatusTier, pausedByLockOrBedtime: Boolean = false): String = when (tier) {
    GOOD -> STATUS_GOOD_MESSAGE
    CAUTION -> STATUS_CAUTION_MESSAGE
    STOP -> if (pausedByLockOrBedtime) STATUS_PAUSED_MESSAGE else STATUS_STOP_MESSAGE
}

/** Where to look, for the in-app explanation of the status icons. */
const val STATUS_ICON_LOCATION_TEXT =
    "Look for a small icon in the top-left of your phone's status bar, next to the clock. " +
        "Pull down from the top of the screen to see \"$STATUS_NOTIFICATION_TITLE\" with a short message."
