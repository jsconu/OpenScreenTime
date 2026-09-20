package org.openscreentime.shared.util

/** The two screen events that shape a day's screen time: the phone was unlocked, or the screen turned off. */
enum class ScreenEvent { UNLOCKED, SCREEN_OFF }

/**
 * Applies a screen event to today's usage: an unlock starts a session and counts an unlock (and, only while a parent
 * has unlock tracking on, starts waiting to see which app is opened first - see #35); the screen turning off folds the
 * finished session into today's total.
 */
fun DayLedger.recordScreenEvent(event: ScreenEvent, trackUnlocks: Boolean, nowMs: Long = System.currentTimeMillis()) {
    when (event) {
        ScreenEvent.UNLOCKED -> {
            startSession()
            incrementUnlockCount()
            if (trackUnlocks) markUnlockAwaitingFirstApp(nowMs)
        }
        ScreenEvent.SCREEN_OFF -> endSessionAndFlush()
    }
}
